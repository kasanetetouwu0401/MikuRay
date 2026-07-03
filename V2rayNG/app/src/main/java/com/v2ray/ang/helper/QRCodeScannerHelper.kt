package com.v2ray.ang.helper

import androidx.activity.result.ActivityResultCaller
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.config.ScannerConfig

/**
 * Helper for scanning QR codes.
 *
 * This class encapsulates the logic for launching the QR code scanner directly
 * using the Quickie library and handling the scan result. Works with both an
 * Activity and a Fragment via [ActivityResultCaller].
 */
class QRCodeScannerHelper(private val caller: ActivityResultCaller) {
    private var scanCallback: ((String?) -> Unit)? = null

    private val scanLauncher = caller.registerForActivityResult(ScanCustomCode()) { result ->
        if (result is QRResult.QRSuccess) {
            scanCallback?.invoke(result.content.rawValue)
        } else {
            scanCallback?.invoke(null)
        }
        scanCallback = null
    }

    /**
     * Launch the QR code scanner camera.
     *
     * @param onResult Callback invoked with the scan result (null if cancelled or failed)
     */
    fun launch(onResult: (String?) -> Unit) {
        scanCallback = onResult
        scanLauncher.launch(
            ScannerConfig.build {
                setHapticSuccessFeedback(true)
                setShowTorchToggle(true)
                setShowCloseButton(true)
                setBarcodeFormats(listOf(BarcodeFormat.QR_CODE))
            }
        )
    }
}
