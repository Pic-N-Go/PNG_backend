# AWS Lightsail 서버 마이그레이션 작업일지

- **작업 일시**: 2026-09-11 ~ 2026-09-12
- **작업자**: 모정민
- **작업 목적**: AWS EC2 크레딧 만료에 따른 인프라 비용 최적화 및 AWS Lightsail 인스턴스로의 무중단급 마이그레이션

---

## 1. 마이그레이션 개요 및 배경

기존 운영 서버는 AWS EC2 환경에서 도커 컴포즈(Docker Compose) 기반의 전체 서비스 스택(Spring Boot + MySQL + Redis + RabbitMQ + 프로메테우스/그라파나)을 구동하고 있었습니다. 
지원 크레딧 만료에 따라 동일 사양 유지 시 월 65,000~75,000원의 비용(인스턴스 + EBS 스토리지 + IPv4 과금 + 아웃바운드 트래픽)이 발생할 것으로 예상되어, 트래픽 3TB와 고정 IP, 스토리지가 모두 포함된 정액제 서비스인 **AWS Lightsail(월 $12 플랜)**로 이전을 진행하였습니다.

### 비용 절감 효과
- **이전 전 (EC2 t3.medium 기준)**: 월 약 65,000 ~ 75,000원 (종량 과금제)
- **이전 후 (Lightsail 2GB/2vCPU)**: **월 $12 (약 16,000원, 고정 정액제)**
- **예상 절감액**: 연간 약 **550,000원 이상** 절약

---

## 2. 신규 서버 인프라 스펙

| 항목 | 설정 내용 |
|---|---|
| **클라우드 서비스** | AWS Lightsail (Seoul 리전, ap-northeast-2a) |
| **운영체제 (OS)** | Ubuntu 22.04 LTS |
| **하드웨어 사양** | 2 vCPU, 2GB RAM, 60GB SSD (Dual-stack) |
| **가상 메모리 (Swap)** | **4GB Swapfile** 구성 (메모리 부족 OOM 방지) |
| **고정 IP (Static IP)** | `3.34.110.32` (`picngo-static-ip`) |
| **도메인 연결** | `https://api.picngo.site` |
| **SSL 인증서** | Let's Encrypt (Certbot 자동 갱신 데몬 연동) |

### 방화벽(IPv4 Firewall) 규칙
- `22 (TCP)`: SSH 원격 접속
- `80 (TCP)`: HTTP (HTTPS 리다이렉트)
- `443 (TCP)`: HTTPS (Nginx SSL)
- `8080 ~ 8081 (TCP)`: Spring Boot App 및 Grafana 포트
- `9090 (TCP)`: Prometheus 모니터링 포트
- `3306 (TCP)`: MySQL 접속 포트

---

## 3. 세부 작업 절차

### [1단계] 기존 EC2 데이터 백업
1. 기존 EC2에 SSH 접속 후 실행 중인 `picngo-mysql` 도커 컨테이너에서 데이터베이스 덤프 추출:
   ```bash
   docker exec -i picngo-mysql mysqldump -u root -ppicngo1234 picngo > picngo_backup.sql
   ```
2. 생성된 `picngo_backup.sql` 및 환경설정 파일들을 로컬 PC(`PNG_backend`)로 안전하게 다운로드:
   ```powershell
   scp -i picngo-ec2-key.pem ubuntu@<기존_EC2_IP>:/home/ubuntu/picngo_backup.sql ./
   ```

### [2단계] Lightsail 인스턴스 생성 및 네트워크 세팅
1. AWS Lightsail 콘솔에서 `Ubuntu 22.04 LTS`, `$12 (2GB RAM)` 플랜으로 인스턴스 생성 (`picngo-backend`).
2. SSH 접속용 커스텀 키페어(`picngo-lightsail-key.pem`) 생성 및 다운로드.
3. 변동 없는 접속을 위해 **고정 IP (`3.34.110.32`)** 생성 후 인스턴스에 Attach.
4. 방화벽 규칙 추가 (80, 443, 8080-8081, 9090, 3306).

