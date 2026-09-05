package com.example.myanmar2d

/**
 * Sample / placeholder data, mirroring the numbers shown in the reference
 * screenshots. Swap this object out for a real repository (API call, local
 * DB, etc.) once you have a live data source to plug in.
 */
object SampleData {

    val today: List<TwoDResult> = listOf(
        TwoDResult(time = "12:01 PM", set = 1588.82, value = 35776.44),
        TwoDResult(time = "4:30 PM", set = 1595.58, value = 63421.60)
    )

    val history: List<TwoDResult> = listOf(
        TwoDResult(date = "2026-09-03", time = "12:01 PM", set = 1580.67, value = 38571.69),
        TwoDResult(date = "2026-09-03", time = "4:30 PM", set = 1576.92, value = 64483.60)
    )

    val threeDHistory: List<ThreeDResult> = listOf(
        ThreeDResult(date = "2026-09-01", threeD = "212"),
        ThreeDResult(date = "2026-08-16", threeD = "615"),
        ThreeDResult(date = "2026-08-01", threeD = "479"),
        ThreeDResult(date = "2026-07-16", threeD = "214"),
        ThreeDResult(date = "2026-07-01", threeD = "495"),
        ThreeDResult(date = "2026-06-16", threeD = "184")
    )
}
