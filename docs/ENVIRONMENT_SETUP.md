# 개발 환경 구축 가이드 (ENVIRONMENT_SETUP)

본 프로젝트를 처음부터 진행하기 위한 환경을 0단계부터 정리한다.
**핵심 전제: 두 SDK 모두 에뮬레이터를 지원하지 않으므로 실기기가 반드시 필요하다.**

---

## 1. 하드웨어 요구사항

| 항목 | 요구사항 | 비고 |
|------|----------|------|
| Galaxy Watch | **Watch4 시리즈 이상**, Wear OS powered by Samsung | 센서별 추가 제약: EDA·MF-BIA → Watch8+, 피부온도 → Watch5+ |
| Android 폰 | **Android 10 (API 29) 이상** | Data SDK 사용 시 Samsung Health 6.30.2+ |
| 개발 PC | RAM 16GB+ 권장, macOS/Windows/Linux | Android Studio 구동 |
| 서버 | Docker 구동 가능한 머신 (Linux 권장) | 로컬 PC도 가능 |

> Galaxy Watch는 **Wear OS 기반(Watch4 이후)** 이어야 한다. 구형 Tizen 워치는 Sensor SDK 미지원.

---

## 2. 소프트웨어 설치

### 2.1 JDK
- **JDK 17 이상** (Data SDK가 Java 17+ 요구).
- Android Studio 내장 JDK 사용 가능.

### 2.2 Android Studio
1. 최신 안정 버전 설치 (Wear OS 개발 지원 버전).
2. SDK Manager에서 설치:
   - Android SDK Platform (API 29 이상, 최신 권장)
   - Wear OS 시스템 이미지 (UI 확인용, 실제 센서 테스트는 실기기)
   - Android SDK Build-Tools, Platform-Tools(adb)
3. Kotlin 플러그인(기본 포함).

### 2.3 Samsung 개발자 도구
- **Samsung Android USB Driver** (Windows에서 워치/폰 인식).
- (선택) Remote Test Lab — 단, 센서 측정은 물리 기기 필요.

### 2.4 Docker
- Docker Engine + Docker Compose v2.
- 포트 80 / 5432 / 3001 가용 확인.

---

## 3. SDK 파일 배치

| SDK | 버전 | 다운로드 | 배치 위치 |
|-----|------|----------|-----------|
| Samsung Health **Sensor** SDK | v1.4.1 | developer.samsung.com/health/sensor | `wear/libs/*.aar` |
| Samsung Health **Data** SDK | v1.1.0 | developer.samsung.com/health/data | `mobile/libs/` (선택 사용) |

> SDK는 Samsung Developer 사이트에서 로그인 후 다운로드. 실제 사용 전 각 SDK의 **App development process(앱 검증/등록)** 절차 확인.

---

## 4. 실기기 개발자 모드 설정

### 4.1 Galaxy Watch
1. 설정 → 워치 정보 → 소프트웨어 정보 → **빌드 번호 연속 탭** → 개발자 모드 활성화.
2. 개발자 옵션 → **ADB 디버깅 / 무선 디버깅** 켜기.
3. PC와 같은 Wi-Fi → `adb connect <워치IP>:<port>` 또는 USB.
4. (필요 시) Sensor SDK 가이드의 **Developer Mode / Connect Watch** 절차 수행.

### 4.2 Android 폰
1. 설정 → 휴대전화 정보 → 빌드 번호 7회 탭 → 개발자 옵션.
2. **USB 디버깅** 켜기.
3. (Data SDK 사용 시) Samsung Health 앱 개발자 옵션 활성화.

---

## 5. 프로젝트 초기 셋업 순서

```bash
# 1) 레포 클론 (Research Stack)
git clone <backend-system-repo> backend
git clone <app-sdk-repo> app-sdk   # starter-wearable-app, starter-app 포함

# 2) Firebase 키 배치
#    Firebase 콘솔에서 프로젝트 생성 → service-account-key.json → backend/
#    모바일용 google-services.json → mobile-app/app/

# 3) 백엔드 기동
cd backend && docker compose up -d
#    → localhost:80 / localhost:5432 / localhost:3001

# 4) Raw 테이블 생성
docker compose exec postgres psql -U postgres -d research -f /sql/raw_tables.sql

# 5) SDK .aar 배치
cp samsung-health-sensor-api-v1.4.1.aar wear/libs/

# 6) 워치 앱 빌드 (워치 연결 후)
./gradlew :wear:installDebug

# 7) 모바일 앱 빌드 (폰 연결 후)
./gradlew :mobile:installDebug
```

---

## 6. 검증 체크리스트

- [ ] `adb devices`에 워치·폰이 모두 보임
- [ ] `docker compose ps` 전 서비스 Up
- [ ] PostgreSQL에 Raw 테이블 생성됨 (`\dt`)
- [ ] 워치 앱에서 PPG 측정 → 로그 출력
- [ ] 폰이 워치 데이터 수신 (Data Layer)
- [ ] 서버에 데이터 INSERT 확인
- [ ] Grafana localhost:3001에서 파형 표시

---

## 7. 자주 막히는 부분

| 증상 | 원인/해결 |
|------|-----------|
| 센서 측정이 안 됨 | 에뮬레이터에서 실행 중 → 실기기로. SDK는 에뮬레이터 미지원 |
| 워치-폰 연결 안 됨 | 두 앱의 applicationId(패키지명)가 동일한지 확인 |
| 빌드 실패 (google-services) | `google-services.json` 위치 확인 |
| 포트 충돌 | 80/5432/3001 점유 프로세스 종료 또는 매핑 변경 |
| 권한 거부 | `BODY_SENSORS` 런타임 권한 재요청 |
| EDA/MF-BIA 측정 불가 | 모델 제약(Watch8+) — capability 확인 |
