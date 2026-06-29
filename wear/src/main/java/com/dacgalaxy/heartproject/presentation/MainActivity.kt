package com.dacgalaxy.heartproject.presentation

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.dacgalaxy.heartproject.data.WearDataLayerSender
import com.dacgalaxy.heartproject.presentation.theme.HeartprojectTheme
import com.dacgalaxy.heartproject.sensor.SamsungSensorController
import com.dacgalaxy.heartproject.sensor.SensorRuntimeState
import com.samsung.android.service.health.tracking.data.HealthTrackerType

class MainActivity : ComponentActivity() {
    private lateinit var sensorController: SamsungSensorController
    private var uiState by mutableStateOf(SensorRuntimeState())

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val hasBodySensors = result[Manifest.permission.BODY_SENSORS] == true
            if (hasBodySensors) {
                sensorController.connect()
            } else {
                uiState = uiState.copy(status = "BODY_SENSORS permission is required")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorController = SamsungSensorController(
            context = this,
            sender = WearDataLayerSender(this),
            onStateChanged = { uiState = it },
        )

        setContent {
            WearSensorApp(
                state = uiState,
                onConnect = { requestSensorPermissions() },
                onStartContinuous = { sensorController.startContinuousSession() },
                onStop = { sensorController.stopAll() },
                onStartOnDemand = { sensorController.startOnDemand(it) },
            )
        }

        requestSensorPermissions()
    }

    override fun onDestroy() {
        sensorController.disconnect()
        super.onDestroy()
    }

    private fun requestSensorPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ),
        )
    }
}

