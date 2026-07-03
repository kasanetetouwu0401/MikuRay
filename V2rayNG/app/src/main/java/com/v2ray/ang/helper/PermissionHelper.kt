package com.v2ray.ang.helper

import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.v2ray.ang.R
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.snackbarDefault

/**
 * Helper for requesting permissions.
 */
class PermissionHelper(private val activity: AppCompatActivity) {
    private var permissionCallback: ((Boolean) -> Unit)? = null

    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val anyGranted = results.values.any { it }
            permissionCallback?.invoke(anyGranted)
            permissionCallback = null
        }

    /**
     * Check the permission and request it if not granted.
     *
     * @param permissionType the type of permission
     * @param onGranted called when permission is granted (called immediately if already granted)
     */
    fun request(permissionType: PermissionType, onGranted: () -> Unit) {
        val permissions = permissionType.getPermissions()
        val alreadyGranted = permissions.any {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
        if (alreadyGranted) {
            onGranted()
        } else {
            permissionCallback = { isGranted ->
                if (isGranted) {
                    onGranted()
                } else {
                    val message = "${activity.getString(R.string.toast_permission_denied)}  ${permissionType.getLabel()}"
                    activity.snackbarDefault(message, title = activity.getString(R.string.title_alerter_info))
                }
            }
            permissionLauncher.launch(permissions)
        }
    }
}