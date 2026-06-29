#!/usr/bin/env python3
import json
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import quote, unquote, urlparse


DATA_DIR = Path(__file__).resolve().parent / "data"
UPLOAD_DIR = DATA_DIR / "uploads"
SIGNALS_FILE = DATA_DIR / "signals.ndjson"
HEALTH_DATA_FILE = DATA_DIR / "health_data.ndjson"
APP_LOGS_FILE = DATA_DIR / "app_logs.ndjson"
STUDY_DATA_FILES_FILE = DATA_DIR / "study_data_files.ndjson"


class SignalApiHandler(BaseHTTPRequestHandler):
    server_version = "HeartprojectSignalApi/0.2"

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/health":
            self.respond_json(200, {"status": "ok"})
            return
        if path == "/api/v1/signals":
            self.respond_json(200, {"signals": read_recent_json_lines(SIGNALS_FILE)})
            return
        if path == "/api/v1/health-data":
            self.respond_json(200, {"health_data": read_recent_json_lines(HEALTH_DATA_FILE)})
            return
        if path == "/api/v1/app-logs":
            self.respond_json(200, {"logs": read_recent_json_lines(APP_LOGS_FILE)})
            return
        if path == "/api/v1/study-data-files":
            self.respond_json(200, {"files": read_recent_json_lines(STUDY_DATA_FILES_FILE)})
            return
        self.respond_json(404, {"error": "not_found"})

    def do_POST(self):
        path = urlparse(self.path).path
        content_length = int(self.headers.get("Content-Length", "0"))
        try:
            payload = json.loads(self.rfile.read(content_length).decode("utf-8"))
            if path == "/api/v1/signals":
                self.handle_signal_upload(payload)
                return
            if path == "/api/v1/health-data:sync":
                self.handle_health_data_sync(payload, batch=False)
                return
            if path == "/api/v1/health-data:sync-batch":
                self.handle_health_data_sync(payload, batch=True)
                return
            if path == "/api/v1/files/presigned-url":
                self.handle_presigned_url(payload)
                return
            if path == "/api/v1/study-data-files":
                self.handle_study_data_file(payload)
                return
            if path == "/api/v1/app-logs":
                self.handle_app_log(payload)
                return
            self.respond_json(404, {"error": "not_found"})
        except ValueError as error:
            self.respond_json(400, {"error": str(error)})
        except Exception as error:
            self.respond_json(500, {"error": error.__class__.__name__})

    def do_PUT(self):
        path = urlparse(self.path).path
        if not path.startswith("/api/v1/files/upload/"):
            self.respond_json(404, {"error": "not_found"})
            return

        relative_path = unquote(path.removeprefix("/api/v1/files/upload/"))
        if not relative_path or ".." in Path(relative_path).parts:
            self.respond_json(400, {"error": "invalid upload path"})
            return

        destination = UPLOAD_DIR / relative_path
        destination.parent.mkdir(parents=True, exist_ok=True)
        content_length = int(self.headers.get("Content-Length", "0"))
        with destination.open("wb") as file:
            file.write(self.rfile.read(content_length))

        self.respond_json(
            201,
            {
                "file_path": str(destination.relative_to(DATA_DIR)),
                "size": destination.stat().st_size,
            },
        )

    def handle_signal_upload(self, payload):
        signals = normalize_signals(payload)
        append_json_lines(SIGNALS_FILE, signals)
        health_data = signals_to_health_data(["local-study"], signals)
        append_json_lines(HEALTH_DATA_FILE, health_data)
        self.respond_json(202, {"accepted": len(signals), "health_data_records": len(health_data)})

    def handle_health_data_sync(self, payload, batch):
        study_ids = normalize_study_ids(payload.get("study_ids") or payload.get("studyIds"))
        health_data = normalize_health_data_payload(payload, batch=batch, study_ids=study_ids)
        append_json_lines(HEALTH_DATA_FILE, health_data)
        signals = health_data_to_signals(health_data)
        if signals:
            append_json_lines(SIGNALS_FILE, signals)
        self.respond_json(202, {"accepted": sum(len(item["data_list"]) for item in health_data)})

    def handle_presigned_url(self, payload):
        file_name = require_string(payload, "file_name", fallback_key="fileName")
        study_id = require_string(payload, "study_id", fallback_key="studyId")
        upload_name = f"{study_id}/{uuid.uuid4()}-{Path(file_name).name}"
        upload_path = quote(upload_name, safe="/")
        host = self.headers.get("Host", "127.0.0.1:8080")
        self.respond_json(
            200,
            {
                "presigned_url": f"http://{host}/api/v1/files/upload/{upload_path}",
                "file_path": f"uploads/{upload_name}",
                "file_name": file_name,
                "study_id": study_id,
            },
        )

    def handle_study_data_file(self, payload):
        record = {
            "study_id": require_string(payload, "study_id", fallback_key="studyId"),
            "file_path": require_string(payload, "file_path", fallback_key="filePath"),
            "file_name": require_string(payload, "file_name", fallback_key="fileName"),
        }
        append_json_lines(STUDY_DATA_FILES_FILE, [record])
        self.respond_json(202, {"accepted": 1})

    def handle_app_log(self, payload):
        if not isinstance(payload, dict):
            raise ValueError("request body must be a JSON object")
        append_json_lines(APP_LOGS_FILE, [payload])
        self.respond_json(202, {"accepted": 1})

    def log_message(self, format, *args):
        print("%s - %s" % (self.address_string(), format % args))

    def respond_json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def normalize_health_data_payload(payload, batch, study_ids):
    if not isinstance(payload, dict):
        raise ValueError("request body must be a JSON object")

    if batch:
        items = payload.get("health_data") or payload.get("healthData")
        if not isinstance(items, list):
            raise ValueError("health_data must be a list")
    else:
        item = payload.get("health_data") or payload.get("healthData")
        items = [item if item is not None else payload]

    normalized = []
    for item in items:
        if not isinstance(item, dict):
            raise ValueError("each health_data item must be a JSON object")
        health_type = require_string(item, "type")
        data_list = item.get("data_list") or item.get("dataList")
        if not isinstance(data_list, list) or not data_list:
            raise ValueError("data_list must be a non-empty list")
        for data in data_list:
            if not isinstance(data, dict):
                raise ValueError("each data_list item must be a JSON object")
        normalized.append(
            {
                "study_ids": study_ids,
                "type": health_type,
                "data_list": data_list,
            }
        )
    return normalized


