# Heartproject API Integration

## 확인한 구조

현재 프로젝트는 `wear`, `mobile`, `backend` 3계층으로 구성된다.

| 계층 | 역할 | API 방향 |
| --- | --- | --- |
| `wear` | Samsung Health Sensor SDK로 생체신호 수집 | Wear OS Data Layer API로 모바일에 전송 |
| `mobile` | 워치 데이터를 수신하고 서버 payload로 변환 | 백엔드 REST API 호출 |
| `backend` | 모바일 업로드를 수신하고 NDJSON 파일로 저장 | 조회, 파일 업로드, 로그 수신 API 제공 |

## S-HealthStack GitHub 대조

대조 기준 저장소는 `https://github.com/S-HealthStack/app-sdk`, 확인 커밋은
`58b4b9b64623a7d2a0865307d490a59a7538d7cd`이다.

S-HealthStack 앱 SDK는 모바일에서 `ManagedChannelBuilder.forAddress(SERVER_ADDRESS, SERVER_PORT)`로 gRPC 채널을 만들고, 다음 서비스 어댑터를 통해 백엔드를 호출한다.

| S-HealthStack 호출 | 확인 파일 | Heartproject 반영 |
| --- | --- | --- |
| `HealthDataService.syncHealthData(studyIds, healthData)` | `HealthDataAdapter.kt` | `POST /api/v1/health-data:sync`로 반영 |
| `HealthDataService.syncBatchHealthData(studyIds, batchHealthData)` | `HealthDataAdapter.kt` | 모바일 기본 업로드 경로 `POST /api/v1/health-data:sync-batch`로 반영 |
| `FileService.getPresignedUrl(fileName, studyId)` | `FileAdapter.kt` | `POST /api/v1/files/presigned-url`로 반영 |
| presigned URL에 `PUT` 업로드 | `FileUploadApi.kt` | `PUT /api/v1/files/upload/{path}`로 반영 |
| `StudyDataService.addStudyDataFile(studyId, filePath, fileName)` | `StudyDataAdapter.kt` | `POST /api/v1/study-data-files`로 반영 |
| `AppLogService.sendAppLog(appLog)` | `AppLogAdapter.kt` | `POST /api/v1/app-logs`로 반영 |
| `StudyService.*` | `StudyAdapter.kt` | 미반영. 현재 앱에 연구 참여/동의/목록 UI와 모델이 없음 |
| `Subject.*` | `SubjectAdapter.kt` | 미반영. 현재 앱에 회원/프로필 도메인이 없음 |
| `TaskService.*` | `TaskAdapter.kt` | 미반영. 현재 앱에 설문/과제 도메인이 없음 |

## 백엔드가 받는 API

### Health data batch sync

`POST /api/v1/health-data:sync-batch`

모바일 앱이 기본으로 호출한다. S-HealthStack의 `syncBatchHealthData` 개념을 REST 형태로 옮긴 API이다.

```json
{
  "study_ids": ["local-study"],
  "health_data": [
    {
      "type": "HEALTH_DATA_TYPE_WEAR_HEART_RATE",
      "data_list": [
        {
          "id": "uuid",
          "session_id": "uuid",
          "tracker_type": "HEART_RATE_CONTINUOUS",
          "timestamp": 1780000000000,
          "sent_at": 1780000000100,
          "received_at": 1780000000200,
          "heart_rate": 72
        }
      ]
    }
  ]
}
```

응답:

```json
{ "accepted": 1 }
```

### Health data single sync

`POST /api/v1/health-data:sync`

S-HealthStack의 `syncHealthData` 개념을 REST 형태로 옮긴 API이다.

```json
{
  "study_ids": ["local-study"],
  "health_data": {
    "type": "HEALTH_DATA_TYPE_WEAR_SPO2",
    "data_list": [
      {
        "id": "uuid",
        "tracker_type": "SPO2_ON_DEMAND",
        "timestamp": 1780000000000,
        "spo2": 98
      }
    ]
  }
}
```

### Raw signal compatibility

`POST /api/v1/signals`

기존 Heartproject payload를 계속 받을 수 있도록 남겨둔 호환 API이다. 수신 후 HealthStack형 `health_data` 레코드도 같이 생성한다.

