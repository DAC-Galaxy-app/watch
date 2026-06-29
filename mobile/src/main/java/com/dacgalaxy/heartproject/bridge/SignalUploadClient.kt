package com.dacgalaxy.heartproject.bridge

import android.content.Context
import com.dacgalaxy.heartproject.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SignalUploadClient(context: Context) {
    private val appContext = context.applicationContext

    fun syncHealthData(samples: List<BiosignalSample>): UploadResult {
        if (samples.isEmpty()) return UploadResult.Success(0)

        val baseUrl = appContext.getString(R.string.server_base_url).trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            return UploadResult.Skipped("server_base_url is empty")
        }

        val payload = samples.toHealthStackBatchPayload(
            studyId = appContext.getString(R.string.default_study_id).ifBlank { DEFAULT_STUDY_ID },
        )

        val connection = (URL("$baseUrl/api/v1/health-data:sync-batch").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        return try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }
            val code = connection.responseCode
            if (code in 200..299) {
                UploadResult.Success(samples.size)
            } else {
                UploadResult.Failure("HTTP $code")
            }
        } catch (error: Exception) {
            UploadResult.Failure(error.message ?: error.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    private fun List<BiosignalSample>.toHealthStackBatchPayload(studyId: String): JSONObject {
        val grouped = groupBy { it.toHealthStackType() }
        return JSONObject().apply {
            put("study_ids", JSONArray().put(studyId))
            put("health_data", JSONArray().also { healthDataArray ->
                grouped.forEach { (healthDataType, groupedSamples) ->
                    healthDataArray.put(
                        JSONObject().apply {
                            put("type", healthDataType)
                            put("data_list", JSONArray().also { dataList ->
                                groupedSamples.forEach { sample ->
                                    dataList.put(sample.toHealthStackData())
                                }
                            })
                        },
                    )
                }
            })
        }
    }

    private fun BiosignalSample.toHealthStackData(): JSONObject {
        val data = JSONObject(valuesJson.ifBlank { "{}" })
        data.put("id", id)
        data.put("session_id", sessionId)
        data.put("tracker_type", trackerType)
        data.put("timestamp", timestamp)
        data.put("sent_at", sentAt)
        data.put("received_at", receivedAt)
        return data
    }

    private fun BiosignalSample.toHealthStackType(): String =
        when (trackerType) {
            "ACCELEROMETER", "ACCELEROMETER_CONTINUOUS" -> "HEALTH_DATA_TYPE_WEAR_ACCELEROMETER"
            "BIA", "BIA_ON_DEMAND", "MF_BIA_ON_DEMAND" -> "HEALTH_DATA_TYPE_WEAR_BIA"
            "ECG", "ECG_ON_DEMAND" -> "HEALTH_DATA_TYPE_WEAR_ECG"
            "HEART_RATE", "HEART_RATE_CONTINUOUS" -> "HEALTH_DATA_TYPE_WEAR_HEART_RATE"
            "PPG_GREEN" -> "HEALTH_DATA_TYPE_WEAR_PPG_GREEN"
            "PPG_IR" -> "HEALTH_DATA_TYPE_WEAR_PPG_IR"
            "PPG_RED" -> "HEALTH_DATA_TYPE_WEAR_PPG_RED"
            "PPG_CONTINUOUS", "PPG_ON_DEMAND" -> "HEALTH_DATA_TYPE_WEAR_PPG_GREEN"
            "SPO2", "SPO2_ON_DEMAND" -> "HEALTH_DATA_TYPE_WEAR_SPO2"
            "SWEAT_LOSS" -> "HEALTH_DATA_TYPE_WEAR_SWEAT_LOSS"
            else -> "HEALTH_DATA_TYPE_UNSPECIFIED"
        }

    companion object {
        private const val DEFAULT_STUDY_ID = "local-study"
    }
}

sealed interface UploadResult {
    data class Success(val count: Int) : UploadResult
    data class Skipped(val reason: String) : UploadResult
    data class Failure(val reason: String) : UploadResult
}
