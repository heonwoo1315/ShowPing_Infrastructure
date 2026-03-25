## ☁️ AWS Cloud Infrastructure (클라우드 인프라 아키텍처)

> Elastic Beanstalk 등 추상화 도구 없이 VPC, ALB, ASG를 직접 수동 구성하여 인프라 전반에 대한 제어권을 확보했습니다.

<!-- 아키텍처 구조도 이미지를 여기에 삽입 -->
<!-- <img src="아키텍처_구조도_URL" alt="ShowPing V3 Infrastructure Architecture" /> -->

---

### 🔹 인프라 설계 원칙

| 원칙 | 설명 |
|------|------|
| **제어권 확보** | EB 대신 수동 구성으로 인프라 전 계층에 대한 이해도 확보 |
| **비용 효율성** | Cost Explorer 기반 분석으로 불필요한 NAT Gateway 식별 및 제거, 월 $24~30 절감 |
| **고가용성** | Multi-AZ(2a, 2b) 구성으로 단일 AZ 장애 시에도 서비스 지속 |
| **계층형 보안** | ALB → EC2 → RDS로 이어지는 연쇄 보안그룹 + IAM IP 제한 정책 |

---

### 🔹 1단계: 네트워크 설계 (VPC & Subnet)

독립된 전용 VPC(`ShowPing-VPC`, `10.0.0.0/16`)를 생성하고, 용도별로 서브넷을 분리했습니다.

| 구분 | 서브넷 이름 | CIDR | 가용 영역 | 용도 |
|------|------------|------|-----------|------|
| **Public** | ShowPing-subnet-public1-ap-northeast-2a | 10.0.0.0/20 | ap-northeast-2a | ALB, EC2 (Docker) |
| **Public** | ShowPing-subnet-public2-ap-northeast-2b | 10.0.16.0/20 | ap-northeast-2b | ALB (이중화) |
| **Private** | ShowPing-subnet-private1-ap-northeast-2a | 10.0.128.0/20 | ap-northeast-2a | 미사용 (확장 대비) |
| **Private** | ShowPing-subnet-private2-ap-northeast-2b | 10.0.144.0/20 | ap-northeast-2b | 미사용 (확장 대비) |
| **Private** | ShowPing-subnet-private3-ap-northeast-2a | 10.0.160.0/20 | ap-northeast-2a | RDS (MySQL) |
| **Private** | ShowPing-subnet-private4-ap-northeast-2b | 10.0.176.0/20 | ap-northeast-2b | RDS (이중화) |

**왜 EC2를 퍼블릭 서브넷에 두었는가?**
> NAT Gateway는 AZ당 월 약 4~5만원의 비용이 발생합니다. EC2를 퍼블릭 서브넷에 배치하되 보안그룹으로 ALB를 통해서만 트래픽을 수신하도록 제한하여, NAT Gateway 없이도 보안성을 확보했습니다. VPC 생성 시 자동 생성된 NAT Gateway 2개는 Cost Explorer 분석을 통해 불필요한 유휴 리소스로 식별한 후 삭제하여 월 $24~30의 비용을 절감했습니다.

---

### 🔹 2단계: 보안 설계 (IAM & Secrets Manager)

민감 정보를 GitHub Secrets에서 AWS Secrets Manager로 이전하고, IAM 정책으로 접근을 제한하여 보안 수준을 강화했습니다.

**Secrets Manager 구성:**
- 보안 암호 이름: `ShowPing_Project_Sensitive_Information_Password`
- JSON 그룹화로 아래 민감 정보를 통합 관리:
  - DB 인증: `spring.datasource.username`, `spring.datasource.password`
  - 보안 키: `jwt.secret`, `admin.password`, `crypto.aesGcmKeyBase64`
  - 외부 API: `portone.secret-key`, `portone.api-key`, `ncp.storage.secret-key`, `ncp.storage.access-key`
  - 메일: `spring.mail.username`, `spring.mail.password`
