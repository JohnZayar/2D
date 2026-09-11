package com.example.myanmar2d

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val YellowTop = Color(0xFFFFE600)
private val RedCard = Color(0xFFF44336)
private val GreenPill = Color(0xFF4CAF50)
private val GoldGreen = Color(0xFF43A047)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmScheduler.scheduleAll(this)
        setContent {
            MaterialTheme {
                AppRoot()
            }
        }
    }
}

private enum class Tab { HOME, RESULTS_2D, RESULTS_3D }

@Composable
private fun AppRoot() {
    var tab by remember { mutableStateOf(Tab.HOME) }

    Scaffold(
        topBar = { TopBar(tab, onTabSelected = { tab = it }) }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.HOME -> HomeScreen()
                Tab.RESULTS_2D -> ResultsList2D()
                Tab.RESULTS_3D -> ResultsList3D()
            }
        }
    }
}

@Composable
private fun TopBar(current: Tab, onTabSelected: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(YellowTop).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Myanmar 2D", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text("v1.0", fontSize = 12.sp, color = Color.DarkGray)
            }
            Row {
                TextButton(onClick = { onTabSelected(Tab.RESULTS_2D) }) { Text("2D", fontWeight = FontWeight.Bold) }
                TextButton(onClick = { onTabSelected(Tab.RESULTS_3D) }) { Text("3D", fontWeight = FontWeight.Bold, color = Color(0xFF1565C0)) }
                TextButton(onClick = { onTabSelected(Tab.HOME) }) { Text("Home") }
            }
        }
    }
}

private data class CapturedSlot(
    val set: Double,
    val value: Double,
    val date: String,
    val capturedAt: String
) {
    val twoD: String get() = calculate2D(set, value)
}

private data class LivePreview(
    val set: Double,
    val value: Double,
    val date: String,
    val time: String
) {
    val twoD: String get() = calculate2D(set, value)
}

private const val SLOT_1_H = 12
private const val SLOT_1_M = 1
private const val SLOT_1_S = 4
private const val SLOT_2_H = 16
private const val SLOT_2_M = 30
private const val SLOT_2_S = 4
private const val CAPTURE_GRACE_SECONDS = 1800

private fun secondsSinceMidnight(h: Int, m: Int, s: Int) = h * 3600 + m * 60 + s

