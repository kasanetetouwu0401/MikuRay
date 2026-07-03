package com.v2ray.ang.ui

import android.net.Uri
import android.os.Bundle
import androidx.viewbinding.ViewBinding
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.helper.FileChooserHelper
import com.v2ray.ang.helper.PermissionHelper
import com.v2ray.ang.helper.QRCodeScannerHelper

/**
 * Fragment-side counterpart of [HelperBaseActivity], providing the same file chooser,
 * permission requesting, and QR code scanning helpers to fragments now hosted inside
 * MainActivity's `fragment_holder` (Routing setting, Sub setting, Backup/restore, ...).
 */
abstract class HelperMikuFragment<VB : ViewBinding> : MikuFragment<VB>() {
    private lateinit var fileChooser: FileChooserHelper
    private lateinit var permissionRequester: PermissionHelper
    private lateinit var qrCodeScanner: QRCodeScannerHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileChooser = FileChooserHelper(this, requireContext())
        permissionRequester = PermissionHelper(this, requireContext())
        qrCodeScanner = QRCodeScannerHelper(this)
    }

    protected fun checkAndRequestPermission(
        permissionType: PermissionType,
        onGranted: () -> Unit
    ) {
        permissionRequester.request(permissionType, onGranted)
    }

    protected fun launchFileChooser(
        mimeType: String = "*/*",
        onResult: (Uri?) -> Unit
    ) {
        fileChooser.launch(mimeType, onResult)
    }

    protected fun launchCreateDocument(
        fileName: String,
        onResult: (Uri?) -> Unit
    ) {
        fileChooser.createDocument(fileName, onResult)
    }

    protected fun launchQRCodeScanner(onResult: (String?) -> Unit) {
        checkAndRequestPermission(PermissionType.CAMERA) {
            qrCodeScanner.launch(onResult)
        }
    }
}
