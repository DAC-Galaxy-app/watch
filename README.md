<<<<<<< HEAD
# watch
=======
# Galaxy Watch 생체신호 수집 플랫폼

Galaxy Watch(BioActive Sensor)로 **실시간 + 온디맨드 생체신호를 전부 수집**하여
모바일 앱 → 백엔드 → 웹 대시보드/Grafana로 전송·저장·시각화하는 연구용 플랫폼.

## 빠른 시작
1. `docs/ENVIRONMENT_SETUP.md` — 개발 환경 구축 (Android Studio, SDK, Docker, 실기기)
2. `docs/SRS_소프트웨어_요구사항_정의서.md` — 무엇을 만들지(요구사항 단일 진실 공급원)
3. `CLAUDE.md` — Claude Code가 따르는 작업 원칙·구조
4. `.claude/skills/` — 작업별 상세 구현 가이드

## 아키텍처 (4계층)
| 단계 | 디렉터리 | 설명 |
|------|----------|------|
| 1 워치 앱 | `watch-app/` | Sensor SDK로 센서 수집 → 폰 전송 |
| 2 모바일 앱 | `mobile-app/` | 수신 → 변환 → 서버 업로드 |
| 3 백엔드 | `backend/` | Docker(PostgreSQL/MongoDB/Redis/웹포털) |
| 4 대시보드 | `dashboard/` | 웹 포털 + Grafana 실시간 시각화 |

> 현재 Android Studio 프로젝트에서는 `watch-app/` 역할을 `wear/` 모듈이,
> `mobile-app/` 역할을 `mobile/` 모듈이 담당한다.

## 수집 신호
- 실시간: 가속도(25Hz), PPG(25Hz), 심박/IBI(1Hz), EDA(1Hz), 피부온도
- 온디맨드: ECG(500Hz), PPG(100Hz), SpO2, BIA, MF-BIA, 피부온도, 발한량

## 핵심 제약
- 두 SDK 모두 **에뮬레이터 미지원** → Galaxy Watch4+ 실기기 필수
- 온디맨드: 포그라운드·동시 1개·30초 이내
- 데이터는 **연구·웰니스 목적 한정** (의료 진단 불가)

---
*Data Science & AI Convergence Lab.*
>>>>>>> 7e0e204 (Initial Heartproject API integration)
