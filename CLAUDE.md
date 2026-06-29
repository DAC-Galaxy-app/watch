# CLAUDE.md

이 파일은 **Claude Code**가 본 프로젝트를 진행할 때 항상 먼저 읽는 프로젝트 헌법(constitution)이다.
모든 작업은 이 문서의 원칙과 구조를 따른다.

---

## 0. 프로젝트 한 줄 요약

> Galaxy Watch(BioActive Sensor)로 **실시간 + 온디맨드 생체신호를 전부 수집** →
> 모바일 앱이 포맷 변환 후 서버 전송 → PostgreSQL 저장 → 웹 포털/Grafana 시각화.

상세 요구사항은 `docs/SRS_소프트웨어_요구사항_정의서.md`를 **단일 진실 공급원(Single Source of Truth)**으로 삼는다.

현재 Android Studio 프로젝트의 실제 모듈명은 `:wear`, `:mobile`이다.
이 문서의 `watch-app/`은 `wear/`, `mobile-app/`은 `mobile/`로 대응해 작업한다.

---

## 1. 아키텍처 (4계층)

| 단계 | 모듈 | 디렉터리 | 재사용/신규 |
|------|------|----------|-------------|
| 1 | 워치 앱 (Wear OS) | `watch-app/` | starter-wearable-app(재사용) + Sensor SDK 모듈(신규) |
| 2 | 모바일 앱 (Android) | `mobile-app/` | starter-app(재사용) + Sensor 브릿지(신규) |
| 3 | 백엔드 (Docker) | `backend/` | backend-system(재사용) + Raw 테이블(신규) |
| 4 | 대시보드 | `dashboard/` | 웹 포털(재사용) + Grafana(연동) |

데이터 흐름: **워치 → (Wearable Data Layer API) → 폰 → (HTTPS REST) → 서버 → (SQL) → 대시보드**

---

## 2. 핵심 기술 결정 (변경 금지 — 변경 시 사용자 확인 필수)

- **언어/런타임**: Kotlin (앱), JDK **17 이상**.
- **워치 수집 SDK**: Samsung Health **Sensor SDK v1.4.1** (`.aar`을 `wear/libs/`에 둔다).
- **폰 보조 SDK**: Samsung Health **Data SDK v1.1.0** (선택적, 기존 Samsung Health 데이터 조회용).
- **워치↔폰 통신**: Wear OS **Wearable Data Layer API** (MessageClient / DataClient).
- **인프라**: Docker Compose (PostgreSQL 5432 · MongoDB · Redis · 웹포털 80 · Grafana 3001).
- **에뮬레이터 사용 금지**: 두 SDK 모두 에뮬레이터 미지원 → **반드시 실기기(Galaxy Watch4+, Android 10+)로 테스트**.

---

## 3. 수집 대상 트래커 (전부 구현 대상)

### 실시간 (Continuous)
- `ACCELEROMETER_CONTINUOUS` (25Hz) · `PPG_CONTINUOUS` (25Hz) · `HEART_RATE_CONTINUOUS` (1Hz, HR+IBI)
- `EDA_CONTINUOUS` (1Hz, Watch8+) · `SKIN_TEMPERATURE_CONTINUOUS` (Watch5+)

### 온디맨드 (On-demand) — **포그라운드, 동시 1개, 30초 이내**
- `ECG_ON_DEMAND` (500Hz) · `PPG_ON_DEMAND` (100Hz) · `SPO2_ON_DEMAND`
- `BIA_ON_DEMAND` · `MF_BIA_ON_DEMAND` (Watch8+) · `SKIN_TEMPERATURE_ON_DEMAND` · `SWEAT_LOSS`

> 측정 전 반드시 `getCapabilities()`로 **기기 지원 여부를 확인**하고, 미지원 트래커는 비활성화한다.

---

## 4. 디렉터리 구조 (목표)