- `spring-cloud-starter-aws-secrets-manager` 연동, 키 이름 일치로 자동 매핑

**IAM 보안 설계:**

| 구성 요소 | 값 | 설명 |
|----------|---|------|
| IAM 사용자 | `HeonWoo` | AWS 계정 내 관리자 사용자 |
| IAM 정책 | `ShowPingSecretAccessPolicy` | Secrets Manager 접근 제어 |
| 허용 액션 | `GetSecretValue`, `DescribeSecret` | 시크릿 조회 및 설명만 허용 |
| 허용 IP | `183.102.62.130` (관리자 PC) | 관리자 로컬 환경에서만 접근 |
| 허용 IP | `15.164.132.246` (EC2 탄력적 IP) | 배포된 서버에서만 접근 |
| 거부 조건 | 위 2개 IP 외 모든 IP | 명시적 Deny로 이중 차단 |

**IAM 정책 구조 (Allow + Deny):**
```
Allow: 관리자 IP + EC2 IP → GetSecretValue, DescribeSecret
Deny:  그 외 모든 IP     → GetSecretValue (명시적 차단)
```
> Access Key가 유출되더라도 지정된 IP가 아니면 시크릿 정보를 절대로 조회할 수 없는 이중 보안 구조입니다.

**환경변수 관리 전략:**

| 저장소 | 관리 대상 | 접근 방식 |
|--------|----------|----------|
| **AWS Secrets Manager** | DB 비밀번호, JWT Secret, API 키 등 런타임 민감 정보 | IAM 정책 (IP 제한) |
| **GitHub Secrets** | EC2_HOST, DOCKER_USERNAME, RDS_HOSTNAME 등 인프라/배포 정보 | GitHub Actions CI/CD |
| **`.env` 파일** | 위 두 소스에서 주입받아 Docker 컨테이너에 전달 | docker-compose env_file |

| 항목 | 기존 방식 (GitHub 중심) | 개선 방식 (AWS 중심) |
|------|------------------------|---------------------|
| 민감 정보 저장소 | GitHub Secrets (일일이 등록) | AWS Secrets Manager (JSON 그룹화) |
| 보안 수준 | 유출 시 즉시 노출 | IP 제한(Allow+Deny) 및 IAM 기반 이중 보안 |
| 관리 편의성 | 비번 변경 시 재배포 필요 | AWS 콘솔에서 수정 후 서버 재시작 |

---

### 🔹 3단계: EC2 내부 서비스 구성 (Docker Compose)

EC2 인스턴스(t3.micro, 15.164.132.246) 내부에서 Docker Compose로 전체 애플리케이션 스택을 운영합니다. 모든 컨테이너는 `showping-net` 브릿지 네트워크로 연결됩니다.

| 컨테이너 | 이미지 | 포트 | 네트워크 | 역할 |
|----------|--------|------|----------|------|
| **showping** | 커스텀 빌드 | 8080 | showping-net (bridge) | Spring Boot 애플리케이션 |
| **redis** | redis:7.0-alpine | 6379 | showping-net | 세션, RefreshToken, MFA 관리 |
| **mongo** | mongo:6.0 | 27017 | showping-net | 채팅 로그, 금칙어 저장 |
| **kafka** | bitnami/kafka:3.9.0 | 9092 | showping-net | 채팅 메시지 분산 처리 |
| **zookeeper** | bitnami/zookeeper:3.9.3 | 2181 | showping-net | Kafka 클러스터 관리 |
| **kms** | kurento/kurento-media-server | 8888 | **host** | WebRTC 실시간 스트리밍 |

**Kurento(KMS)가 `host` 네트워크를 사용하는 이유:**
> WebRTC는 UDP 포트 범위(10000-65535)를 통해 미디어를 전송합니다. Docker bridge 네트워크에서는 이 대량의 UDP 포트를 매핑하기 어려우므로, KMS 컨테이너만 host 네트워크 모드로 운영하여 직접 UDP 통신을 처리합니다.

