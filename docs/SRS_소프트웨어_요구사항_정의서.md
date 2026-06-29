# 소프트웨어 요구사항 정의서 (SRS)
## Galaxy Watch 실시간/온디맨드 생체신호 수집 플랫폼

| 항목 | 내용 |
|------|------|
| 문서 버전 | v1.0 |
| 작성일 | 2026-05-21 |
| 대상 시스템 | Galaxy Watch 기반 생체신호 수집·전송·시각화 플랫폼 |
| 소속 | Data Science & AI Convergence Lab. |
| 표준 | IEEE 830 기반 (의료/연구용 변형) |

---

## 1. 개요 (Introduction)

### 1.1 목적
본 문서는 Galaxy Watch(BioActive Sensor)에서 수집 가능한 모든 생체신호를 **실시간(Continuous) 방식**과 **온디맨드(On-demand) 방식**으로 수집하고, 이를 모바일 앱을 거쳐 백엔드 서버에 저장하며, 웹 대시보드와 Grafana로 실시간 시각화하는 연구용 플랫폼의 소프트웨어 요구사항을 정의한다.

### 1.2 범위
- **수집 대상**: Samsung Health Sensor SDK가 제공하는 전체 트래커 타입 (가속도계, PPG, ECG, 심박수/IBI, EDA, 피부온도, SpO2, BIA, MF-BIA, 발한량)
- **수집 방식**: 실시간 연속 수집 + 사용자 요청 기반 온디맨드 수집
- **처리 흐름**: 워치 앱 → 모바일 앱(포맷 변환) → 백엔드 서버(저장) → 웹 대시보드/Grafana(시각화)
- **재사용 기반**: Samsung Research Stack(워치 앱, 모바일 앱, 웹 포털) + Docker 인프라
- **신규 개발**: Sensor SDK 트래커 모듈, 워치↔모바일 브릿지, Raw 센서 DB 스키마, Grafana 연동

### 1.3 범위 외 (Out of Scope)
- 혈압 측정 알고리즘 자체 개발 (첨부된 삼성 헬스 모니터 혈압 측정은 *참고용 흐름*이며 본 프로젝트의 직접 구현 대상이 아님)
- 의료 진단/치료 목적의 사용 (수집 데이터는 **연구·웰니스 목적 한정**)
- iOS 지원 (Android 전용)

### 1.4 용어 정의
| 용어 | 설명 |
|------|------|
| Continuous Tracker | 이벤트가 해제될 때까지 주기적으로 데이터를 전달하는 실시간 트래커 |
| On-demand Tracker | 사용자 요청 시 단발성으로 측정하는 트래커 (포그라운드, 30초 이내, 동시 1개) |
| Health Tracking Service | Sensor SDK의 핵심 서비스 객체, 트래커 획득의 진입점 |
| Research Stack | Samsung이 제공하는 연구용 워치/모바일/웹 풀스택 프레임워크 |
| Data Layer API | Wear OS의 워치↔폰 데이터 전송 API |
| BioActive Sensor | Galaxy Watch4 이상에 탑재된 광학심박+전기심박+생체임피던스 통합 센서 |

### 1.5 참고 문서
- Samsung Health Sensor SDK Overview / Data Specifications (v1.4.1)
- Samsung Health Data SDK Overview (v1.1.0)
- 프로젝트 개발 개요 (개발_개요.pptx)
- 삼성 갤럭시 워치 혈압 어플리케이션 제품 설명서 (흐름 참고용)

---

## 2. 전체 시스템 설명 (Overall Description)

### 2.1 시스템 아키텍처 (4계층)

