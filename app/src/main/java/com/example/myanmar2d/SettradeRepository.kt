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
                    "Fetched page (${html.length} chars) but couldn't find a plausible SET row"
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
        val withoutScripts = html
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")

        val plainText = withoutScripts
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")

        val numberRegex = Regex("""-?[0-9][0-9,]*\.[0-9]+""")
        val setTokenRegex = Regex("""\bSET\b""")

        for (match in setTokenRegex.findAll(plainText)) {
            val windowStart = match.range.last + 1
            val windowEnd = (windowStart + 300).coerceAtMost(plainText.length)
            val window = plainText.substring(windowStart, windowEnd)

            val numbers = numberRegex.findAll(window)
                .map { it.value.replace(",", "") }
                .mapNotNull { it.toDoubleOrNull() }
                .toList()

            if (numbers.size < 2) continue

            val candidateIndex = numbers.first()
            if (candidateIndex < 500 || candidateIndex > 5000) continue

            val candidateValue = numbers.last()
            return LiveMarketData(set = candidateIndex, value = candidateValue)
        }

        return null
    }
}
