## ☁️ AWS Cloud Infrastructure (클라우드 인프라 아키텍처)

> Elastic Beanstalk 등 추상화 도구 없이 VPC, ALB, ASG를 직접 수동 구성하여 인프라 전반에 대한 제어권을 확보했습니다.

---

### 🔹 인프라 아키텍처 구조

<img width="1111" height="826" alt="image" src="https://github.com/user-attachments/assets/1a212815-ec82-40a2-ad8f-dbf96d3e23e0" />



---

### 🔹 인프라 설계 원칙

| 원칙 | 설명 |
|------|------|
| **제어권 확보** | EB 대신 수동 구성으로 각 리소스의 동작 원리를 체득. 향후 Terraform/CloudFormation으로 Iac 전환 계획 |
| **비용 효율성** | Cost Explorer 기반 분석으로 NAT Gateway 삭제(월 $66.90 절감) 및 고아 EIP 해제(월 $13.71 추가 절감), 누적 월 $80.61 절감 ($141.48 → $41.31, 70.8% 절감) |
| **고가용성** | "Multi-AZ(2a, 2b) 서브넷을 모든 계층(App, DB)에 설계하여 HA 전환 기반을 확보. 현재는 단일 노드 운영, 향후 즉시 전환 가능" |
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
> NAT Gateway 없이도 보안성을 확보했습니다. VPC 생성 시 자동 생성된 NAT Gateway 2개는 Cost Explorer 분석을 통해 불필요한 유휴 리소스로 식별한 후 삭제하여 월 $66.90을 절감했습니다. 이후 NAT Gateway 삭제로 연결이 끊긴 고아 EIP(Elastic IP)가 미해제 상태로 남아 추가 과금이 발생함을 EC2 콘솔에서 식별하여 즉시 해제, 월 $13.71을 추가 절감했습니다. (3월 실측 $141.48 → 5월 실측 $41.31, 누적 70.8% 절감)

**네트워크 연결:**
- `ShowPing-igw`: 인터넷 게이트웨이 — VPC에 1:1로 attach되어 외부 통신의 유일한 출입구 역할. 퍼블릭 서브넷의 라우팅 테이블이 `0.0.0.0/0 → IGW`를 가리킴으로써 해당 서브넷이 퍼블릭으로 동작

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
| **ShowPing-ALB-SG** | HTTP(80), HTTPS(443) ← `0.0.0.0/0` | 외부 접속 정문 |
| **ShowPing-EC2-SG** | 8080 ← `ShowPing-ALB-SG` | ALB 우회 접근 차단 |
| **ShowPing-RDS-SG-FINAL** | 3306 ← `ShowPing-EC2-SG` + 관리자 IP(`183.102.62.130/32`) | EC2 및 관리자만 DB 접근 |

**핵심:** EC2의 8080 포트에 `0.0.0.0/0`을 열지 않고 ALB 보안그룹 ID로만 제한하여, 로드밸런서를 우회한 직접 접근을 원천 차단했습니다.

**DBeaver 접속:** EC2를 Bastion Host로 활용한 SSH 터널링을 통해 Private Subnet의 RDS에 안전하게 접속합니다.

---

### 🔹 6단계: 고가용성 및 자동 확장 (Auto Scaling)

**AMI & Launch Template:**
- 정상 동작하는 EC2에서 AMI(`ShowPing-Gold-Image-v1`) 생성
- Launch Template에 보안그룹, IAM Role 연결
- 서브넷은 ASG에서 Multi-AZ(2a, 2b) 선택

**Auto Scaling Group 운영 전략:**
- **Desired: 1 / Min: 1 / Max: 1**
- **전략 포인트:** 현재는 데이터 정합성(Stateful 서비스 내부 운영) 제약으로 단일 노드 운영 중입니다. 하지만 인스턴스 장애 시 ASG의 **Self-healing** 기능을 통해 동일한 AMI로 자동 복구되도록 설정하여 최소한의 가용성을 확보했습니다.
- **확장성:** 향후 상태 저장 서비스(DB, Redis 등) 외부화 시, 즉시 **스케일 아웃 정책(Target Tracking)**을 적용할 수 있도록 시작 템플릿 설계를 완료했습니다.

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
- **결과:** NAT Gateway 삭제로 월 $66.90 절감 (EC2-기타 $70.26 → $3.36)
- **후속 이슈:** NAT Gateway 삭제 후 연결이 끊긴 고아 EIP(Elastic IP)가 미해제 상태로 남아 VPC 항목에서 추가 과금 발생. EC2 콘솔에서 고아 EIP를 식별 후 해제하여 월 $13.71 추가 절감 (VPC $25.21 → $11.50)
- **최종 결과:** 2단계 최적화 누적 — 월 $141.48 → $41.31 (70.8% 절감, AWS Cost Explorer 실측 기준)
- **교훈:** 리소스 삭제 후 연결된 의존 리소스(EIP, 보안그룹 등)를 반드시 후속 점검해야 함. IaC 도구나 마법사가 자동 생성한 리소스도 실제 필요 여부를 데이터로 검증해야 함

</details>

## ⚠️ 엔지니어링 트레이드오프 및 한계점 (Reflections)

프로젝트를 진행하며 직면한 기술적 제약과 그에 따른 선택, 그리고 현재 구조의 한계점을 기록합니다.

### 1. ASG 내 상태 저장 서비스(Stateful) 운영의 한계
* **현황:** 비용 절감을 위해 Redis, MongoDB, Kafka를 EC2 내부 Docker Compose로 운영하고 있습니다.
* **문제점:** Auto Scaling 발생 시 각 인스턴스가 독립된 데이터 저장소를 갖게 되어 세션 불일치 및 데이터 파편화가 발생할 수 있음을 인지하고 있습니다.
* **해결 방향:** 실운영 환경에서는 AWS ElastiCache(Redis), DocumentDB(Mongo), MSK(Kafka) 등의 관리형 서비스를 활용하여 상태 저장 계층을 애플리케이션 계층과 완전히 분리하는 것이 정석이나, 현재는 비용 제약으로 단일 EC2에서 Docker Compose로 운영하며, ASG는 Self-healing(자동 복구) 용도로 활용 중입니다. 스케일 아웃이 필요한 시점에는 상태 저장 서비스를 외부 관리형 서비스로 분리하는 것이 선행되어야 합니다.

### 2. 비용 최적화와 네트워크 보안의 균형
* **결정:** NAT Gateway의 고정 비용(월 약 $30) 발생을 방지하기 위해 EC2를 Public Subnet에 배치했습니다.
* **보안 보완:** 서버가 외부 노출되는 위험을 최소화하기 위해 보안 그룹(Security Group)을 ALB ID 기반으로 체이닝하여 로드밸런서를 통하지 않은 직접 접근을 원천 차단했습니다.
* **향후 계획:** NAT Instance를 직접 구축하여 비용을 유지하면서도 Private Subnet으로 서버를 이전하는 구조적 개선을 계획 중입니다. 이를 위한 Private App 서브넷(private-1, private-2)은 이미 2개 AZ에 걸쳐 확보해둔 상태입니다.

### 3. 리소스 제약(t3.micro) 하의 안정성 확보
* **액션:** "Swap 메모리 설정을 통해 물리 메모리 한계를 우회했으며, 향후 JMeter + CloudWatch를 연동한 부하 테스트를 통해 병목 지점을 수치화하고 인스턴스 Right-sizing을 진행할 계획입니다."