**외부 데이터 저장소:**

| 서비스 | 위치 | 용도 |
|--------|------|------|
| **RDS (db.t4g.micro)** | VPC Private 서브넷 (private3-2a) | MySQL 관계형 데이터 |
| **NCP Object Storage** | 외부 (Naver Cloud Platform) | MP4 라이브 영상 저장 |

---

### 🔹 4단계: 트래픽 분산 및 HTTPS (ALB & ACM & Route53)

**ALB (Application Load Balancer):**
- Internet-facing ALB를 Public Subnet(2a, 2b)에 배치
- HTTPS(443) 리스너 + HTTP(80) → HTTPS 자동 리다이렉트
- Target Group: EC2 8080 포트, 헬스체크 프로토콜 HTTP

**ACM (SSL 인증서):**
- `showping-live.com` 도메인에 대한 퍼블릭 인증서 발급 (DNS 검증)
- ALB에서 HTTPS Termination 처리 → EC2는 HTTP로만 통신

**Route 53:**
- A 레코드를 EC2 IP 직접 연결 → ALB Alias로 전환
- 도메인 → ALB → EC2로 이어지는 정석 트래픽 흐름 완성

---

### 🔹 5단계: 보안그룹 체인 (Security Group Chain)

외부에서 내부로 갈수록 접근이 제한되는 **3-Tier 연쇄 보안** 구조를 설계했습니다.

| 보안 그룹 | 인바운드 규칙 | 설명 |
|-----------|-------------|------|
| **ShowPing-ALB-SG** | HTTP(80), HTTPS(443) ← `0.0.0.0/0` | 누구나 접속 가능한 정문 |
| **ShowPing-EC2-SG** | 8080 ← `ShowPing-ALB-SG` | ALB를 통해서만 접근 가능 |
| **ShowPing-RDS-SG-FINAL** | 3306 ← `ShowPing-EC2-SG` + 관리자 IP(`183.102.62.130/32`) | EC2 및 관리자만 DB 접근 |

**핵심:** EC2의 8080 포트에 `0.0.0.0/0`을 열지 않고 ALB 보안그룹 ID로만 제한하여, 로드밸런서를 우회한 직접 접근을 원천 차단했습니다.

**DBeaver 접속:** EC2를 Bastion Host로 활용한 SSH 터널링을 통해 Private Subnet의 RDS에 안전하게 접속합니다.

---

### 🔹 6단계: 고가용성 및 자동 확장 (Auto Scaling)

**AMI & Launch Template:**
- 정상 동작하는 EC2에서 AMI(`ShowPing-Gold-Image-v1`) 생성
- Launch Template에 보안그룹, IAM Role 연결
- 서브넷은 ASG에서 Multi-AZ(2a, 2b) 선택

**Auto Scaling Group:**
- Desired: 2 / Min: 2 / Max: 5
- 스케일링 정책: CPU 평균 사용률 60% 대상 추적 (Target Tracking)
- Sticky Session 활성화 (WebRTC/WebSocket 세션 유지를 위해 필수)

**검증:** ASG Self-Healing(Unhealthy 인스턴스 자동 교체) 작동 확인. 대상 그룹 헬스체크 설정 최적화는 진행 중

---

### 🔹 7단계: CI/CD 파이프라인 (GitHub Actions)

```
GitHub Push (main) → Build (Gradle) → Docker Build & Push → SCP → EC2 Deploy
```

1. `main` 브랜치 push 시 GitHub Actions 워크플로 트리거
2. JDK 17 + Gradle로 빌드 → Docker 이미지 빌드 및 Docker Hub push
3. `docker-compose.yml`을 SCP로 EC2에 전송
4. EC2에서 `.env` 파일 동적 생성 (AWS 자격 증명 + 인프라 정보 주입)
5. 기존 컨테이너 정리 → `docker-compose pull && docker-compose up -d`로 배포

