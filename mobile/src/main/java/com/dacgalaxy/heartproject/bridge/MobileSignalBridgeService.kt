package com.dacgalaxy.heartproject.bridge

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class MobileSignalBridgeService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val store = SignalQueueStore(this)
        val uploadClient = SignalUploadClient(this)

        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val dataItem = event.dataItem
            if (!dataItem.uri.path.orEmpty().startsWith(BiosignalDataContract.DATA_PATH_PREFIX)) return@forEach

            val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
            val sample = BiosignalSample(
                id = dataMap.getString(BiosignalDataContract.KEY_ID) ?: dataItem.uri.lastPathSegment.orEmpty(),
                sessionId = dataMap.getString(BiosignalDataContract.KEY_SESSION_ID) ?: "",
                trackerType = dataMap.getString(BiosignalDataContract.KEY_TRACKER_TYPE) ?: "UNKNOWN",
                timestamp = dataMap.getLong(BiosignalDataContract.KEY_TIMESTAMP),
                sentAt = dataMap.getLong(BiosignalDataContract.KEY_SENT_AT),
                receivedAt = System.currentTimeMillis(),
                valuesJson = dataMap.getString(BiosignalDataContract.KEY_VALUES_JSON) ?: "{}",
            )
            store.enqueue(sample)
        }

        Thread {
            syncQueuedSignals(store, uploadClient)
        }.start()
    }

    private fun syncQueuedSignals(store: SignalQueueStore, uploadClient: SignalUploadClient) {
        val queued = store.queuedSamples()
        when (val result = uploadClient.syncHealthData(queued)) {
            is UploadResult.Success -> {
                store.markUploadSuccess(result.count)
                store.replaceQueue(emptyList())
                Log.i(TAG, "Uploaded ${result.count} biosignal samples")
            }
            is UploadResult.Skipped -> Log.i(TAG, "Upload skipped: ${result.reason}")
            is UploadResult.Failure -> Log.w(TAG, "Upload failed: ${result.reason}")
        }
    }

    companion object {
        private const val TAG = "MobileSignalBridge"
    }
}
