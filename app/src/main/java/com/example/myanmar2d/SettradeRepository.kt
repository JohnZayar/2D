package com.example.myanmar2d

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object SettradeRepository {

    private const val OVERVIEW_URL = "https://www.settrade.com/th/equities/market-summary/overview"

    data class LiveMarketData(val set: Double, val value: Double) {
        val twoD: String get() = calculate2D(set, value)
    }

    sealed interface FetchResult {
        data class Success(val data: LiveMarketData) : FetchResult
        data class Failure(val reason: String) : FetchResult
    }

    suspend fun fetchLiveSetIndex(): FetchResult = withContext(Dispatchers.IO) {
        try {
            val (code, html) = fetchHtml(OVERVIEW_URL)
            if (code !in 200..299) {
                return@withContext FetchResult.Failure("HTTP $code from settrade.com")
            }
            if (html.isNullOrBlank()) {
                return@withContext FetchResult.Failure("Empty response body")
            }
            val data = parseSetRow(html)
                ?: return@withContext FetchResult.Failure(
                    "Fetched page (${html.length} chars) but couldn't find SET row"
                )
            FetchResult.Success(data)
        } catch (e: Exception) {
            FetchResult.Failure("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun fetchHtml(urlString: String): Pair<Int, String?> {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            connection.setRequestProperty("Accept-Language", "th-TH,th;q=0.9,en;q=0.8")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }
            code to body
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSetRow(html: String): LiveMarketData? {
        val candidates = listOf(
            Regex("""index=SET["'&]"""),
            Regex("""index=SET(?=["'&\s])"""),
            Regex("""[?&]index=SET\b""")
        )
        val match = candidates.firstNotNullOfOrNull { it.find(html) } ?: return null

        var window = html.substring(
            match.range.first,
            (match.range.first + 4000).coerceAtMost(html.length)
        )

        val rowEnd = window.indexOf("</tr>")
        if (rowEnd != -1) window = window.substring(0, rowEnd)

        val plainText = window.replace(Regex("<[^>]*>"), " ")

        val numberRegex = Regex("""-?[0-9][0-9,]*\.[0-9]+""")
        val numbers = numberRegex.findAll(plainText)
            .map { it.value.replace(",", "") }
            .mapNotNull { it.toDoubleOrNull() }
            .toList()

        if (numbers.size < 2) return null

        val setIndex = numbers.first()
        val tradingValue = numbers.last()

        return LiveMarketData(set = setIndex, value = tradingValue)
    }
}
