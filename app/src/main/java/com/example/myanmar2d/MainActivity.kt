package com.example.myanmar2d

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val YellowTop = Color(0xFFFFE600)
private val RedCard = Color(0xFFF44336)
private val GreenPill = Color(0xFF4CAF50)
private val GoldGreen = Color(0xFF43A047)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

private sealed interface LiveState {
    data object Loading : LiveState
    data class Loaded(val data: SettradeRepository.LiveMarketData, val fetchedAt: String) : LiveState
    data object Failed : LiveState
}

@Composable
private fun HomeScreen() {
    var state by remember { mutableStateOf<LiveState>(LiveState.Loading) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        state = LiveState.Loading
        val live = SettradeRepository.fetchLiveSetIndex()
        state = if (live != null) {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            LiveState.Loaded(live, time)
        } else {
            LiveState.Failed
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        when (val s = state) {
            is LiveState.Loading -> {
                CircularProgressIndicator(color = GoldGreen)
                Spacer(Modifier.height(12.dp))
                Text("Fetching live SET Index...", fontSize = 14.sp, color = Color.DarkGray)
            }
            is LiveState.Loaded -> {
                Text(
                    text = s.data.twoD,
                    fontSize = 110.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldGreen
                )
                Spacer(Modifier.height(8.dp))
                Text("Live \u2022 updated ${s.fetchedAt}", fontSize = 14.sp, color = Color.DarkGray)
                Spacer(Modifier.height(16.dp))
                LiveResultCard(s.data)
            }
            is LiveState.Failed -> {
                val fallback = SampleData.today.last()
                Text(
                    text = fallback.twoD,
                    fontSize = 110.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldGreen
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Couldn't reach settrade.com \u2014 showing sample data",
                    fontSize = 13.sp,
                    color = Color(0xFFD32F2F)
                )
                Spacer(Modifier.height(16.dp))
                SampleData.today.forEach { result ->
                    ResultCard(result)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { refreshTrigger++ }) {
            Text("Refresh")
        }
    }
}

@Composable
private fun LiveResultCard(data: SettradeRepository.LiveMarketData) {
    Surface(shape = RoundedCornerShape(16.dp), color = RedCard, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SET (live)", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue("SET", "%.2f".format(data.set))
                LabeledValue("Value", "%,.2f".format(data.value))
                LabeledValue("2D", data.twoD, valueColor = Color(0xFFFFEB3B), big = true)
            }
        }
    }
}

@Composable
private fun ResultsList2D() {
    Column(Modifier.fillMaxSize()) {
        DatePill(text = "Today")
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(SampleData.history) { result ->
                ResultCard(result)
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
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
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
