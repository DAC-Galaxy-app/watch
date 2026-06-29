# Heartproject Signal API

Local receiver for watch biosignal uploads. The server keeps the old raw
`signals` API and also exposes REST-compatible endpoints that mirror the
S-HealthStack SDK backend calls used for health data sync, file upload,
study data file registration, and app logs.

```bash
python3 backend/server.py
```

Endpoints:

- `GET /health`
- `POST /api/v1/health-data:sync`
- `POST /api/v1/health-data:sync-batch`
- `GET /api/v1/health-data`
- `POST /api/v1/files/presigned-url`
- `PUT /api/v1/files/upload/{study_id}/{file_name}`
- `POST /api/v1/study-data-files`
- `GET /api/v1/study-data-files`
- `POST /api/v1/app-logs`
- `GET /api/v1/app-logs`
- `POST /api/v1/signals` legacy raw signal compatibility
- `GET /api/v1/signals` legacy raw signal compatibility

Android mobile posts this HealthStack-compatible batch shape:

```json
{
  "study_ids": ["local-study"],
  "health_data": [
    {
      "type": "HEALTH_DATA_TYPE_WEAR_PPG_GREEN",
      "data_list": [
        {
          "id": "uuid",
          "session_id": "uuid",
          "tracker_type": "PPG_CONTINUOUS",
          "timestamp": 1780000000000,
          "sent_at": 1780000000100,
          "received_at": 1780000000200,
          "ppg_green": 1840
        }
      ]
    }
  ]
}
```

Legacy raw signal shape:

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
