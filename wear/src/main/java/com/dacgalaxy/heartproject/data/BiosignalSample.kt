package com.dacgalaxy.heartproject.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class BiosignalSample(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val trackerType: String,
    val timestamp: Long,
    val values: Map<String, Any?>,
) {
    fun valuesJson(): String {
        val json = JSONObject()
        values.forEach { (key, value) ->
            json.put(key, value.toJsonValue())
        }
        return json.toString()
    }
}

private fun Any?.toJsonValue(): Any =
    when (this) {
        null -> JSONObject.NULL
        is Iterable<*> -> JSONArray().also { array ->
            forEach { array.put(it.toJsonValue()) }
        }
        is Array<*> -> JSONArray().also { array ->
            forEach { array.put(it.toJsonValue()) }
        }
        else -> this
    }
