## ☁️ AWS Cloud Infrastructure (클라우드 인프라 아키텍처)

> Elastic Beanstalk 등 추상화 도구 없이 VPC, ALB, ASG를 직접 수동 구성하여 인프라 전반에 대한 제어권을 확보했습니다.

<!-- 아키텍처 구조도 이미지를 여기에 삽입 -->
<!-- <img src="아키텍처_구조도_URL" alt="ShowPing V3 Infrastructure Architecture" /> -->

---

### 🔹 인프라 설계 원칙

| 원칙 | 설명 |
|------|------|
| **제어권 확보** | EB 대신 수동 구성으로 인프라 전 계층에 대한 이해도 확보 |
| **비용 효율성** | 불필요한 NAT Gateway 식별 및 제거, Cost Explorer 기반 비용 모니터링 |
| **고가용성** | Multi-AZ(2a, 2b) 구성으로 단일 AZ 장애 시에도 서비스 지속 |
| **계층형 보안** | ALB → EC2 → RDS로 이어지는 연쇄 보안그룹(Security Group Chain) 설계 |

---

### 🔹 1단계: 네트워크 설계 (VPC & Subnet)

독립된 전용 VPC(`ShowPing-VPC`, `10.0.0.0/16`)를 생성하고, 용도별로 서브넷을 분리했습니다.

| 구분 | 서브넷 이름 | CIDR | 가용 영역 | 용도 |
|------|------------|------|-----------|------|
| **Public** | ShowPing-subnet-public1-ap-northeast-2a | 10.0.0.0/20 | ap-northeast-2a | ALB, EC2 |
| **Public** | ShowPing-subnet-public2-ap-northeast-2b | 10.0.16.0/20 | ap-northeast-2b | ALB (이중화) |
| **Private** | ShowPing-subnet-private1-ap-northeast-2a | 10.0.128.0/20 | ap-northeast-2a | EC2 확장 대비 |
| **Private** | ShowPing-subnet-private2-ap-northeast-2b | 10.0.144.0/20 | ap-northeast-2b | EC2 확장 대비 |
| **Private** | ShowPing-subnet-private3-ap-northeast-2a | 10.0.160.0/20 | ap-northeast-2a | RDS (MySQL) |
| **Private** | ShowPing-subnet-private4-ap-northeast-2b | 10.0.176.0/20 | ap-northeast-2b | RDS (이중화) |

**왜 EC2를 퍼블릭 서브넷에 두었는가?**
> NAT Gateway는 AZ당 월 약 4~5만원의 비용이 발생합니다. EC2를 퍼블릭 서브넷에 배치하되 보안그룹으로 ALB를 통해서만 트래픽을 수신하도록 제한하여, NAT Gateway 없이도 보안성을 확보했습니다. Private App 서브넷(private1, private2)은 향후 EC2 마이그레이션을 위해 예비로 확보해둔 상태입니다.

**네트워크 연결:**
- `ShowPing-igw`: 인터넷 게이트웨이 — VPC의 외부 통신 창구
- `ShowPing-vpce-s3`: S3 VPC Endpoint — S3 통신을 VPC 내부에서 처리하여 NAT Gateway 우회

---

### 🔹 2단계: 보안 설계 (IAM & Secrets Manager)

민감 정보를 GitHub Secrets에서 AWS Secrets Manager로 이전하여 보안 수준을 강화했습니다.

**Secrets Manager 구성:**
- 시크릿 이름: `showping/prod/credentials`
- JSON 그룹화로 DB 접속 정보, JWT Secret, 외부 API 키를 통합 관리
- `application.yml`의 키 이름과 100% 일치시켜 자동 매핑 적용

**IAM 보안 정책:**
- 정책명: `ShowPingSecretAccessPolicy`
- IP 제한: 관리자 PC IP + EC2 탄력적 IP만 접근 허용
- EC2에 IAM Role(`ShowPing-EC2-S3-Role`) 부여 → Access Key 파일 없이 Secrets Manager 및 S3 접근

| 항목 | 기존 방식 (GitHub 중심) | 개선 방식 (AWS 중심) |
|------|------------------------|---------------------|
| 민감 정보 저장소 | GitHub Secrets (일일이 등록) | AWS Secrets Manager (JSON 그룹화) |
| 보안 수준 | 유출 시 즉시 노출 | IP 제한 및 IAM Role 기반 보안 |
| 관리 편의성 | 비번 변경 시 재배포 필요 | AWS 콘솔에서 수정 후 서버 재시작 |

---

### 🔹 3단계: 트래픽 분산 및 HTTPS (ALB & ACM & Route53)

**ALB (Application Load Balancer):**
- Internet-facing ALB를 Public Subnet(2a, 2b)에 배치
- HTTPS(443) 리스너 + HTTP(80) → HTTPS 자동 리다이렉트 설정
- Target Group: EC2 8080 포트, 헬스체크 프로토콜 HTTP