@Composable
private fun HomeScreen() {
    val context = LocalContext.current

    var slot1 by remember { mutableStateOf<CapturedSlot?>(null) }
    var slot2 by remember { mutableStateOf<CapturedSlot?>(null) }
    var lastResetDate by remember { mutableStateOf("") }
    var livePreview by remember { mutableStateOf<LivePreview?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }

    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        val today = dateFmt.format(Calendar.getInstance().time)
        HistoryStore.getSlot(context, today, HistoryStore.SLOT_1201)?.let {
            slot1 = CapturedSlot(it.set, it.value, today, it.capturedAt)
        }
        HistoryStore.getSlot(context, today, HistoryStore.SLOT_1630)?.let {
            slot2 = CapturedSlot(it.set, it.value, today, it.capturedAt)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            when (val result = SettradeRepository.fetchLiveSetIndex()) {
                is SettradeRepository.FetchResult.Success -> {
                    val now = Calendar.getInstance()
                    livePreview = LivePreview(
                        result.data.set, result.data.value,
                        dateFmt.format(now.time), timeFmt.format(now.time)
                    )
                    lastError = null
                }
                is SettradeRepository.FetchResult.Failure -> lastError = result.reason
            }
            delay(4000)
        }
    }

    LaunchedEffect(Unit) {
        val slot1Target = secondsSinceMidnight(SLOT_1_H, SLOT_1_M, SLOT_1_S)
        val slot2Target = secondsSinceMidnight(SLOT_2_H, SLOT_2_M, SLOT_2_S)

        while (true) {
            val now = Calendar.getInstance()
            val today = dateFmt.format(now.time)

            if (today != lastResetDate) {
                lastResetDate = today
                if (HistoryStore.getSlot(context, today, HistoryStore.SLOT_1201) == null) slot1 = null
                if (HistoryStore.getSlot(context, today, HistoryStore.SLOT_1630) == null) slot2 = null
            }

            val nowSeconds = secondsSinceMidnight(
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND)
            )

            if (slot1 == null && nowSeconds in slot1Target..(slot1Target + CAPTURE_GRACE_SECONDS)) {
                when (val result = SettradeRepository.fetchLiveSetIndex()) {
                    is SettradeRepository.FetchResult.Success -> {
                        val capturedAt = timeFmt.format(now.time)
                        slot1 = CapturedSlot(result.data.set, result.data.value, today, capturedAt)
                        HistoryStore.recordSlot(context, today, HistoryStore.SLOT_1201, result.data.set, result.data.value, capturedAt)
                    }
                    is SettradeRepository.FetchResult.Failure -> lastError = result.reason
                }
            }
            if (slot2 == null && nowSeconds in slot2Target..(slot2Target + CAPTURE_GRACE_SECONDS)) {
                when (val result = SettradeRepository.fetchLiveSetIndex()) {
                    is SettradeRepository.FetchResult.Success -> {
                        val capturedAt = timeFmt.format(now.time)
                        slot2 = CapturedSlot(result.data.set, result.data.value, today, capturedAt)
                        HistoryStore.recordSlot(context, today, HistoryStore.SLOT_1630, result.data.set, result.data.value, capturedAt)
                    }
                    is SettradeRepository.FetchResult.Failure -> lastError = result.reason
                }
            }

            delay(1000)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        if (livePreview != null) {
            Text(
                text = livePreview!!.twoD,
                fontSize = 110.sp,
                fontWeight = FontWeight.Bold,
                color = GoldGreen
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Live \u2022 ${livePreview!!.date} ${livePreview!!.time}",
                fontSize = 14.sp,
                color = Color.DarkGray
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("SET: %.2f".format(livePreview!!.set), fontSize = 15.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                Text("Value: %,.2f".format(livePreview!!.value), fontSize = 15.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        } else {
            CircularProgressIndicator(color = GoldGreen)
            Spacer(Modifier.height(8.dp))
            Text("Fetching live SET Index...", fontSize = 14.sp, color = Color.DarkGray)
        }

        lastError?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                "Last fetch issue: $it",
                fontSize = 11.sp,
                color = Color(0xFFD32F2F),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        SlotCard(label = "12:01 PM", slot = slot1, live = livePreview)
        Spacer(Modifier.height(16.dp))
        SlotCard(label = "4:30 PM", slot = slot2, live = livePreview)
    }
}

@Composable
private fun SlotCard(label: String, slot: CapturedSlot?, live: LivePreview?) {
    Surface(shape = RoundedCornerShape(16.dp), color = RedCard, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(4.dp))
            if (slot != null) {
                Text(
                    "confirmed ${slot.date} ${slot.capturedAt}",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            } else if (live != null) {
                Text(
                    "live \u2022 not yet confirmed",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val setText = slot?.let { "%.2f".format(it.set) } ?: live?.let { "%.2f".format(it.set) } ?: "--"
                val valueText = slot?.let { "%,.2f".format(it.value) } ?: live?.let { "%,.2f".format(it.value) } ?: "--"
                val twoDText = slot?.twoD ?: live?.twoD ?: "--"
                LabeledValue("SET", setText)
                LabeledValue("Value", valueText)
                LabeledValue("2D", twoDText, valueColor = Color(0xFFFFEB3B), big = true)
            }
        }
    }
}

@Composable
private fun ResultsList2D() {
    val context = LocalContext.current
    val dates = remember { HistoryStore.getAllDates(context) }

    if (dates.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(40.dp))
            Text(
                "No confirmed results yet. Once the app captures 12:01 PM or 4:30 PM, they'll show up here.",
                fontSize = 14.sp,
                color = Color.DarkGray
            )
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(dates) { date ->
            Column {
                DatePill(text = date)
                Spacer(Modifier.height(12.dp))
                HistoryStore.getSlot(context, date, HistoryStore.SLOT_1201)?.let { slot ->
                    ResultCard(TwoDResult(time = "12:01 PM", set = slot.set, value = slot.value, date = date))
                    Spacer(Modifier.height(12.dp))
                }
                HistoryStore.getSlot(context, date, HistoryStore.SLOT_1630)?.let { slot ->
                    ResultCard(TwoDResult(time = "4:30 PM", set = slot.set, value = slot.value, date = date))
                }
            }
        }
    }
}

@Composable
private fun ResultsList3D() {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(SampleData.threeDHistory) { result ->
            ThreeDCard(result)
        }
    }
}

@Composable
private fun DatePill(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(50), color = GreenPill) {
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun ResultCard(result: TwoDResult) {
    Surface(shape = RoundedCornerShape(16.dp), color = RedCard, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(result.time, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue("SET", "%.2f".format(result.set))
                LabeledValue("Value", "%,.2f".format(result.value))
                LabeledValue("2D", result.twoD, valueColor = Color(0xFFFFEB3B), big = true)
            }
        }
    }
}

@Composable
private fun ThreeDCard(result: ThreeDResult) {
    Surface(shape = RoundedCornerShape(16.dp), color = GreenPill, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LabeledValue("Date", result.date, valueColor = Color.White)
            LabeledValue("3D", result.threeD, valueColor = Color(0xFFFFEB3B), big = true)
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String, valueColor: Color = Color.White, big: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = if (big) 30.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
