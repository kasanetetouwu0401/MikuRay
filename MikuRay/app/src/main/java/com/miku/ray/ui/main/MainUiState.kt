package com.miku.ray.ui.main

import com.miku.ray.dto.TestProgressInfo

data class MainUiState(
    val isRunning: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: String = "",
    val testProgress: TestProgressInfo? = null,
    val countryCodeProgress: TestProgressInfo? = null,
    val ipAddress: String? = null,
    val trafficSpeed: String = "",
)