```json
{
  "signals": [
    {
      "id": "uuid",
      "session_id": "uuid",
      "tracker_type": "PPG_CONTINUOUS",
      "timestamp": 1780000000000,
      "sent_at": 1780000000100,
      "received_at": 1780000000200,
      "values": {
        "ppg_green": 1840
      }
    }
  ]
}
```

### File presigned URL

`POST /api/v1/files/presigned-url`

S-HealthStack의 `getPresignedUrl(fileName, studyId)`에 대응한다.

```json
{
  "file_name": "wear-data.json",
  "study_id": "local-study"
}
```

응답의 `presigned_url`로 파일을 `PUT`한다.

### Study data file registration

`POST /api/v1/study-data-files`

S-HealthStack의 `addStudyDataFile(studyId, filePath, fileName)`에 대응한다.

```json
{
  "study_id": "local-study",
  "file_path": "uploads/local-study/file.json",
  "file_name": "file.json"
}
```

### App log

`POST /api/v1/app-logs`

S-HealthStack의 `sendAppLog(appLog)`에 대응한다. 현재는 JSON 객체를 그대로 저장한다.

## 백엔드가 주는 API

| API | 설명 |
| --- | --- |
| `GET /health` | 서버 상태 확인 |
| `GET /api/v1/health-data` | 최근 HealthStack형 health data 100개 조회 |
| `GET /api/v1/signals` | 최근 raw signal 100개 조회 |
| `GET /api/v1/study-data-files` | 등록된 study data file 조회 |
| `GET /api/v1/app-logs` | 수신된 app log 조회 |
| `POST /api/v1/files/presigned-url` | 업로드 가능한 로컬 URL 발급 |

## 모바일이 호출하는 API

`mobile/src/main/java/com/dacgalaxy/heartproject/bridge/SignalUploadClient.kt`는 워치에서 받은 샘플을 tracker type별로 묶고, 다음 타입 문자열로 변환해 `/api/v1/health-data:sync-batch`를 호출한다.

| Watch tracker | HealthStack type |
| --- | --- |
| `ACCELEROMETER`, `ACCELEROMETER_CONTINUOUS` | `HEALTH_DATA_TYPE_WEAR_ACCELEROMETER` |
| `BIA`, `BIA_ON_DEMAND`, `MF_BIA_ON_DEMAND` | `HEALTH_DATA_TYPE_WEAR_BIA` |
| `ECG`, `ECG_ON_DEMAND` | `HEALTH_DATA_TYPE_WEAR_ECG` |
| `HEART_RATE`, `HEART_RATE_CONTINUOUS` | `HEALTH_DATA_TYPE_WEAR_HEART_RATE` |
| `PPG_GREEN`, `PPG_CONTINUOUS`, `PPG_ON_DEMAND` | `HEALTH_DATA_TYPE_WEAR_PPG_GREEN` |
| `PPG_IR` | `HEALTH_DATA_TYPE_WEAR_PPG_IR` |
| `PPG_RED` | `HEALTH_DATA_TYPE_WEAR_PPG_RED` |
| `SPO2`, `SPO2_ON_DEMAND` | `HEALTH_DATA_TYPE_WEAR_SPO2` |
| `SWEAT_LOSS` | `HEALTH_DATA_TYPE_WEAR_SWEAT_LOSS` |
| 기타 | `HEALTH_DATA_TYPE_UNSPECIFIED` |

## 결론

수정 전에는 S-HealthStack API 호출 구조가 코드에 반영되어 있지 않았고, 모바일은 자체 `POST /api/v1/signals`만 호출했다. 수정 후에는 생체신호 업로드 경로가 S-HealthStack의 `syncBatchHealthData` 구조와 맞게 바뀌었고, 백엔드는 HealthData/File/StudyDataFile/AppLog 계열 API를 REST 호환 형태로 제공한다.

S-HealthStack의 Study/Subject/Task API는 아직 반영하지 않았다. 현재 Heartproject에는 연구 목록, 연구 참여, 동의서, 사용자 프로필, 과제/설문 기능이 없기 때문에 해당 API를 연결하려면 별도 도메인 모델과 화면 흐름을 먼저 추가해야 한다.
