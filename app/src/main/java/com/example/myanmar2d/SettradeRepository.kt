package com.example.myanmar2d

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Fetches the live SET Index value and trading Value (มูลค่า, in million THB)
 * from settrade.com's public market-summary page, then derives the 2D number
 * with the same [calculate2D] formula used everywhere else in the app.
 *
 * settrade.com's overview table lists every index (SET, SET50, SET100, mai,
 * ...) as one row each, with a link like:
 *   https://www.settrade.com/th/equities/market-data/overview?category=Index&index=SET
 * We find that exact row (matching "index=SET" and nothing after it, so we
 * don't accidentally match "SET50"), then read the numbers out of that row's
 * text rather than relying on brittle column positions, since column count
 * can shift slightly between page updates.
 *
 * Row text looks like:
 *   "SET 1,595.58 +18.66 (+1.18%) 1,584.66 1,579.36 1,599.10 8,795,511 63,421.60"
 *    ^name  ^ล่าสุด (last = SET Index)                                  ^มูลค่า (last column with a number)
 *
 * So: first number in the row = SET Index value, last number in the row =
 * trading Value (ล้านบาท). Everything between (change/open/intraday/volume)
 * is ignored.
 */
object SettradeRepository {

    private const val OVERVIEW_URL = "https://www.settrade.com/th/equities/market-summary/overview"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class LiveMarketData(val set: Double, val value: Double) {
        val twoD: String get() = calculate2D(set, value)
    }

    /**
     * Returns the live SET/Value pair, or null if the fetch/parse failed
     * (no internet, market page layout changed, market closed with no table
     * rendered yet, etc). Callers should fall back to [SampleData] on null.
     */
    suspend fun fetchLiveSetIndex(): LiveMarketData? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(OVERVIEW_URL)
                .header("User-Agent", "Mozilla/5.0 (Android) Myanmar2DApp")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val html = response.body?.string() ?: return@withContext null
                parseSetRow(html)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSetRow(html: String): LiveMarketData? {
        val doc = Jsoup.parse(html)

        // Find the <a> link that points at the SET index page specifically
        // (not SET50 / SET100 / SETHD / ...). The href ends in exactly
        // "index=SET" for the plain SET row.
        val setLink = doc.select("a[href*=index=SET]")
            .firstOrNull { it.attr("href").trimEnd('/').endsWith("index=SET") }
            ?: return null

        // Walk up to the row (<tr>) containing that link.
        val row = setLink.parents().firstOrNull { it.tagName() == "tr" } ?: return null

        val rowText = row.text()
        val numberRegex = Regex("""-?[0-9][0-9,]*\.[0-9]+|-?[0-9][0-9,]+""")
        val numbers = numberRegex.findAll(rowText)
            .map { it.value.replace(",", "") }
            .mapNotNull { it.toDoubleOrNull() }
            .toList()

        if (numbers.size < 2) return null

        val setIndex = numbers.first()          // ล่าสุด (last) = SET Index
        val tradingValue = numbers.last()        // มูลค่า (ล้านบาท) = trading Value

        return LiveMarketData(set = setIndex, value = tradingValue)
    }
}
