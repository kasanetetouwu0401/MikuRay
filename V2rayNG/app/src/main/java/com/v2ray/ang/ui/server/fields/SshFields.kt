package com.v2ray.ang.ui.server.fields

import android.app.Activity
import android.view.View
import android.widget.AutoCompleteTextView
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.ESshAuthType
import com.v2ray.ang.enums.ESshMode
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.v2ray.ang.util.Utils

class SshFields(private val activity: Activity) {

    private val sshModes: Array<out String> by lazy { activity.resources.getStringArray(R.array.ssh_modes) }
    private val sshAuthTypes: Array<out String> by lazy { activity.resources.getStringArray(R.array.ssh_auth_types) }

    private val spMode: AutoCompleteTextView by lazy { activity.findViewById(R.id.sp_ssh_mode) }
    private val spAuthType: AutoCompleteTextView by lazy { activity.findViewById(R.id.sp_ssh_auth_type) }

    private val layoutPassword: TextInputLayout by lazy { activity.findViewById(R.id.layout_ssh_password) }
    private val layoutPrivateKey: TextInputLayout by lazy { activity.findViewById(R.id.layout_ssh_private_key) }
    private val layoutPrivateKeyPassword: TextInputLayout by lazy { activity.findViewById(R.id.layout_ssh_private_key_password) }
    private val layoutCertificate: TextInputLayout by lazy { activity.findViewById(R.id.layout_ssh_certificate) }
    private val layoutSni: TextInputLayout by lazy { activity.findViewById(R.id.layout_ssh_sni) }
    private val layoutPayload: TextInputLayout by lazy { activity.findViewById(R.id.layout_ssh_payload) }
    private val layoutProxyHost: TextInputLayout by lazy { activity.findViewById(R.id.layout_ssh_proxy_host) }
    private val layoutProxyPort: TextInputLayout by lazy { activity.findViewById(R.id.layout_ssh_proxy_port) }
    private val layoutProxyUsername: TextInputLayout by lazy { activity.findViewById(R.id.layout_ssh_proxy_username) }
    private val layoutProxyPassword: TextInputLayout by lazy { activity.findViewById(R.id.layout_ssh_proxy_password) }

    private val etPassword: TextInputEditText by lazy { activity.findViewById(R.id.et_id) }
    private val etPrivateKey: TextInputEditText by lazy { activity.findViewById(R.id.et_ssh_private_key) }
    private val etPrivateKeyPassword: TextInputEditText by lazy { activity.findViewById(R.id.et_ssh_private_key_password) }
    private val etCertificate: TextInputEditText by lazy { activity.findViewById(R.id.et_ssh_certificate) }
    private val etSni: TextInputEditText by lazy { activity.findViewById(R.id.et_ssh_sni) }
    private val etPayload: TextInputEditText by lazy { activity.findViewById(R.id.et_ssh_payload) }
    private val etProxyHost: TextInputEditText by lazy { activity.findViewById(R.id.et_ssh_proxy_host) }
    private val etProxyPort: TextInputEditText by lazy { activity.findViewById(R.id.et_ssh_proxy_port) }
    private val etProxyUsername: TextInputEditText by lazy { activity.findViewById(R.id.et_ssh_proxy_username) }
    private val etProxyPassword: TextInputEditText by lazy { activity.findViewById(R.id.et_ssh_proxy_password) }

    var mode: ESshMode = ESshMode.SSH
        private set
    var authType: ESshAuthType = ESshAuthType.PASSWORD
        private set

    fun setup() {
        spMode.setOnItemClickListener { _, _, position, _ ->
            mode = ESshMode.entries.getOrElse(position) { ESshMode.SSH }
            updateVisibility()
        }
        spAuthType.setOnItemClickListener { _, _, position, _ ->
            authType = ESshAuthType.entries.getOrElse(position) { ESshAuthType.PASSWORD }
            updateVisibility()
        }
    }