---

### 🔹 인프라 구축 과정 트러블슈팅

<details>
<summary><b>VPC 간 보안그룹 격리 문제 (RDS 접속 실패)</b></summary>

- **현상:** DBeaver에서 SSH 터널링을 통해 RDS에 접속하려 했으나 실패
- **원인:** RDS 보안그룹이 이전 VPC(Default VPC)의 보안그룹 ID를 참조. 보안그룹은 VPC 경계를 넘으면 서로 인식하지 못하는 '유령 규칙'이 됨
- **해결:** RDS 보안그룹의 인바운드 규칙을 현재 ShowPing-VPC 내의 EC2 보안그룹 ID로 수정
- **교훈:** 보안그룹은 VPC 단위로 격리되며, VPC 이전 시 모든 SG 참조를 새 VPC 기준으로 갱신해야 함

</details>

<details>
<summary><b>Target Group Unhealthy 문제 (헬스체크 — 진행 중)</b></summary>

- **현상:** ALB Target Group에서 ASG로 생성된 EC2 인스턴스가 Unhealthy 상태
- **시도:** 헬스체크 프로토콜을 HTTPS → HTTP로 변경, 경로를 /index.html → /로 수정, 성공 코드에 200,401,302,301 추가
- **현재 상태:** 단일 EC2(수동 배포)에서는 Healthy이나, ASG로 자동 생성된 인스턴스에서는 Unhealthy 지속. Docker 컨테이너 초기화 타이밍, AMI 내 환경변수 주입 방식 등 추가 분석 필요
- **교훈:** ALB에서 HTTPS Termination을 처리하므로 EC2 헬스체크는 HTTP로 수행해야 하며, ASG 환경에서는 인스턴스 부팅 후 애플리케이션 Ready 상태까지의 시간을 고려한 헬스체크 유예 기간 설정이 중요

</details>

<details>
<summary><b>WebRTC 라이브 방송 불가 (Kurento STUN 설정)</b></summary>

- **현상:** 인프라 이전 후 라이브 방송이 작동하지 않음
- **원인:** Kurento Media Server의 STUN 서버 IP가 이전 EC2의 IP를 참조
- **해결:** `WebRtcEndpoint.conf.ini`의 `externalIPv4`를 새 탄력적 IP(15.164.132.246)로 갱신, EC2 보안그룹에 UDP 10000-65535 포트 범위 개방, 컨테이너 재시작
- **교훈:** WebRTC는 UDP 포트 범위와 STUN/TURN 서버 IP가 인프라 변경 시 함께 갱신되어야 함

</details>

<details>
<summary><b>NAT Gateway 불필요 비용 발생 (비용 최적화)</b></summary>

- **현상:** VPC 생성 마법사("VPC 등" 옵션)가 자동 생성한 NAT Gateway 2개가 Available 상태로 비용 발생 중
- **분석:** EC2는 퍼블릭 서브넷에 위치하여 IGW로 직접 인터넷 통신 가능. RDS는 Private 서브넷에 있지만 외부로 나갈 일이 없음(연결을 받기만 하는 수동적 서비스). Redis/MongoDB/Kafka는 EC2 내부 Docker 컨테이너이므로 NAT Gateway와 무관. 즉, NAT Gateway를 사용하는 리소스가 없음
- **해결:** Cost Explorer로 비용 분석 후 NAT Gateway 2개 삭제, Private 서브넷 라우팅 테이블의 NAT 경로 제거
- **결과:** 월 $24~30 비용 절감
- **교훈:** IaC 도구나 마법사가 자동 생성한 리소스도 실제 필요 여부를 데이터로 검증해야 함

</details>

추후 트래픽 증가 및 서비스 안정성 확보를 위해 RDS Multi-AZ 배포 및 ASG 최소 용량 증설을 통한 Active-Active 구성을 고려하고 있음
