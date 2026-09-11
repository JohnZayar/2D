package com.example.myanmar2d

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CaptureAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val slotKey = intent.getStringExtra(AlarmScheduler.EXTRA_SLOT_KEY) ?: return
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val today = dateFmt.format(Calendar.getInstance().time)

                if (HistoryStore.getSlot(appContext, today, slotKey) == null) {
                    when (val result = SettradeRepository.fetchLiveSetIndex()) {
                        is SettradeRepository.FetchResult.Success -> {
                            val capturedAt = timeFmt.format(Calendar.getInstance().time)
                            HistoryStore.recordSlot(
                                appContext, today, slotKey,
                                result.data.set, result.data.value, capturedAt
                            )
                        }
                        is SettradeRepository.FetchResult.Failure -> {}
                    }
                }

                AlarmScheduler.scheduleAll(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