```
┌──────────────────────────────────────────────────────────────┐
│  1단계: Galaxy Watch 앱 (Wear OS)                              │
│  - Research Stack starter-wearable-app (재사용)                │
│  - Sensor SDK 모듈 (신규): PPG/ECG/HR/Accel/EDA/SpO2/BIA/Temp  │
│  - 실시간 + 온디맨드 트래커 → Data Layer API로 폰 전송         │
└───────────────────────────────┬──────────────────────────────┘
                                 │ Wearable Data Layer API
┌───────────────────────────────▼──────────────────────────────┐
│  2단계: 모바일 앱 (Android)                                    │
│  - Research Stack starter-app (재사용)                         │
│  - Sensor 브릿지 모듈 (신규): Raw 수신 → HealthData 변환       │
│  - syncHealthData()로 서버 업로드                              │
└───────────────────────────────┬──────────────────────────────┘
                                 │ HTTPS / REST
┌───────────────────────────────▼──────────────────────────────┐
│  3단계: 백엔드 서버 (Docker Compose)                           │
│  - PostgreSQL (정형/Raw 센서) · MongoDB · Redis                │
│  - Research Stack backend-system (재사용)                      │
│  - Raw 센서 테이블 (신규 SQL 스키마)                           │
└───────────────────────────────┬──────────────────────────────┘
                                 │
┌───────────────────────────────▼──────────────────────────────┐
│  4단계: 웹 대시보드                                            │
│  - Research Stack 웹 포털 (localhost:80) — 재사용              │
│  - Grafana 실시간 모니터링 (localhost:3001) — 외부 툴 연동     │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 사용자 클래스
| 사용자 | 역할 |
|--------|------|
| 연구 참여자 (피험자) | 워치 착용, 측정 수행, 동의서·설문 응답 |
| 연구자 | 웹 포털에서 스터디 생성, 참여자 관리, 데이터 조회 |
| 시스템 관리자 | Docker 인프라 운영, DB·Grafana 관리 |

### 2.3 운영 환경
- **워치**: Galaxy Watch4 시리즈 이상, Wear OS powered by Samsung
- **폰**: Android 10 (API 29) 이상, Samsung Health 6.30.2 이상(Data SDK 사용 시)
- **서버**: Docker / Docker Compose 환경 (Linux 권장)
- **개발 PC**: Android Studio, JDK 17 이상

### 2.4 제약 사항
- Sensor SDK / Data SDK 모두 **에뮬레이터 미지원** → 실기기 필수
- 온디맨드 트래커: 포그라운드에서만, **동시 1개만**, **30초 이내** 측정
- 온디맨드 측정 중 동시 실시간 트래커 측정 시 값이 부정확해질 수 있음
- 일부 센서는 모델 의존: EDA·MF-BIA는 Watch8+, 피부온도는 Watch5+, SpO2는 Health Platform v1.3.0+

---

## 3. 수집 대상 생체신호 명세 (핵심 요구사항 데이터)

### 3.1 실시간(Continuous) 트래커
| 트래커 타입 | Raw/가공 | 샘플링 | 데이터 셋 | 모델 제약 |
|-------------|----------|--------|-----------|-----------|
| `ACCELEROMETER_CONTINUOUS` | Raw | 25 Hz | AccelerometerSet (x,y,z) | Watch4+ |
| `PPG_CONTINUOUS` | Raw | 25 Hz | PpgSet (green/IR/red) | Watch4+ |
| `HEART_RATE_CONTINUOUS` | 가공 | 1 Hz | HeartRateSet (HR+IBI) | Watch4+ |
| `EDA_CONTINUOUS` | Raw | 1 Hz | EdaSet | Watch8+ |
| `SKIN_TEMPERATURE_CONTINUOUS` | 가공 | 이벤트 | SkinTemperatureSet | Watch5+ |

> 화면 ON일 때는 데이터 포인트 1개, OFF일 때는 누적 데이터 포인트 전달 방식의 차이가 있음 (배터리 최적화).

### 3.2 온디맨드(On-demand) 트래커
| 트래커 타입 | Raw/가공 | 샘플링 | 데이터 셋 | 모델 제약 |
|-------------|----------|--------|-----------|-----------|
| `ECG_ON_DEMAND` | Raw | 500 Hz | EcgSet | Watch4+ |
| `PPG_ON_DEMAND` | Raw | 100 Hz | PpgSet (green/IR/red) | Watch4+ |
| `SPO2_ON_DEMAND` | 가공 | 단발 | Spo2Set | Health Platform v1.3.0+ |
| `BIA_ON_DEMAND` | 가공 | 단발 | BiaSet (체성분) | Watch4+ |
| `MF_BIA_ON_DEMAND` | 가공 | 단발 | MfBiaSet (5/10/50/250kHz) | Watch8+ |
| `SKIN_TEMPERATURE_ON_DEMAND` | 가공 | 단발 | SkinTemperatureSet | Watch5+ |
| `SWEAT_LOSS` | 가공 | 단발 | SweatLossSet (러닝 후) | - |

---

## 4. 기능 요구사항 (Functional Requirements)

### 4.1 워치 앱 (1단계)
| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-W-01 | Health Tracking Service에 연결하고 연결 상태를 관리한다. | 필수 |
| FR-W-02 | `getCapabilities()`로 기기가 지원하는 트래커 타입을 조회한다. | 필수 |
| FR-W-03 | 측정 전 필요한 권한(BODY_SENSORS 등)을 요청·확인한다. | 필수 |
| FR-W-04 | 실시간 트래커(가속도/PPG/HR/EDA/피부온도)를 시작·중지할 수 있다. | 필수 |
| FR-W-05 | 온디맨드 트래커(ECG/PPG/SpO2/BIA/MF-BIA/피부온도)를 단발 측정한다. | 필수 |
| FR-W-06 | `TrackerEventListener.onDataReceived()`로 데이터 포인트를 수신한다. | 필수 |
| FR-W-07 | 온디맨드 측정은 동시에 1개만, 30초 이내로 제한한다. | 필수 |
| FR-W-08 | 수신한 Raw 데이터를 Data Layer API로 폰에 전송한다. | 필수 |
| FR-W-09 | 측정 오류(약한 신호/미감지/움직임/배터리 부족 등)를 화면에 표시한다. | 권장 |
| FR-W-10 | 워치 단독 임시 저장(버퍼) 후 폰 연결 시 일괄 전송한다. | 권장 |

### 4.2 모바일 앱 (2단계)
| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-M-01 | 워치로부터 Data Layer API로 Raw 데이터를 수신하는 리스너를 등록한다. | 필수 |
| FR-M-02 | float 배열 등 Raw 데이터를 HealthData 객체로 포맷 변환한다. | 필수 |
| FR-M-03 | Research Stack `syncHealthData()`로 서버에 업로드한다. | 필수 |
| FR-M-04 | 로그인·동의서·설문 화면을 제공한다 (Research Stack 기본). | 필수 |
| FR-M-05 | 백엔드 서버 IP/엔드포인트를 설정 파일에서 관리한다. | 필수 |
| FR-M-06 | 네트워크 단절 시 로컬 큐잉 후 재전송한다. | 권장 |
| FR-M-07 | 수집 세션(시작/종료/메타데이터)을 관리한다. | 권장 |

### 4.3 백엔드 서버 (3단계)
| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-S-01 | Docker Compose로 PostgreSQL·MongoDB·Redis·웹 포털을 기동한다. | 필수 |
| FR-S-02 | Firebase service-account-key.json으로 인증을 구성한다. | 필수 |
| FR-S-03 | PPG/ECG 등 Raw 센서용 커스텀 테이블을 SQL로 생성한다. | 필수 |
| FR-S-04 | 모바일 앱의 업로드 데이터를 수신·검증·저장하는 API를 제공한다. | 필수 |
| FR-S-05 | 참여자·스터디·세션 메타데이터를 정형 DB에 저장한다. | 필수 |
| FR-S-06 | 데이터 무결성(타임스탬프, 참여자ID, 트래커타입)을 보장한다. | 권장 |

### 4.4 웹 대시보드 (4단계)
| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-D-01 | Research Stack 웹 포털(localhost:80)에서 스터디 생성·참여자 초대·관리. | 필수 |
| FR-D-02 | 설문 생성 및 배포 기능. | 권장 |
| FR-D-03 | 커스텀 SQL로 Raw 센서 데이터 조회. | 필수 |
| FR-D-04 | Grafana(localhost:3001)에서 PostgreSQL 연결 후 차트를 생성한다. | 필수 |
| FR-D-05 | PPG/ECG 실시간 파형을 시각화하고 5초 자동 새로고침을 설정한다. | 필수 |

---

## 5. 비기능 요구사항 (Non-Functional Requirements)

| 분류 | ID | 요구사항 |
|------|-----|----------|
| 성능 | NFR-P-01 | PPG 25Hz·ECG 500Hz 등 고주파 데이터의 손실 없는 수신·전송 보장 |
| 성능 | NFR-P-02 | Grafana 시각화 지연 5초 이내 |
| 배터리 | NFR-B-01 | Continuous Tracker는 CPU wake-up 없이 AP에서 수집(SDK 기본 동작) 활용 |
| 신뢰성 | NFR-R-01 | 네트워크 단절 시 데이터 유실 없이 재전송 (큐잉) |
| 보안 | NFR-S-01 | 전송 구간 HTTPS 암호화, 저장 데이터 접근 권한 통제 |
| 보안 | NFR-S-02 | 개인 식별 정보 최소화, 참여자 ID 익명화 |
| 호환성 | NFR-C-01 | Galaxy Watch4+ / Android 10+ / JDK 17+ 환경 |
| 규정 | NFR-G-01 | 수집 데이터는 연구·웰니스 목적에 한정 (의료 진단 불가 명시) |
| 윤리 | NFR-E-01 | 측정 전 동의서 취득 및 IRB 절차 준수 (해당 시) |

---

## 6. 데이터 모델 (Raw 센서 테이블 예시)

```sql
-- 수집 세션
CREATE TABLE collection_session (
    session_id    UUID PRIMARY KEY,
    participant_id TEXT NOT NULL,
    watch_model   TEXT,
    started_at    TIMESTAMPTZ NOT NULL,
    ended_at      TIMESTAMPTZ,
    mode          TEXT CHECK (mode IN ('continuous','on_demand'))
);

