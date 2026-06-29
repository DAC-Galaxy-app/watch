package com.dacgalaxy.heartproject.sensor

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.dacgalaxy.heartproject.data.BiosignalSample
import com.dacgalaxy.heartproject.data.WearDataLayerSender
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.PpgType
import com.samsung.android.service.health.tracking.data.ValueKey
import java.util.EnumSet
import java.util.UUID

data class SensorRuntimeState(
    val connected: Boolean = false,
    val supportedTrackers: Set<HealthTrackerType> = emptySet(),
    val activeTrackers: Set<HealthTrackerType> = emptySet(),
    val sessionId: String = "",
    val samplesSent: Int = 0,
    val latestTracker: String = "-",
    val latestValues: String = "-",
    val status: String = "Ready",
)

class SamsungSensorController(
    context: Context,
    private val sender: WearDataLayerSender,
    private val onStateChanged: (SensorRuntimeState) -> Unit,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeTrackers = mutableMapOf<HealthTrackerType, HealthTracker>()
    private var service: HealthTrackingService? = null
    private var state = SensorRuntimeState()

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            val capability = service?.trackingCapability
            update(
                state.copy(
                    connected = true,
                    supportedTrackers = capability?.supportHealthTrackerTypes?.toSet() ?: emptySet(),
                    status = "Sensor SDK connected: ${capability?.version ?: "unknown"}",
                ),
            )
        }

        override fun onConnectionEnded() {
            activeTrackers.clear()
            update(state.copy(connected = false, activeTrackers = emptySet(), status = "Sensor SDK disconnected"))
        }

        override fun onConnectionFailed(exception: HealthTrackerException) {
            update(
                state.copy(
                    connected = false,
                    status = "Sensor SDK failed: ${exception.message ?: exception.errorCode}",
                ),
            )
        }
    }

    fun connect() {
        if (service != null) return
        service = HealthTrackingService(connectionListener, appContext)
        service?.connectService()
        update(state.copy(status = "Connecting Sensor SDK"))
    }

    fun disconnect() {
        stopAll()
        service?.disconnectService()
        service = null
        update(state.copy(connected = false, status = "Sensor SDK disconnected"))
    }

    fun startContinuousSession() {
        if (!state.connected) {
            update(state.copy(status = "Sensor SDK is not connected"))
            return
        }
        val sessionId = state.sessionId.ifBlank { UUID.randomUUID().toString() }
        update(state.copy(sessionId = sessionId, status = "Starting continuous trackers"))
        continuousTrackers.forEach(::startTracker)
    }

    fun stopAll() {
        activeTrackers.values.forEach { tracker ->
            runCatching { tracker.unsetEventListener() }
        }
        activeTrackers.clear()
        update(state.copy(activeTrackers = emptySet(), sessionId = "", status = "All trackers stopped"))
    }

    fun startOnDemand(type: HealthTrackerType) {
        if (!onDemandTrackers.contains(type)) {
            update(state.copy(status = "$type is not an on-demand tracker"))
            return
        }
        if (activeTrackers.keys.any { onDemandTrackers.contains(it) }) {
            update(state.copy(status = "Another on-demand tracker is already active"))
            return
        }
        val sessionId = state.sessionId.ifBlank { UUID.randomUUID().toString() }
        update(state.copy(sessionId = sessionId, status = "Starting $type"))
        startTracker(type)
        mainHandler.postDelayed({
            stopTracker(type)
        }, ON_DEMAND_TIMEOUT_MS)
    }

    private fun startTracker(type: HealthTrackerType) {
        val trackingService = service
        if (trackingService == null || !state.connected) {
            update(state.copy(status = "Sensor SDK is not connected"))
            return
        }
        if (!state.supportedTrackers.contains(type)) {
            update(state.copy(status = "$type is not supported on this device"))
            return
        }
        if (activeTrackers.containsKey(type)) {
            update(state.copy(status = "$type is already active"))
            return
        }

        val tracker = runCatching {
            if (type == HealthTrackerType.PPG_CONTINUOUS || type == HealthTrackerType.PPG_ON_DEMAND) {
                trackingService.getHealthTracker(type, EnumSet.allOf(PpgType::class.java))
            } else {
                trackingService.getHealthTracker(type)
            }
        }.getOrElse { error ->
            update(state.copy(status = "Cannot open $type: ${error.message ?: error.javaClass.simpleName}"))
            return
        }

        tracker.setEventListener(listenerFor(type))
        activeTrackers[type] = tracker
        update(state.copy(activeTrackers = activeTrackers.keys.toSet(), status = "$type started"))
    }

    private fun stopTracker(type: HealthTrackerType) {
        val tracker = activeTrackers.remove(type) ?: return
        runCatching { tracker.unsetEventListener() }
        val newSessionId = if (activeTrackers.isEmpty()) "" else state.sessionId
        update(state.copy(activeTrackers = activeTrackers.keys.toSet(), sessionId = newSessionId, status = "$type stopped"))
    }

    private fun listenerFor(type: HealthTrackerType) =
        object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
                dataPoints.forEach { dataPoint ->
                    val sample = BiosignalSample(
                        sessionId = state.sessionId.ifBlank { UUID.randomUUID().toString() },
                        trackerType = type.name,
                        timestamp = dataPoint.timestamp,
                        values = dataPoint.extractValues(type),
                    )
                    sender.send(sample) { success, message ->
                        val sentCount = if (success) state.samplesSent + 1 else state.samplesSent
                        update(
                            state.copy(
                                samplesSent = sentCount,
                                latestTracker = type.name,
                                latestValues = sample.values.entries.joinToString(limit = 3) { "${it.key}=${it.value}" },
                                status = message,
                            ),
                        )
                    }
                }
            }

            override fun onFlushCompleted() {
                update(state.copy(status = "$type flush completed"))
            }

            override fun onError(error: HealthTracker.TrackerError) {
                update(state.copy(status = "$type error: $error"))
            }
        }

    private fun DataPoint.extractValues(type: HealthTrackerType): Map<String, Any?> {
        val values = linkedMapOf<String, Any?>()
        when (type) {
            HealthTrackerType.PPG_GREEN,
            HealthTrackerType.PPG_RED,
            HealthTrackerType.PPG_IR,
            HealthTrackerType.PPG_CONTINUOUS,
            HealthTrackerType.PPG_ON_DEMAND -> {
                values.putValue("ppg_green", this, ValueKey.PpgSet.PPG_GREEN)
                values.putValue("green_status", this, ValueKey.PpgSet.GREEN_STATUS)
                values.putValue("ppg_ir", this, ValueKey.PpgSet.PPG_IR)
                values.putValue("ir_status", this, ValueKey.PpgSet.IR_STATUS)
                values.putValue("ppg_red", this, ValueKey.PpgSet.PPG_RED)
                values.putValue("red_status", this, ValueKey.PpgSet.RED_STATUS)
            }
            HealthTrackerType.HEART_RATE,
            HealthTrackerType.HEART_RATE_CONTINUOUS -> {
                values.putValue("heart_rate", this, ValueKey.HeartRateSet.HEART_RATE)
                values.putValue("heart_rate_status", this, ValueKey.HeartRateSet.HEART_RATE_STATUS)
                values.putValue("ibi_list", this, ValueKey.HeartRateSet.IBI_LIST)
                values.putValue("ibi_status_list", this, ValueKey.HeartRateSet.IBI_STATUS_LIST)
            }
            HealthTrackerType.ECG,
            HealthTrackerType.ECG_ON_DEMAND -> {
                values.putValue("ecg_mv", this, ValueKey.EcgSet.ECG_MV)
                values.putValue("lead_off", this, ValueKey.EcgSet.LEAD_OFF)
                values.putValue("sequence", this, ValueKey.EcgSet.SEQUENCE)
                values.putValue("ppg_green", this, ValueKey.EcgSet.PPG_GREEN)
                values.putValue("max_threshold_mv", this, ValueKey.EcgSet.MAX_THRESHOLD_MV)
                values.putValue("min_threshold_mv", this, ValueKey.EcgSet.MIN_THRESHOLD_MV)
            }
            HealthTrackerType.ACCELEROMETER,
            HealthTrackerType.ACCELEROMETER_CONTINUOUS -> {
                values.putValue("x", this, ValueKey.AccelerometerSet.ACCELEROMETER_X)
                values.putValue("y", this, ValueKey.AccelerometerSet.ACCELEROMETER_Y)
                values.putValue("z", this, ValueKey.AccelerometerSet.ACCELEROMETER_Z)
            }
            HealthTrackerType.EDA_CONTINUOUS -> {
                values.putValue("skin_conductance", this, ValueKey.EdaSet.SKIN_CONDUCTANCE)
                values.putValue("status", this, ValueKey.EdaSet.STATUS)
            }
            HealthTrackerType.SKIN_TEMPERATURE,
            HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS,
            HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND -> {
                values.putValue("object_temperature", this, ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE)
                values.putValue("ambient_temperature", this, ValueKey.SkinTemperatureSet.AMBIENT_TEMPERATURE)
                values.putValue("status", this, ValueKey.SkinTemperatureSet.STATUS)
            }
            HealthTrackerType.SPO2,
            HealthTrackerType.SPO2_ON_DEMAND -> {
                values.putValue("spo2", this, ValueKey.SpO2Set.SPO2)
                values.putValue("heart_rate", this, ValueKey.SpO2Set.HEART_RATE)
                values.putValue("accuracy_flag", this, ValueKey.SpO2Set.ACCURACY_FLAG)
                values.putValue("status", this, ValueKey.SpO2Set.STATUS)
            }
            HealthTrackerType.BIA,
            HealthTrackerType.BIA_ON_DEMAND -> {
                values.putValue("status", this, ValueKey.BiaSet.STATUS)
                values.putValue("body_fat_ratio", this, ValueKey.BiaSet.BODY_FAT_RATIO)
                values.putValue("body_fat_mass", this, ValueKey.BiaSet.BODY_FAT_MASS)
                values.putValue("total_body_water", this, ValueKey.BiaSet.TOTAL_BODY_WATER)
                values.putValue("skeletal_muscle_ratio", this, ValueKey.BiaSet.SKELETAL_MUSCLE_RATIO)
                values.putValue("skeletal_muscle_mass", this, ValueKey.BiaSet.SKELETAL_MUSCLE_MASS)
                values.putValue("basal_metabolic_rate", this, ValueKey.BiaSet.BASAL_METABOLIC_RATE)
                values.putValue("fat_free_ratio", this, ValueKey.BiaSet.FAT_FREE_RATIO)
                values.putValue("fat_free_mass", this, ValueKey.BiaSet.FAT_FREE_MASS)
                values.putValue("progress", this, ValueKey.BiaSet.PROGRESS)
                values.putValue("body_impedance_magnitude", this, ValueKey.BiaSet.BODY_IMPEDANCE_MAGNITUDE)
                values.putValue("body_impedance_degree", this, ValueKey.BiaSet.BODY_IMPEDANCE_DEGREE)
            }
            HealthTrackerType.MF_BIA_ON_DEMAND -> {
                values.putValue("status", this, ValueKey.MfBiaSet.STATUS)
                values.putValue("progress", this, ValueKey.MfBiaSet.PROGRESS)
                values.putValue("magnitude_5k", this, ValueKey.MfBiaSet.BODY_IMPEDANCE_MAGNITUDE_5K)
                values.putValue("phase_5k", this, ValueKey.MfBiaSet.BODY_IMPEDANCE_PHASE_5K)
                values.putValue("magnitude_10k", this, ValueKey.MfBiaSet.BODY_IMPEDANCE_MAGNITUDE_10K)
                values.putValue("phase_10k", this, ValueKey.MfBiaSet.BODY_IMPEDANCE_PHASE_10K)
                values.putValue("magnitude_50k", this, ValueKey.MfBiaSet.BODY_IMPEDANCE_MAGNITUDE_50K)
                values.putValue("phase_50k", this, ValueKey.MfBiaSet.BODY_IMPEDANCE_PHASE_50K)
                values.putValue("magnitude_250k", this, ValueKey.MfBiaSet.BODY_IMPEDANCE_MAGNITUDE_250K)
                values.putValue("phase_250k", this, ValueKey.MfBiaSet.BODY_IMPEDANCE_PHASE_250K)
            }
            HealthTrackerType.SWEAT_LOSS -> {
                values.putValue("sweat_loss", this, ValueKey.SweatLossSet.SWEAT_LOSS)
                values.putValue("status", this, ValueKey.SweatLossSet.STATUS)
            }
        }
        return values
    }

    private fun <T> MutableMap<String, Any?>.putValue(name: String, dataPoint: DataPoint, key: ValueKey<T>) {
        val value = runCatching { dataPoint.getValue(key) }.getOrNull()
        if (value != null) this[name] = value
    }

    private fun update(newState: SensorRuntimeState) {
        state = newState
        mainHandler.post { onStateChanged(state) }
    }

    companion object {
        private const val ON_DEMAND_TIMEOUT_MS = 30_000L

        val continuousTrackers = listOf(
            HealthTrackerType.ACCELEROMETER_CONTINUOUS,
            HealthTrackerType.PPG_CONTINUOUS,
            HealthTrackerType.HEART_RATE_CONTINUOUS,
            HealthTrackerType.EDA_CONTINUOUS,
            HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS,
        )

        val onDemandTrackers = listOf(
            HealthTrackerType.ECG_ON_DEMAND,
            HealthTrackerType.PPG_ON_DEMAND,
            HealthTrackerType.SPO2_ON_DEMAND,
            HealthTrackerType.BIA_ON_DEMAND,
            HealthTrackerType.MF_BIA_ON_DEMAND,
            HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND,
            HealthTrackerType.SWEAT_LOSS,
        )
    }
}
