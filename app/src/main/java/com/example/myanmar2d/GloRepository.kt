package com.example.myanmar2d

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Fetches the official Thai Government Lottery (GLO) result via glo.or.th's public
 * checking API, and freezes it locally until the next draw (GLO draws on the 1st and
 * 16th of every month). Uses the phone's own local clock for period calculation -
 * no timezone conversion.
 *
 *   POST https://www.glo.or.th/api/checking/getLotteryResult
 *   Body: {"date":"DD","month":"MM","year":"YYYY"}
 *
 * The raw response is logged (Log.d, tag "GloRepository") on every fetch attempt so the
 * exact JSON shape can be inspected in Logcat and the parsing adjusted if needed.
 */
object GloRepository {

    private const val TAG = "GloRepository"
    private const val PREFS_NAME = "glo_cache"
    private const val KEY_DATE = "cached_draw_date"          // "yyyy-MM-dd" of the draw this result belongs to
    private const val KEY_FIRST = "cached_first_prize"
    private const val KEY_LAST2 = "cached_last2"
    private const val KEY_DISPLAY_DATE = "cached_display_date"

    private const val ENDPOINT = "https://www.glo.or.th/api/checking/getLotteryResult"
    private val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    sealed class FetchResult {
        data class Success(val data: GloDrawResult) : FetchResult()
        data class Failure(val reason: String) : FetchResult()
    }

    data class GloDrawResult(
        val drawDateKey: String,   // "yyyy-MM-dd" - which draw (1st/16th) this result belongs to
        val displayDate: String,
        val firstPrize: String,    // full 6-digit first-prize number
        val last3: String,         // last 3 digits of the first prize
        val last2: String?
    )

    /** The most recent draw date (1st or 16th) on/before today, per the phone's own clock. */
    private fun currentPeriodCalendar(): Calendar {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, if (day < 16) 1 else 16)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    /** Whatever result is currently saved on-device (could be this period's, or an older one). */
    fun readCached(context: Context): GloDrawResult? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val date = prefs.getString(KEY_DATE, null) ?: return null
        val first = prefs.getString(KEY_FIRST, null) ?: return null
        return GloDrawResult(
            drawDateKey = date,
            displayDate = prefs.getString(KEY_DISPLAY_DATE, date) ?: date,
            firstPrize = first,
            last3 = first.takeLast(3),
            last2 = prefs.getString(KEY_LAST2, null)
        )
    }

    private fun saveCache(context: Context, data: GloDrawResult) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_DATE, data.drawDateKey)
            .putString(KEY_FIRST, data.firstPrize)
            .putString(KEY_LAST2, data.last2)
            .putString(KEY_DISPLAY_DATE, data.displayDate)
            .apply()
    }

    /**
     * Returns the number that should be shown right now:
     *  - if this period's (current 1st/16th) result is already cached, returns it with no
     *    network call
     *  - otherwise tries to fetch it; on success, freezes (caches) it as the new result
     *  - on failure (draw not announced yet, or network issue), keeps returning the last
     *    cached (older) result so the screen never goes blank - just keep calling this
     *    periodically and it will pick up the new result automatically once available
     */
    suspend fun fetchAndFreeze(context: Context): FetchResult = withContext(Dispatchers.IO) {
        val periodCal = currentPeriodCalendar()
        val periodKey = keyFmt.format(periodCal.time)

        val cached = readCached(context)
        if (cached?.drawDateKey == periodKey) {
            return@withContext FetchResult.Success(cached)
        }

        val day = "%02d".format(periodCal.get(Calendar.DAY_OF_MONTH))
        val month = "%02d".format(periodCal.get(Calendar.MONTH) + 1)
        val year = "%04d".format(periodCal.get(Calendar.YEAR))

        try {
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val body = JSONObject().apply {
                put("date", day); put("month", month); put("year", year)
            }.toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            Log.d(TAG, "period=$periodKey httpCode=$code rawResponse=$raw")

            if (code !in 200..299) {
                return@withContext cached?.let { FetchResult.Success(it) }
                    ?: FetchResult.Failure("HTTP $code (no cached fallback yet, see Logcat tag $TAG)")
            }

            val root = JSONObject(raw)
            val first = extractPrizeNumber(root, "first")
            if (first == null) {
                Log.d(TAG, "Could not find 'first' field - draw for $periodKey may not be announced yet, or the field name differs from what's expected. Check the rawResponse line above.")
                return@withContext cached?.let { FetchResult.Success(it) }
                    ?: FetchResult.Failure("First-prize field not found (see Logcat tag $TAG)")
            }

            val last2 = extractPrizeNumber(root, "last2")
            val displayDate =
                root.optJSONObject("response")?.optString("date")?.takeIf { it.isNotBlank() }
                    ?: root.optString("date").takeIf { it.isNotBlank() }
                    ?: "$day/$month/$year"

            val fresh = GloDrawResult(
                drawDateKey = periodKey,
                displayDate = displayDate,
                firstPrize = first,
                last3 = first.takeLast(3),
                last2 = last2
            )
            saveCache(context, fresh)
            FetchResult.Success(fresh)
        } catch (e: Exception) {
            Log.d(TAG, "Exception fetching GLO result: ${e.message}")
            cached?.let { FetchResult.Success(it) }
                ?: FetchResult.Failure(e.message ?: "Unknown error")
        }
    }

    /**
     * Tries a few common shapes for a prize field (e.g. key = "first" or "last2"):
     *   root.response.first            -> "203752"
     *   root.response.first[0]         -> ["203752"]
     *   root.response.first.number[0]  -> {"number": ["203752"]}
     *   same shapes without the "response" wrapper
     */
    private fun extractPrizeNumber(root: JSONObject, key: String): String? {
        val containers = listOfNotNull(root.optJSONObject("response"), root)
        for (container in containers) {
            if (!container.has(key)) continue
            when (val value = container.get(key)) {
                is String -> if (value.isNotBlank()) return value
                is JSONArray -> if (value.length() > 0) return value.optString(0).takeIf { it.isNotBlank() }
                is JSONObject -> {
                    value.optJSONArray("number")?.let { arr ->
                        if (arr.length() > 0) return arr.optString(0)
                    }
                    val numStr = value.optString("number", "")
                    if (numStr.isNotBlank()) return numStr
                }
                else -> {}
            }
        }
        return null
    }
}