### [3단계] Lightsail 서버 초기 환경 세팅
1. 도커 공식 스크립트를 통한 Docker Engine 및 Docker Compose V2 설치:
   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker ubuntu
   ```
2. 2GB 물리 메모리 환경에서의 7개 컨테이너 안정적 가동을 위한 **4GB Swap 메모리** 생성:
   ```bash
   sudo fallocate -l 4G /swapfile
   sudo chmod 600 /swapfile
   sudo mkswap /swapfile
   sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   ```
3. 키 마운트용 디렉토리 생성:
   ```bash
   mkdir -p /home/ubuntu/secrets
   ```

### [4단계] 데이터베이스 복원 및 정합성 검증
1. 로컬에 백업해둔 파일들을 새 Lightsail 서버로 전송:
   - `picngo_backup.sql` ➡️ `/home/ubuntu/`
   - `.env.prod` ➡️ `/home/ubuntu/.env.prod`
   - `docker-compose.prod.yml` ➡️ `/home/ubuntu/docker-compose.prod.yml`
   - `secrets/firebase-key.json` ➡️ `/home/ubuntu/secrets/firebase-key.json`
2. MySQL 컨테이너만 선기동:
   ```bash
   docker compose -f docker-compose.prod.yml --env-file .env.prod up -d mysql
   ```
3. 덤프 데이터 밀어넣기 및 복원:
   ```bash
   docker exec -i picngo-mysql mysql -u root -ppicngo1234 picngo < picngo_backup.sql
   ```
4. 복원 검증 결과:
   - 총 **44개 테이블** 복원 완료 (`users`, `spot`, `course`, `review`, `contest` 등 전체 테이블)
   - Flyway 스키마 이력 테이블(**`flyway_schema_history`**) 15건 정상 보존 확인

### [5단계] GitHub Actions CI/CD 파이프라인 갱신 및 전체 배포
1. GitHub Repository Secrets 갱신:
   - `EC2_HOST`: `3.34.110.32`
   - `EC2_SSH_KEY`: `picngo-lightsail-key.pem` 프라이빗 키 전문 등록
   - `EC2_USERNAME`: `ubuntu`
2. GitHub Actions 배포 워크플로우(`deploy.yml`) 수동 트리거 실행:
   - JDK 21 빌드 및 `deploy.tar` 전송 성공
   - 7개 서비스 전체 자동 컨테이너 빌드 및 백그라운드 구동 완료

### [6단계] Nginx 리버스 프록시 및 SSL (HTTPS) 적용
1. Nginx 및 Certbot 패키지 설치:
   ```bash
   sudo apt update && sudo apt install -y nginx certbot python3-certbot-nginx
   ```
2. Nginx 리버스 프록시 설정 (`/etc/nginx/sites-available/default`):
   - `api.picngo.site` 요청을 내부 `http://127.0.0.1:8080` (Spring Boot)으로 프록시 전달
   - 대용량 이미지 업로드를 위한 `client_max_body_size 50M` 적용
   - 실시간 채팅 및 WebSocket(STOMP)을 위한 `Upgrade`, `Connection "upgrade"` 헤더 및 타임아웃 세팅
3. 도메인 DNS A 레코드 갱신 (`api.picngo.site` ➡️ `3.34.110.32`)
4. Let's Encrypt SSL 인증서 발급 및 HTTPS 적용:
   ```bash
   sudo certbot --nginx -d api.picngo.site
   ```
   - HTTPS 443 포트 적용 및 HTTP -> HTTPS 자동 리다이렉트 활성화
   - `systemd`를 통한 인증서 자동 갱신(90일 주기) 스케줄러 등록 완료

### [7단계] 기존 인프라 정리
- 새 서버 정상 작동 검증 후, 기존 AWS EC2 인스턴스 종료(Terminate) 처리로 추가 과금 차단.

---

## 4. 최종 운영 상태 점검 결과

### 실행 중인 컨테이너 목록 (`docker ps`)
- `picngo-app`: **`Up (healthy)`** - 스프링부트 메인 애플리케이션
- `picngo-mysql`: **`Up (healthy)`** - MySQL 8 데이터베이스
- `picngo-redis`: **`Up`** - Redis 7 캐시 및 세션 저장소
- `picngo-rabbitmq`: **`Up`** - RabbitMQ 3 메시지 브로커
- `picngo-grafana`: **`Up`** - Grafana 대시보드
- `picngo-prometheus`: **`Up`** - Prometheus 메트릭 수집
- `picngo-mysqld-exporter`: **`Up`** - MySQL 성능 계측 익스포터

### 엔드포인트 동작 확인
- **헬스체크**: `https://api.picngo.site/actuator/health` ➡️ `{"status":"UP"}` (정상 응답)
- **API 문서**: `https://api.picngo.site/picngo-team-api-2026.html` ➡️ Swagger UI 정상 로드
- **로컬 안전 백업본**: `picngo_backup.sql` 파일 로컬 PC 보관 완료

---

## 5. 서버 운영 및 유지보수 명령어 가이드

### 서버 접속
```powershell
ssh -i picngo-lightsail-key.pem ubuntu@3.34.110.32
```

### 컨테이너 상태 및 실시간 로그 확인
```bash
# 전체 컨테이너 상태 확인
docker ps

# 스프링부트 애플리케이션 실시간 로그
docker logs -f picngo-app

# 리소스 사용량(메모리, CPU) 실시간 확인
docker stats --no-stream
```

### Nginx 관리
```bash
# Nginx 상태 확인
sudo systemctl status nginx

# 설정 변경 후 리로드
sudo nginx -t && sudo systemctl reload nginx
```