@Composable
fun WearSensorApp(
    state: SensorRuntimeState,
    onConnect: () -> Unit,
    onStartContinuous: () -> Unit,
    onStop: () -> Unit,
    onStartOnDemand: (HealthTrackerType) -> Unit,
) {
    HeartprojectTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            ScreenScaffold(scrollState = listState) { contentPadding ->
                TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
                    item {
                        ListHeader(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("Heartproject", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                Text(
                                    text = if (state.activeTrackers.isNotEmpty()) "생체신호 수집 중" else "수집 대기",
                                    color = AccentGreen,
                                )
                            }
                        }
                    }
                    item {
                        CollectionPanel(state)
                    }
                    item {
                        if (state.connected) {
                            if (state.activeTrackers.isEmpty()) {
                                Button(onClick = onStartContinuous, modifier = Modifier.fillMaxWidth()) {
                                    Text("수집 시작")
                                }
                            } else {
                                Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                                    Text("수집 중지")
                                }
                            }
                        } else {
                            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                                Text("센서 연결")
                            }
                        }
                    }
                    item {
                        AutoTransferPanel(state)
                    }
                    item {
                        DataSummaryPanel(state)
                    }
                    item {
                        SectionLabel("온디맨드 측정")
                    }
                    SamsungSensorController.onDemandTrackers.forEach { trackerType ->
                        item {
                            Button(
                                onClick = { onStartOnDemand(trackerType) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state.supportedTrackers.contains(trackerType),
                            ) {
                                Text(onDemandLabel(trackerType))
                            }
                        }
                    }
                    item {
                        Text(
                            text = "연구/웰니스용. 진단용 아님.",
                            modifier = Modifier.fillMaxWidth(),
                            color = MutedText,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionPanel(state: SensorRuntimeState) {
    StatusPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MetricTile(
                label = "수집",
                value = if (state.activeTrackers.isEmpty()) "OFF" else "ON",
                valueColor = if (state.activeTrackers.isEmpty()) MutedText else AccentGreen,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "활성",
                value = state.activeTrackers.size.toString(),
                valueColor = Color.White,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "지원",
                value = state.supportedTrackers.size.toString(),
                valueColor = Color.White,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = if (state.connected) state.status else "센서 SDK 연결 필요",
            modifier = Modifier.fillMaxWidth(),
            color = MutedText,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AutoTransferPanel(state: SensorRuntimeState) {
    val transferState = when {
        state.samplesSent > 0 -> "전송됨"
        state.activeTrackers.isNotEmpty() -> "자동 대기"
        else -> "대기"
    }

    StatusPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("앱 자동 전송", fontWeight = FontWeight.SemiBold)
                Text("Wear Data Layer", color = MutedText, fontSize = 11.sp)
            }
            StatusPill(
                text = transferState,
                color = if (state.samplesSent > 0) AccentGreen else AccentBlue,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MetricTile(
                label = "전송",
                value = state.samplesSent.toString(),
                valueColor = AccentGreen,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "최근",
                value = shortTrackerName(state.latestTracker),
                valueColor = Color.White,
                modifier = Modifier.weight(2f),
            )
        }
    }
}

@Composable
private fun DataSummaryPanel(state: SensorRuntimeState) {
    StatusPanel {
        SectionLabel("수집 데이터")
        signalItems(state).chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowItems.forEach { item ->
                    SignalTile(item = item, modifier = Modifier.weight(1f))
                }
                if (rowItems.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
        if (state.latestValues != "-") {
            Text(
                text = state.latestValues,
                modifier = Modifier.fillMaxWidth(),
                color = MutedText,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StatusPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PanelBackground)
            .border(1.dp, PanelBorder, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TileBackground)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = MutedText, fontSize = 10.sp, maxLines = 1)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun SignalTile(item: SignalUiItem, modifier: Modifier = Modifier) {
    val color = when {
        item.active -> AccentGreen
        item.supported -> AccentBlue
        else -> MutedText
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TileBackground)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(item.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(item.status, color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(item.brief, color = MutedText, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

private data class SignalUiItem(
    val label: String,
    val brief: String,
    val supported: Boolean,
    val active: Boolean,
) {
    val status: String
        get() = when {
            active -> "ON"
            supported -> "OK"
            else -> "--"
        }
}

private fun signalItems(state: SensorRuntimeState): List<SignalUiItem> =
    listOf(
        SignalUiItem(
            label = "HR",
            brief = "심박/IBI",
            supported = state.supports(HealthTrackerType.HEART_RATE_CONTINUOUS, HealthTrackerType.HEART_RATE),
            active = state.isActive(HealthTrackerType.HEART_RATE_CONTINUOUS, HealthTrackerType.HEART_RATE),
        ),
        SignalUiItem(
            label = "PPG",
            brief = "Green/IR/Red",
            supported = state.supports(HealthTrackerType.PPG_CONTINUOUS, HealthTrackerType.PPG_ON_DEMAND),
            active = state.isActive(HealthTrackerType.PPG_CONTINUOUS, HealthTrackerType.PPG_ON_DEMAND),
        ),
        SignalUiItem(
            label = "ACC",
            brief = "x/y/z",
            supported = state.supports(HealthTrackerType.ACCELEROMETER_CONTINUOUS),
            active = state.isActive(HealthTrackerType.ACCELEROMETER_CONTINUOUS),
        ),
        SignalUiItem(
            label = "ECG",
            brief = "500Hz",
            supported = state.supports(HealthTrackerType.ECG_ON_DEMAND),
            active = state.isActive(HealthTrackerType.ECG_ON_DEMAND),
        ),
        SignalUiItem(
            label = "SpO2",
            brief = "산소포화도",
            supported = state.supports(HealthTrackerType.SPO2_ON_DEMAND),
            active = state.isActive(HealthTrackerType.SPO2_ON_DEMAND),
        ),
        SignalUiItem(
            label = "TEMP",
            brief = "피부온도",
            supported = state.supports(
                HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS,
                HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND,
            ),
            active = state.isActive(
                HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS,
                HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND,
            ),
        ),
        SignalUiItem(
            label = "EDA",
            brief = "전기피부반응",
            supported = state.supports(HealthTrackerType.EDA_CONTINUOUS),
            active = state.isActive(HealthTrackerType.EDA_CONTINUOUS),
        ),
        SignalUiItem(
            label = "BIA",
            brief = "체성분",
            supported = state.supports(HealthTrackerType.BIA_ON_DEMAND, HealthTrackerType.MF_BIA_ON_DEMAND),
            active = state.isActive(HealthTrackerType.BIA_ON_DEMAND, HealthTrackerType.MF_BIA_ON_DEMAND),
        ),
    )

private fun SensorRuntimeState.supports(vararg trackers: HealthTrackerType): Boolean =
    trackers.any { supportedTrackers.contains(it) }

private fun SensorRuntimeState.isActive(vararg trackers: HealthTrackerType): Boolean =
    trackers.any { activeTrackers.contains(it) }

private fun shortTrackerName(name: String): String =
    name
        .replace("_CONTINUOUS", "")
        .replace("_ON_DEMAND", "")
        .takeIf { it != "-" }
        ?: "-"

private fun onDemandLabel(type: HealthTrackerType): String =
    when (type) {
        HealthTrackerType.ECG_ON_DEMAND -> "ECG 측정"
        HealthTrackerType.PPG_ON_DEMAND -> "PPG 측정"
        HealthTrackerType.SPO2_ON_DEMAND -> "SpO2 측정"
        HealthTrackerType.BIA_ON_DEMAND -> "BIA 측정"
        HealthTrackerType.MF_BIA_ON_DEMAND -> "MF-BIA 측정"
        HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND -> "피부온도 측정"
        HealthTrackerType.SWEAT_LOSS -> "발한량 측정"
        else -> type.name
    }

private val PanelBackground = Color(0xFF111318)
private val TileBackground = Color(0xFF1B1F27)
private val PanelBorder = Color(0xFF2A303B)
private val AccentGreen = Color(0xFF62E6A6)
private val AccentBlue = Color(0xFF8AB7FF)
private val MutedText = Color(0xFF9AA3B2)

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DefaultPreview() {
    WearSensorApp(
        state = SensorRuntimeState(
            connected = true,
            supportedTrackers = SamsungSensorController.onDemandTrackers.toSet(),
            status = "Preview",
        ),
        onConnect = {},
        onStartContinuous = {},
        onStop = {},
        onStartOnDemand = {},
    )
}