-- PPG Raw (25Hz continuous / 100Hz on-demand)
CREATE TABLE raw_ppg (
    id          BIGSERIAL PRIMARY KEY,
    session_id  UUID REFERENCES collection_session(session_id),
    ts          TIMESTAMPTZ NOT NULL,
    ppg_green   INTEGER,
    ppg_ir      INTEGER,
    ppg_red     INTEGER,
    sampling_hz INTEGER
);

-- ECG Raw (500Hz on-demand)
CREATE TABLE raw_ecg (
    id          BIGSERIAL PRIMARY KEY,
    session_id  UUID REFERENCES collection_session(session_id),
    ts          TIMESTAMPTZ NOT NULL,
    ecg_mv      REAL,
    lead_off    BOOLEAN
);

-- 가속도계 Raw (25Hz)
CREATE TABLE raw_accelerometer (
    id          BIGSERIAL PRIMARY KEY,
    session_id  UUID REFERENCES collection_session(session_id),
    ts          TIMESTAMPTZ NOT NULL,
    x REAL, y REAL, z REAL
);

-- 가공 데이터 (HR/IBI, SpO2, 체온, BIA, EDA 등)
CREATE TABLE processed_signal (
    id          BIGSERIAL PRIMARY KEY,
    session_id  UUID REFERENCES collection_session(session_id),
    ts          TIMESTAMPTZ NOT NULL,
    signal_type TEXT NOT NULL,  -- heart_rate, ibi, spo2, skin_temp, bia, mf_bia, eda, sweat_loss
    value       JSONB NOT NULL  -- 타입별 구조 유연 저장
);

