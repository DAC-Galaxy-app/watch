package com.dacgalaxy.heartproject.data

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class WearDataLayerSender(context: Context) {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    fun send(sample: BiosignalSample, onResult: (Boolean, String) -> Unit) {
        val request = PutDataMapRequest.create("${BiosignalDataContract.DATA_PATH_PREFIX}/${sample.id}")
        request.dataMap.putString(BiosignalDataContract.KEY_ID, sample.id)
        request.dataMap.putString(BiosignalDataContract.KEY_SESSION_ID, sample.sessionId)
        request.dataMap.putString(BiosignalDataContract.KEY_TRACKER_TYPE, sample.trackerType)
        request.dataMap.putLong(BiosignalDataContract.KEY_TIMESTAMP, sample.timestamp)
        request.dataMap.putString(BiosignalDataContract.KEY_VALUES_JSON, sample.valuesJson())
        request.dataMap.putLong(BiosignalDataContract.KEY_SENT_AT, System.currentTimeMillis())

        dataClient.putDataItem(request.asPutDataRequest().setUrgent())
            .addOnSuccessListener { onResult(true, "Sent ${sample.trackerType}") }
            .addOnFailureListener { error ->
                onResult(false, "Data Layer failed: ${error.message ?: error.javaClass.simpleName}")
            }
    }
}
