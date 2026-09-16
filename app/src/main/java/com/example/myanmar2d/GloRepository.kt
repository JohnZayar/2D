package com.example.myanmar2d

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

/**
 * Fetches the official Thai Government Lottery (GLO) result via glo.or.th's public
 * checking API. GLO draws happen on the 1st and 16th of every month.
 *
 * Reference (official, public):
 *   POST https://www.glo.or.th/api/checking/getLotteryResult
 *   Body: {"date":"DD","month":"MM","year":"YYYY"}   (year is Gregorian, e.g. 2024)
 *
 * The exact JSON response shape isn't fully published, so [extractPrizeNumber] tries a
 * few common shapes defensively. If it ever returns null for a field that should exist,
 * capture the raw response (see the Failure reason / logcat) and the parsing can be
 * adjusted to match the real shape.
 */
object GloRepository {

    sealed class FetchResult {
        data class Success(val data: GloDrawResult) : FetchResult()
        data class Failure(val reason: String) : FetchResult()
    }

    data class GloDrawResult(
        val displayDate: String,
        val firstPrize: String,   // full 6-digit first-prize number, e.g. "203752"
        val last3: String,        // last 3 digits of the first prize (commonly used as "3D")
        val last2: String?        // 2-digit prize, if the API exposes it directly
    )

    private const val ENDPOINT = "https://www.glo.or.th/api/checking/getLotteryResult"

    /** Most recent GLO draw date (1st or 16th) on/before today. */
    private fun latestDrawDate(): Triple<String, String, String> {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, if (day < 16) 1 else 16)
        val d = "%02d".format(cal.get(Calendar.DAY_OF_MONTH))
        val m = "%02d".format(cal.get(Calendar.MONTH) + 1)
        val y = "%04d".format(cal.get(Calendar.YEAR))
        return Triple(d, m, y)
    }

    suspend fun fetchLatest(): FetchResult = withContext(Dispatchers.IO) {
        val (day, month, year) = latestDrawDate()
        try {
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val body = JSONObject().apply {
                put("date", day)
                put("month", month)
                put("year", year)
            }.toString()

            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val streamToRead = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = streamToRead?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                return@withContext FetchResult.Failure("HTTP $code: ${raw.take(200)}")
            }

            val root = JSONObject(raw)

            val first = extractPrizeNumber(root, "first")
                ?: return@withContext FetchResult.Failure(
                    "First-prize field not found in response: ${raw.take(300)}"
                )

            val last2 = extractPrizeNumber(root, "last2")
            val displayDate =
                root.optJSONObject("response")?.optString("date")?.takeIf { it.isNotBlank() }
                    ?: root.optString("date").takeIf { it.isNotBlank() }
                    ?: "$day/$month/$year"

            FetchResult.Success(
                GloDrawResult(
                    displayDate = displayDate,
                    firstPrize = first,
                    last3 = first.takeLast(3),
                    last2 = last2
                )
            )
        } catch (e: Exception) {
            FetchResult.Failure(e.message ?: "Unknown error")
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