```
galaxywatch-biosignal/
├── CLAUDE.md                  # 이 파일
├── README.md
├── docs/
│   └── SRS_소프트웨어_요구사항_정의서.md
├── .claude/
│   └── skills/                # 작업별 스킬 (아래 5절)
│       ├── samsung-sensor-sdk/SKILL.md
│       ├── wear-data-layer/SKILL.md
│       ├── android-mobile-bridge/SKILL.md
│       ├── backend-docker/SKILL.md
│       └── grafana-dashboard/SKILL.md
├── watch-app/                 # 1단계
│   ├── libs/                  # samsung-health-sensor-api.aar 위치
│   └── ...
├── mobile-app/                # 2단계
│   └── ...
├── backend/                   # 3단계
│   ├── docker-compose.yml
│   ├── service-account-key.json   # (git-ignore)
│   └── sql/raw_tables.sql
└── dashboard/                 # 4단계 (Grafana provisioning 등)
```

---

## 5. 스킬 사용 규칙

`.claude/skills/` 아래 작업별 SKILL.md가 있다. 해당 영역 작업 시작 전 **반드시 그 SKILL.md를 먼저 읽는다.**

| 작업 | 읽을 스킬 |
|------|-----------|
| 워치 센서 트래커 구현 | `samsung-sensor-sdk` |
| 워치↔폰 데이터 전송 | `wear-data-layer` |
| 폰 수신·변환·업로드 | `android-mobile-bridge` |
| 서버/DB/Docker | `backend-docker` |
| Grafana 시각화 | `grafana-dashboard` |

---

## 6. 작업 원칙 (Claude Code 행동 규칙)

1. **SRS 우선**: 새 기능을 추가하기 전에 SRS의 FR/NFR ID와 매핑한다. 매핑되지 않는 기능은 사용자에게 확인.
2. **재사용 먼저**: Research Stack에 이미 있는 기능(로그인, 동의서, 설문, syncHealthData)은 새로 만들지 말고 재사용한다.
3. **실기기 가정**: 코드에 에뮬레이터 분기/목업을 남기지 않는다. 단, 로컬 단위 테스트용 가짜 데이터는 `test/`에 격리.
4. **고주파 데이터 안전성**: PPG/ECG 같은 고주파 스트림은 버퍼링·배치 전송·백프레셔를 항상 고려한다 (NFR-P-01).
5. **비밀정보 금지**: `service-account-key.json`, API 키, 서버 IP는 절대 커밋하지 않는다 → `.gitignore` 등록.
6. **데이터는 연구/웰니스 목적**: 코드 주석·문서·UI에 의료 진단 용도가 아님을 명시 (NFR-G-01).
7. **작은 단위로 진행**: 단계(1~4)별로 빌드·확인 후 다음 단계로. 한 번에 전 계층을 동시 변경하지 않는다.

---

## 7. 자주 쓰는 명령어

```bash
# 백엔드 기동
cd backend && docker compose up -d
#   → localhost:80 (웹포털) / localhost:5432 (PostgreSQL) / localhost:3001 (Grafana)

# Raw 테이블 적용
docker compose exec postgres psql -U postgres -d research -f /sql/raw_tables.sql

# 워치 앱 빌드 (실기기 연결 후)
cd watch-app && ./gradlew installDebug

# 모바일 앱 빌드
cd mobile-app && ./gradlew installDebug
```

---

## 8. 진행 체크리스트 (단계별 완료 조건)

- [ ] 환경: Android Studio + JDK17 + Docker 설치, SDK `.aar` 배치 (`docs/ENVIRONMENT_SETUP.md`)
- [ ] 1단계: backend/app-sdk 클론, Firebase 키 발급
- [ ] 2단계: `docker compose up -d` 성공, Raw 테이블 생성
- [ ] 3단계: 워치 앱에서 전 트래커 측정 → 폰 전송 확인
- [ ] 4단계: 폰 → 서버 업로드, DB 적재 확인
- [ ] 5단계: Grafana 실시간 파형 시각화
- [ ] 6단계: 실기기 통합 테스트 + 데이터 유실/정확도 검증

> 막히면: `docs/SRS_소프트웨어_요구사항_정의서.md` → 해당 `.claude/skills/*/SKILL.md` 순으로 참조.
