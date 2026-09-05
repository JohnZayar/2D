package com.example.myanmar2d

/**
 * One draw session's raw stock figures + the 2D number derived from them.
 */
data class TwoDResult(
    val time: String,      // e.g. "12:01 PM"
    val set: Double,        // SET index value, e.g. 1588.82
    val value: Double,      // trading Value, e.g. 35776.44
    val date: String = ""
) {
    /** The published 2D number for this session. */
    val twoD: String get() = calculate2D(set, value)
}

data class ThreeDResult(
    val date: String,
    val threeD: String
)

/**
 * Formula (reverse engineered from real published results):
 *   digit1 = last digit of SET's decimal part   (e.g. 1588.82 -> "82" -> 2)
 *   digit2 = last digit of Value's integer part (e.g. 35776.44 -> "35776" -> 6)
 *   2D = "digit1 digit2"
 *
 * Verified against 4 real sessions:
 *   1588.82 / 35776.44 -> 26
 *   1595.58 / 63421.60 -> 81
 *   1580.67 / 38571.69 -> 71
 *   1576.92 / 64483.60 -> 23
 */
fun calculate2D(set: Double, value: Double): String {
    val setDecimalDigits = "%.2f".format(set).substringAfter(".")
    val digit1 = setDecimalDigits.last()

    val valueIntegerDigits = "%.2f".format(value).substringBefore(".")
    val digit2 = valueIntegerDigits.last()

    return "$digit1$digit2"
}
