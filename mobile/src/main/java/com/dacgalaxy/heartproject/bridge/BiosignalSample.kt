package com.dacgalaxy.heartproject.bridge

import org.json.JSONObject

data class BiosignalSample(
    val id: String,
    val sessionId: String,
    val trackerType: String,
    val timestamp: Long,
    val sentAt: Long,
    val receivedAt: Long,
    val valuesJson: String,
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("session_id", sessionId)
            put("tracker_type", trackerType)
            put("timestamp", timestamp)
            put("sent_at", sentAt)
            put("received_at", receivedAt)
            put("values", JSONObject(valuesJson.ifBlank { "{}" }))
        }

    companion object {
        fun fromJson(json: JSONObject): BiosignalSample =
            BiosignalSample(
                id = json.getString("id"),
                sessionId = json.optString("session_id"),
                trackerType = json.getString("tracker_type"),
                timestamp = json.optLong("timestamp"),
                sentAt = json.optLong("sent_at"),
                receivedAt = json.optLong("received_at"),
                valuesJson = json.optJSONObject("values")?.toString() ?: "{}",
            )
    }
}
