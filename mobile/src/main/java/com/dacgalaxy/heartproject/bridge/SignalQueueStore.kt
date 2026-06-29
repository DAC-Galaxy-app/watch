package com.dacgalaxy.heartproject.bridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SignalQueueStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun enqueue(sample: BiosignalSample) {
        val queue = readQueue()
        queue.put(sample.toJson())
        preferences.edit()
            .putString(KEY_QUEUE, queue.toString())
            .putLong(KEY_RECEIVED_COUNT, receivedCount() + 1)
            .putString(KEY_LAST_TRACKER, sample.trackerType)
            .apply()
    }

    @Synchronized
    fun replaceQueue(samples: List<BiosignalSample>) {
        val queue = JSONArray()
        samples.forEach { queue.put(it.toJson()) }
        preferences.edit().putString(KEY_QUEUE, queue.toString()).apply()
    }

    @Synchronized
    fun queuedSamples(): List<BiosignalSample> {
        val queue = readQueue()
        return buildList {
            for (index in 0 until queue.length()) {
                val item = queue.optJSONObject(index) ?: continue
                add(BiosignalSample.fromJson(item))
            }
        }
    }

    fun markUploadSuccess(count: Int) {
        preferences.edit().putLong(KEY_UPLOADED_COUNT, uploadedCount() + count).apply()
    }

    fun queuedCount(): Int = readQueue().length()

    fun receivedCount(): Long = preferences.getLong(KEY_RECEIVED_COUNT, 0L)

    fun uploadedCount(): Long = preferences.getLong(KEY_UPLOADED_COUNT, 0L)

    fun lastTracker(): String = preferences.getString(KEY_LAST_TRACKER, "-") ?: "-"

    private fun readQueue(): JSONArray {
        val raw = preferences.getString(KEY_QUEUE, "[]") ?: "[]"
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }

    companion object {
        private const val PREFS_NAME = "biosignal_queue"
        private const val KEY_QUEUE = "queue"
        private const val KEY_RECEIVED_COUNT = "received_count"
        private const val KEY_UPLOADED_COUNT = "uploaded_count"
        private const val KEY_LAST_TRACKER = "last_tracker"
    }
}