CREATE INDEX idx_raw_ppg_session_ts ON raw_ppg(session_id, ts);
CREATE INDEX idx_raw_ecg_session_ts ON raw_ecg(session_id, ts);
```

---

## 7. 데이터 처리 흐름 (시퀀스)

```
[워치] Health Tracking Service 연결
   → Capability/Permission 확인
   → 실시간: 트래커 setEventListener → onDataReceived(주기적)
   → 온디맨드: 트래커 시작 → onDataReceived(단발, ≤30s) → 자동 중지
   → Data Layer API.putDataItem(raw)
        │
[폰] Data Layer Listener.onDataChanged()
   → Raw(float[]) → HealthData 객체 변환
   → 로컬 큐 적재
   → syncHealthData() → POST /api/v1/signals
        │
[서버] 인증·검증 → raw_ppg / raw_ecg / processed_signal INSERT
        │
[대시보드] 웹 포털 SQL 조회 / Grafana PostgreSQL 쿼리 → 파형 시각화(5s refresh)
```

---

## 8. 개발 단계 (구현 로드맵)

| 단계 | 작업 | 산출물 |
|------|------|--------|
| 0 | 환경 구축 (Android Studio, SDK, Docker) | 개발 PC 세팅 완료 |
| 1 | backend-system + app-sdk 클론, Firebase 키 발급 | 레포 준비 |
| 2 | `docker compose up -d` + Raw 테이블 SQL 적용 | 인프라 기동 |
| 3 | 워치 앱: starter-wearable-app 빌드 + Sensor SDK 모듈 개발 | 워치 APK |
| 4 | 모바일 앱: starter-app + Sensor 브릿지 모듈 개발 | 폰 APK |
| 5 | Grafana 연동 + 대시보드 구성 | 시각화 완료 |
| 6 | 통합 테스트 (실기기), 데이터 유실/정확도 검증 | 검증 리포트 |

---

## 9. 검증 및 인수 기준 (Acceptance Criteria)

| ID | 기준 |
|----|------|
| AC-01 | Galaxy Watch4+ 실기기에서 전 트래커 타입 측정 성공 |
| AC-02 | PPG 25Hz / ECG 500Hz 데이터가 손실 없이 서버에 적재됨 |
| AC-03 | 워치→폰→서버 전 구간 end-to-end 데이터 일관성 확인 |
| AC-04 | Grafana에서 PPG/ECG 실시간 파형이 5초 내 갱신됨 |
| AC-05 | 네트워크 단절 후 재연결 시 큐잉 데이터가 정상 재전송됨 |
| AC-06 | 온디맨드 측정이 30초 제한·동시 1개 제약을 준수함 |

---

## 부록 A. 두 SDK의 역할 구분 (중요)

| 구분 | Samsung Health **Sensor** SDK | Samsung Health **Data** SDK |
|------|------------------------------|------------------------------|
| 실행 위치 | **워치(Wear OS)** | **폰(Android)** |
| 제공 데이터 | Raw 센서 신호 (PPG/ECG/Accel 등) | Samsung Health 앱에 저장된 가공 데이터 |
| 본 프로젝트 용도 | **1단계 워치 앱 (핵심 수집원)** | (선택) 폰에서 기존 Samsung Health 데이터 보조 조회 |
| 버전 | v1.4.1 | v1.1.0 |
| 공통 제약 | 에뮬레이터 미지원, 실기기 필수 | 에뮬레이터 미지원, Java 17+ |

> **핵심**: "갤럭시 워치로 수집 가능한 모든 생체신호"의 직접 수집은 **Sensor SDK(워치)**가 담당한다. Data SDK는 폰에서 Samsung Health 앱에 이미 저장된 데이터(수면, 걸음 등)를 보조적으로 가져올 때 활용한다.