def signals_to_health_data(study_ids, signals):
    grouped = {}
    for signal in signals:
        grouped.setdefault(signal_to_health_data_type(signal["tracker_type"]), []).append(
            {
                **signal.get("values", {}),
                "id": signal["id"],
                "session_id": signal.get("session_id", ""),
                "tracker_type": signal["tracker_type"],
                "timestamp": signal["timestamp"],
                "sent_at": signal.get("sent_at", 0),
                "received_at": signal.get("received_at", 0),
            }
        )
    return [{"study_ids": study_ids, "type": key, "data_list": value} for key, value in grouped.items()]


def health_data_to_signals(health_data_items):
    signals = []
    for item in health_data_items:
        for data in item["data_list"]:
            signal_id = str(data.get("id") or uuid.uuid4())
            tracker_type = str(data.get("tracker_type") or item["type"])
            timestamp = data.get("timestamp")
            if timestamp is None:
                continue
            values = {
                key: value
                for key, value in data.items()
                if key not in {"id", "session_id", "tracker_type", "timestamp", "sent_at", "received_at"}
            }
            signals.append(
                {
                    "id": signal_id,
                    "session_id": data.get("session_id", ""),
                    "tracker_type": tracker_type,
                    "timestamp": timestamp,
                    "sent_at": data.get("sent_at", 0),
                    "received_at": data.get("received_at", 0),
                    "values": values,
                }
            )
    return signals


