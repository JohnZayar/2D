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

    suspend fun fetchLiveSetIndex(): LiveMarketData? = withContext(Dispatchers.IO) {
        try {
            val html = fetchHtml(OVERVIEW_URL) ?: return@withContext null
            parseSetRow(html)
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchHtml(urlString: String): String? {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Myanmar2DApp")
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSetRow(html: String): LiveMarketData? {
        val linkRegex = Regex("""index=SET["'&]""")
        val match = linkRegex.find(html) ?: return null

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