**ACM (SSL 인증서):**
- `showping-live.com` 도메인에 대한 퍼블릭 인증서 발급 (DNS 검증)
- ALB에 인증서를 연결하여 HTTPS Termination 처리

**Route 53:**
- A 레코드를 EC2 IP 직접 연결 → ALB Alias로 전환
- 도메인 → ALB → EC2로 이어지는 정석 트래픽 흐름 완성

---

### 🔹 4단계: 보안그룹 체인 (Security Group Chain)

외부에서 내부로 갈수록 접근이 제한되는 **3-Tier 연쇄 보안** 구조를 설계했습니다.

| 보안 그룹 | 인바운드 규칙 | 설명 |
|-----------|-------------|------|
| **ShowPing-ALB-SG** | HTTP(80), HTTPS(443) ← `0.0.0.0/0` | 누구나 접속 가능한 정문 |
| **ShowPing-EC2-SG** | 8080 ← `ShowPing-ALB-SG` | ALB를 통해서만 접근 가능 |
| **ShowPing-RDS-SG-FINAL** | 3306 ← `ShowPing-EC2-SG` + 관리자 IP(`183.102.62.130/32`) | EC2 서버 및 관리자만 DB 접근 |

**핵심:** EC2의 8080 포트에 `0.0.0.0/0`을 열지 않고 ALB 보안그룹 ID로만 제한하여, 해커가 로드밸런서를 우회하여 직접 EC2에 접근하는 것을 원천 차단했습니다.

**DBeaver 접속:** EC2를 Bastion Host로 활용한 SSH 터널링을 통해 Private Subnet의 RDS에 안전하게 접속합니다.

---

### 🔹 5단계: 고가용성 및 자동 확장 (Auto Scaling)

**AMI & Launch Template:**
- 정상 동작하는 EC2에서 AMI(`ShowPing-Gold-Image-v1`) 생성
- Launch Template에 보안그룹, IAM Role(`ShowPing-EC2-S3-Role`) 연결
- 서브넷은 템플릿에 지정하지 않고 ASG에서 Multi-AZ 선택

**Auto Scaling Group:**
- Desired: 2 / Min: 2 / Max: 5
- 스케일링 정책: CPU 평균 사용률 60% 대상 추적 (Target Tracking)
- 가용 영역 2a, 2b에 걸쳐 인스턴스 분산 배치
- Sticky Session 활성화 (WebRTC/WebSocket 세션 유지)

**검증:** ASG 활동 이력에서 Unhealthy 인스턴스 자동 교체(Self-Healing) 확인, Target Group에서 Healthy 상태 검증 완료

---

### 🔹 인프라 구축 과정 트러블슈팅

<details>
<summary><b>VPC 간 보안그룹 격리 문제 (RDS 접속 실패)</b></summary>

- **현상:** DBeaver에서 SSH 터널링을 통해 RDS에 접속하려 했으나 실패
- **원인:** RDS 보안그룹이 이전 VPC(Default VPC)의 보안그룹 ID를 참조하고 있었음. 보안그룹은 VPC 경계를 넘으면 서로 인식하지 못하는 '유령 규칙'이 됨
- **해결:** RDS 보안그룹의 인바운드 규칙을 현재 ShowPing-VPC 내의 EC2 보안그룹 ID로 수정
- **교훈:** 보안그룹은 VPC 단위로 격리되며, VPC 이전 시 모든 SG 참조를 새 VPC 기준으로 갱신해야 함

</details>

<details>
<summary><b>Target Group Unhealthy 문제 (헬스체크 실패)</b></summary>

- **현상:** ALB Target Group에서 EC2 인스턴스가 Unhealthy 상태
- **원인:** 헬스체크 프로토콜이 HTTPS로 설정되어 있었고, 경로가 /index.html로 지정되어 있었음
- **해결:** 프로토콜을 HTTP로 변경, 경로를 /로 수정, 성공 코드에 200,401,302,301 추가
- **교훈:** ALB에서 HTTPS Termination을 처리하므로 EC2 헬스체크는 HTTP로 수행해야 함

</details>

<details>
<summary><b>WebRTC 라이브 방송 불가 (Kurento STUN 설정)</b></summary>

- **현상:** 인프라 이전 후 라이브 방송이 작동하지 않음
- **원인:** Kurento Media Server의 STUN 서버 IP가 이전 EC2의 IP를 참조하고 있었음
- **해결:** EC2 터미널에서 `WebRtcEndpoint.conf.ini`의 `externalIPv4`를 새 탄력적 IP(15.164.132.246)로 갱신, EC2 보안그룹에 UDP 10000-65535 포트 범위 개방, 컨테이너 재시작
- **교훈:** WebRTC는 UDP 포트 범위와 STUN/TURN 서버 IP가 인프라 변경 시 함께 갱신되어야 함

</details>
