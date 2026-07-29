package com.v2ray.ang.ui.server

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.EditText
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputLayout
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.applyEdgeToEdgeListInsets
import com.v2ray.ang.extension.snackbarError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.ui.server.fields.AddressPortFields
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.showDeleteConfirmDialog

class ServerSshActivity : BaseActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    private val etUsername: EditText by lazy { findViewById(R.id.et_ssh_username) }
    private val spAuthType: AutoCompleteTextView by lazy { findViewById(R.id.sp_ssh_auth_type) }

    private val tilPassword: TextInputLayout by lazy { findViewById(R.id.til_ssh_password) }
    private val etPassword: EditText by lazy { findViewById(R.id.et_ssh_password) }

    private val tilPrivateKey: TextInputLayout by lazy { findViewById(R.id.til_ssh_private_key) }
    private val etPrivateKey: EditText by lazy { findViewById(R.id.et_ssh_private_key) }

    private val tilPrivateKeyPassphrase: TextInputLayout by lazy { findViewById(R.id.til_ssh_private_key_passphrase) }
    private val etPrivateKeyPassphrase: EditText by lazy { findViewById(R.id.et_ssh_private_key_passphrase) }

    private val tilCertificate: TextInputLayout by lazy { findViewById(R.id.til_ssh_certificate) }
    private val etCertificate: EditText by lazy { findViewById(R.id.et_ssh_certificate) }

    private val etPayload: EditText by lazy { findViewById(R.id.et_ssh_payload) }
    private val etProxy: EditText by lazy { findViewById(R.id.et_ssh_proxy) }
    private val etSni: EditText by lazy { findViewById(R.id.et_ssh_sni) }

    private lateinit var addressPortFields: AddressPortFields

    private val authTypes by lazy { resources.getStringArray(R.array.ssh_auth_types) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = MmkvManager.decodeServerConfig(editGuid)

        setContentView(R.layout.activity_server_ssh)

        findViewById<androidx.core.widget.NestedScrollView>(R.id.server_scroll_content).applyEdgeToEdgeListInsets()

        addressPortFields = AddressPortFields(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = EConfigType.SSH.toString())

        spAuthType.setOnItemClickListener { _, _, _, _ -> updateAuthFieldsVisibility() }

        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
        }
        updateAuthFieldsVisibility()
    }

    private fun updateAuthFieldsVisibility() {
        when (spAuthType.text?.toString()) {
            AppConfig.SSH_AUTH_PRIVATE_KEY -> {
                tilPassword.visibility = View.GONE
                tilPrivateKey.visibility = View.VISIBLE
                tilPrivateKeyPassphrase.visibility = View.VISIBLE
                tilCertificate.visibility = View.GONE
            }

            AppConfig.SSH_AUTH_CERTIFICATE -> {
                tilPassword.visibility = View.GONE
                tilPrivateKey.visibility = View.VISIBLE
                tilPrivateKeyPassphrase.visibility = View.VISIBLE
                tilCertificate.visibility = View.VISIBLE
            }

            else -> {
                tilPassword.visibility = View.VISIBLE
                tilPrivateKey.visibility = View.GONE
                tilPrivateKeyPassphrase.visibility = View.GONE
                tilCertificate.visibility = View.GONE
            }
        }
    }

    private fun bindingServer(config: ProfileItem): Boolean {
        addressPortFields.bind(config)
        etUsername.text = Utils.getEditable(config.username.orEmpty())
        val authPos = Utils.arrayFind(authTypes, config.sshAuthType.orEmpty())
        spAuthType.setText(authTypes.getOrElse(authPos.takeIf { it >= 0 } ?: 0) { authTypes.first() }, false)
        etPassword.text = Utils.getEditable(config.password.orEmpty())
        etPrivateKey.text = Utils.getEditable(config.sshPrivateKey.orEmpty())
        etPrivateKeyPassphrase.text = Utils.getEditable(config.sshPrivateKeyPassphrase.orEmpty())
        etCertificate.text = Utils.getEditable(config.sshCertificate.orEmpty())
        etPayload.text = Utils.getEditable(config.sshPayload.orEmpty())
        etProxy.text = Utils.getEditable(config.sshProxy.orEmpty())
        etSni.text = Utils.getEditable(config.sni.orEmpty())
        return true
    }

    private fun clearServer(): Boolean {
        addressPortFields.clear()
        etUsername.text = null
        spAuthType.setText(authTypes.firstOrNull().orEmpty(), false)
        etPassword.text = null
        etPrivateKey.text = null
        etPrivateKeyPassphrase.text = null
        etCertificate.text = null
        etPayload.text = null
        etProxy.text = null
        etSni.text = null
        return true
    }

    private fun saveServer(): Boolean {
        if (TextUtils.isEmpty(addressPortFields.remarksText)) {
            snackbarError(getString(R.string.server_lab_remarks), title = getString(R.string.title_alerter_error))
            return false
        }
        if (TextUtils.isEmpty(addressPortFields.addressText)) {
            snackbarError(getString(R.string.server_lab_address), title = getString(R.string.title_alerter_error))
            return false
        }
        if (Utils.parseInt(addressPortFields.portText) <= 0) {
            snackbarError(getString(R.string.server_lab_port), title = getString(R.string.title_alerter_error))
            return false
        }
        if (TextUtils.isEmpty(etUsername.text)) {
            snackbarError(getString(R.string.server_lab_ssh_username), title = getString(R.string.title_alerter_error))
            return false
        }

        val authType = authTypes.getOrElse(Utils.arrayFind(authTypes, spAuthType.text?.toString().orEmpty()).takeIf { it >= 0 } ?: 0) {
            AppConfig.SSH_AUTH_PASSWORD
        }

        if (authType == AppConfig.SSH_AUTH_PRIVATE_KEY || authType == AppConfig.SSH_AUTH_CERTIFICATE) {
            if (TextUtils.isEmpty(etPrivateKey.text)) {
                snackbarError(getString(R.string.server_lab_ssh_private_key), title = getString(R.string.title_alerter_error))
                return false
            }
        }
        if (authType == AppConfig.SSH_AUTH_CERTIFICATE && TextUtils.isEmpty(etCertificate.text)) {
            snackbarError(getString(R.string.server_lab_ssh_certificate), title = getString(R.string.title_alerter_error))
            return false
        }

        val proxyText = etProxy.text.toString().trim()
        if (proxyText.isNotEmpty() && (!proxyText.contains(":") || Utils.parseInt(proxyText.substringAfterLast(":")) <= 0)) {
            snackbarError(getString(R.string.server_lab_ssh_proxy), title = getString(R.string.title_alerter_error))
            return false
        }

        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(EConfigType.SSH)

        addressPortFields.save(config)
        config.username = etUsername.text.toString().trim()
        config.sshAuthType = authType
        config.password = etPassword.text.toString()
        config.sshPrivateKey = etPrivateKey.text.toString()
        config.sshPrivateKeyPassphrase = etPrivateKeyPassphrase.text.toString()
        config.sshCertificate = etCertificate.text.toString()
        config.sshPayload = etPayload.text.toString()
        config.sshProxy = proxyText
        config.sni = etSni.text.toString().trim()

        config.description = AngConfigManager.generateDescription(config)

        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }
        MmkvManager.encodeServerConfig(editGuid, config)
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }

    private fun deleteServer(): Boolean {
        if (editGuid.isNotEmpty()) {
            if (editGuid != MmkvManager.getSelectServer()) {
                if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
                    showDeleteConfirmDialog(context = this, messageRes = R.string.del_config_dialog_comfirm_message) {
                        MmkvManager.removeServer(editGuid)
                        finish()
                    }
                } else {
                    MmkvManager.removeServer(editGuid)
                    finish()
                }
            } else {
                snackbarError(getString(R.string.toast_action_not_allowed), title = getString(R.string.title_alerter_error))
            }
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        val delButton = menu.findItem(R.id.del_config)
        val saveButton = menu.findItem(R.id.save_config)

        if (editGuid.isNotEmpty()) {
            if (isRunning) {
                delButton?.isVisible = false
                saveButton?.isVisible = false
            }
        } else {
            delButton?.isVisible = false
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.del_config -> {
            deleteServer()
            true
        }
        R.id.save_config -> {
            saveServer()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