def signal_to_health_data_type(tracker_type):
    return {
        "ACCELEROMETER": "HEALTH_DATA_TYPE_WEAR_ACCELEROMETER",
        "ACCELEROMETER_CONTINUOUS": "HEALTH_DATA_TYPE_WEAR_ACCELEROMETER",
        "BIA": "HEALTH_DATA_TYPE_WEAR_BIA",
        "BIA_ON_DEMAND": "HEALTH_DATA_TYPE_WEAR_BIA",
        "MF_BIA_ON_DEMAND": "HEALTH_DATA_TYPE_WEAR_BIA",
        "ECG": "HEALTH_DATA_TYPE_WEAR_ECG",
        "ECG_ON_DEMAND": "HEALTH_DATA_TYPE_WEAR_ECG",
        "HEART_RATE": "HEALTH_DATA_TYPE_WEAR_HEART_RATE",
        "HEART_RATE_CONTINUOUS": "HEALTH_DATA_TYPE_WEAR_HEART_RATE",
        "PPG_GREEN": "HEALTH_DATA_TYPE_WEAR_PPG_GREEN",
        "PPG_IR": "HEALTH_DATA_TYPE_WEAR_PPG_IR",
        "PPG_RED": "HEALTH_DATA_TYPE_WEAR_PPG_RED",
        "PPG_CONTINUOUS": "HEALTH_DATA_TYPE_WEAR_PPG_GREEN",
        "PPG_ON_DEMAND": "HEALTH_DATA_TYPE_WEAR_PPG_GREEN",
        "SPO2": "HEALTH_DATA_TYPE_WEAR_SPO2",
        "SPO2_ON_DEMAND": "HEALTH_DATA_TYPE_WEAR_SPO2",
        "SWEAT_LOSS": "HEALTH_DATA_TYPE_WEAR_SWEAT_LOSS",
    }.get(tracker_type, "HEALTH_DATA_TYPE_UNSPECIFIED")


def normalize_study_ids(value):
    if value is None:
        return ["local-study"]
    if isinstance(value, str) and value:
        return [value]
    if isinstance(value, list) and all(isinstance(item, str) and item for item in value):
        return value
    raise ValueError("study_ids must be a non-empty string or a list of strings")


def require_string(payload, key, fallback_key=None):
    if not isinstance(payload, dict):
        raise ValueError("request body must be a JSON object")
    value = payload.get(key)
    if value is None and fallback_key:
        value = payload.get(fallback_key)
    if not isinstance(value, str) or not value:
        raise ValueError(f"missing required field: {key}")
    return value


def normalize_signals(payload):
    if isinstance(payload, dict) and isinstance(payload.get("signals"), list):
        signals = payload["signals"]
    elif isinstance(payload, dict):
        signals = [payload]
    else:
        raise ValueError("request body must be a JSON object")

    for signal in signals:
        if not isinstance(signal, dict):
            raise ValueError("each signal must be a JSON object")
        for key in ("id", "tracker_type", "timestamp", "values"):
            if key not in signal:
                raise ValueError(f"missing required field: {key}")
        if not isinstance(signal["values"], dict):
            raise ValueError("values must be a JSON object")
    return signals


def append_json_lines(path, records):
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as file:
        for record in records:
            file.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")))
            file.write("\n")


def read_recent_json_lines(path, limit=100):
    if not path.exists():
        return []
    lines = path.read_text(encoding="utf-8").splitlines()[-limit:]
    return [json.loads(line) for line in lines if line.strip()]


def main():
    server = ThreadingHTTPServer(("0.0.0.0", 8080), SignalApiHandler)
    print("Heartproject signal API listening on http://0.0.0.0:8080")
    server.serve_forever()


if __name__ == "__main__":
    main()