    fun bind(config: ProfileItem) {
        mode = ESshMode.fromName(config.sshMode)
        authType = ESshAuthType.fromName(config.sshAuthType)
        spMode.setText(sshModes.getOrElse(mode.value) { sshModes.first() }, false)
        spAuthType.setText(sshAuthTypes.getOrElse(authType.value) { sshAuthTypes.first() }, false)

        etPassword.text = Utils.getEditable(config.password.orEmpty())
        etPrivateKey.text = Utils.getEditable(config.sshPrivateKey.orEmpty())
        etPrivateKeyPassword.text = Utils.getEditable(config.sshPrivateKeyPassword.orEmpty())
        etCertificate.text = Utils.getEditable(config.sshCertificate.orEmpty())
        etSni.text = Utils.getEditable(config.sni.orEmpty())
        etPayload.text = Utils.getEditable(config.sshPayload.orEmpty())
        etProxyHost.text = Utils.getEditable(config.sshProxyHost.orEmpty())
        etProxyPort.text = Utils.getEditable(config.sshProxyPort.orEmpty())
        etProxyUsername.text = Utils.getEditable(config.sshProxyUsername.orEmpty())
        etProxyPassword.text = Utils.getEditable(config.sshProxyPassword.orEmpty())

        updateVisibility()
    }

    fun clear() {
        mode = ESshMode.SSH
        authType = ESshAuthType.PASSWORD
        spMode.setText(sshModes.first(), false)
        spAuthType.setText(sshAuthTypes.first(), false)

        etPassword.text = null
        etPrivateKey.text = null
        etPrivateKeyPassword.text = null
        etCertificate.text = null
        etSni.text = null
        etPayload.text = null
        etProxyHost.text = null
        etProxyPort.text = Utils.getEditable("8080")
        etProxyUsername.text = null
        etProxyPassword.text = null

        updateVisibility()
    }

    fun save(config: ProfileItem) {
        config.sshMode = mode.name
        config.sshAuthType = authType.name

        config.password = if (authType == ESshAuthType.PASSWORD) etPassword.text.toString().trim() else null
        config.sshPrivateKey = if (authType != ESshAuthType.PASSWORD) etPrivateKey.text.toString().trim() else null
        config.sshPrivateKeyPassword = if (authType != ESshAuthType.PASSWORD) etPrivateKeyPassword.text.toString().trim() else null
        config.sshCertificate = if (authType == ESshAuthType.CERTIFICATE) etCertificate.text.toString().trim() else null

        config.sni = if (mode.usesSsl) etSni.text.toString().trim() else null
        config.sshPayload = if (mode.usesPayload) etPayload.text.toString().trim() else null
        config.sshProxyHost = if (mode.usesProxy) etProxyHost.text.toString().trim() else null
        config.sshProxyPort = if (mode.usesProxy) etProxyPort.text.toString().trim() else null
        config.sshProxyUsername = if (mode.usesProxy) etProxyUsername.text.toString().trim() else null
        config.sshProxyPassword = if (mode.usesProxy) etProxyPassword.text.toString().trim() else null
    }

    private fun updateVisibility() {
        layoutPassword.visibility = if (authType == ESshAuthType.PASSWORD) View.VISIBLE else View.GONE
        layoutPrivateKey.visibility = if (authType != ESshAuthType.PASSWORD) View.VISIBLE else View.GONE
        layoutPrivateKeyPassword.visibility = if (authType != ESshAuthType.PASSWORD) View.VISIBLE else View.GONE
        layoutCertificate.visibility = if (authType == ESshAuthType.CERTIFICATE) View.VISIBLE else View.GONE

        layoutSni.visibility = if (mode.usesSsl) View.VISIBLE else View.GONE
        layoutPayload.visibility = if (mode.usesPayload) View.VISIBLE else View.GONE
        val proxyVisibility = if (mode.usesProxy) View.VISIBLE else View.GONE
        layoutProxyHost.visibility = proxyVisibility
        layoutProxyPort.visibility = proxyVisibility
        layoutProxyUsername.visibility = proxyVisibility
        layoutProxyPassword.visibility = proxyVisibility
    }
}
