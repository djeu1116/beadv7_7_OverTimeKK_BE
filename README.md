# 🎫 Re:Seat
> 공연 취소표 예약 서비스

인기 공연은 예매 시작 직후 좌석이 빠르게 매진되고, 이후 발생하는 취소표는 사용자가 직접 반복 조회해야만 확인할 수 있습니다.
**Re:Seat**은 이러한 문제를 해결하기 위해 일반 티켓 예매 기능과 함께, 공연이 전체 매진된 후 취소표 대기를 신청하고 순번에 따라 자동으로 예매 기회를 배정받을 수 있는 서비스입니다.

사용자는 매진된 구역에 대기를 신청할 수 있으며, 취소표가 발생하면 대기 순서에 따라 한 명에게 우선 예매 권한이 부여됩니다. 해당 사용자는 알림을 받은 후 제한 시간 내 결제를 완료해야 하며, 결제하지 않을 경우 다음 대기자에게 기회가 자동으로 이전됩니다.

---

## 목차

- [핵심 기능](#핵심-기능)
- [기술 스택](#기술-스택)
- [시스템 아키텍처](#시스템-아키텍처)
- [프로젝트 구조](#프로젝트-구조)
- [실행 방법](#실행-방법)

---

## 핵심 기능

- 판매자의 공연, 회차, 좌석 등록 및 관리
- 사용자의 공연 조회와 티켓 예매 및 취소
- 동시 예매 시 좌석 중복 판매 방지
- 희망 구역 취소표 대기 신청
- 취소표 발생 시 대기 순번에 따른 자동 배정
- 배정된 사용자에게 알림 및 제한 시간 내 결제 기회 제공
- 미결제 시 다음 순번 대기자에게 자동 배정

---

## 기술 스택

### Backend

| 기술 | 선택 이유 |
|---|---|
| **Java 21** | Virtual Thread 지원으로 I/O 중심 서비스에 적합 · Spring Boot 및 AI(Spring AI) 생태계와의 호환성 우수 · LTS 버전으로 안정성 확보 |
| **Spring Boot** | 빠른 REST API 개발 · Spring Data JPA, Security 등 다양한 생태계 활용 |

### Database

| 기술 | 선택 이유 |
|---|---|
| **MySQL** | 프로젝트 규모에서 충분한 성능과 안정성 제공 · 팀의 숙련도를 고려하여 개발 생산성 확보 · PostgreSQL의 VACUUM 등 운영 관리 부담까지 고려할 필요가 없는 규모 |

### Infrastructure

| 기술 | 선택 이유 |
|---|---|
| **Docker** | 개발 환경 통일 및 배포 자동화 |
| **EC2** | 서비스 운영 서버 |
| **S3** | 공연 이미지 등 정적 파일 저장 |

### CI/CD

| 기술 | 선택 이유 |
|---|---|
| **GitHub Actions** | 빌드 및 배포 자동화 |

---

## 시스템 아키텍처

MSA(Microservices Architecture) 기반으로 구성되어 있으며, 각 도메인 서비스는 독립된 서버에서 동작합니다.

```
                         ┌──────────────────────┐
                         │   nginx (:443, TLS)   │
                         │   EC2 #1 (large)      │
                         └──────────┬────────────┘
                                    │
                     ┌──────────────┴──────────────┐
                     ▼                              ▼
          ┌─────────────────────┐          ┌──────────────────┐
          │  gateway-service    │          │   frontend       │
          │  (:8080)            │          │   (:3000)        │
          │  - 인증(Auth) 처리    │          └──────────────────┘
          │  - 라우팅             │
          │  - Rate Limit (예정) │
          └───────────┬─────────┘
                      │
        ┌─────────────┼─────────────┐
        ▼             ▼             ▼
┌───────────────┐┌───────────────┐┌─────────────────────┐
│ user-service  ││ order-service ││ performance-service │
│ (:8081)       ││ (:8082)       ││ (:8083)             │
└───────────────┘└───────────────┘└─────────────────────┘
        │             │             │
        └─────────────┴─────────────┘
                      ▼
              ┌───────────────┐
              │   MySQL (통합) │
              └───────────────┘
```

- 브라우저 요청은 nginx(TLS)를 거쳐 `gateway-service` 또는 `frontend`로 분기됩니다.
- `gateway-service`는 인증 처리와 각 도메인 서비스로의 라우팅을 담당하며, Rate Limit 기능을 추가할 예정입니다.
- `user-service`, `order-service`, `performance-service`는 각각 독립된 서버에서 동작하지만, 현재는 하나의 DB를 공유합니다.

<img width="1431" height="589" alt="스크린샷 2026-08-31 오전 9 35 41" src="https://github.com/user-attachments/assets/10bae9f5-d67e-4b7e-93d7-5f7d343264be" />


---

## 프로젝트 구조

```
beadv7_7_OverTimeKK_BE/
├── gateway-service/        # 인증, 라우팅, Rate Limit
├── user-service/           # 회원 도메인
├── order-service/          # 예매/주문 도메인
├── performance-service/    # 공연/좌석 도메인
├── common/                 # 공통 모듈
├── deploy/                 # 배포 관련 설정
├── observability/          # 모니터링/관측성 설정
├── local/                  # 로컬 개발 환경 설정
├── Dockerfile
├── init-order.sql          # order-service 전용 MySQL 인스턴스용
├── init-payment.sql        # payment-service 전용 MySQL 인스턴스용
├── init-performance.sql    # performance-service 전용 MySQL 인스턴스용
├── init-user.sql           # user-service 전용 MySQL 인스턴스용
├── run_local.sh
└── build.gradle
```

---

## 실행 방법

```bash
# 저장소 클론
git clone https://github.com/happ-in/beadv7_7_OverTimeKK_BE.git
cd beadv7_7_OverTimeKK_BE

# 로컬 실행 스크립트 사용
./run_local.sh
```

> DB 초기화가 필요한 경우 `init-order.sql`/`init-payment.sql`/`init-performance.sql`/`init-user.sql`을 참고해 로컬 MySQL에 스키마를 생성해주세요 - 4개 서비스 전부 별도 MySQL 인스턴스를 씁니다(`local/compose.yaml` 참고).
> 서비스별 상세 설정(포트, 환경변수 등)은 각 서비스 디렉터리(`gateway-service`, `user-service`, `order-service`, `performance-service`)의 설정 파일을 참고해주세요.

---

## 🔗 Repository

[github.com/happ-in/beadv7_7_OverTimeKK_BE](https://github.com/happ-in/beadv7_7_OverTimeKK_BE)
