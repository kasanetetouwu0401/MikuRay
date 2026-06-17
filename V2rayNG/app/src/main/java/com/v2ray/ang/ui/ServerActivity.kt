package com.v2ray.ang.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.util.showDeleteConfirmDialog
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputLayout
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.DEFAULT_PORT
import com.v2ray.ang.AppConfig.REALITY
import com.v2ray.ang.AppConfig.TLS
import com.v2ray.ang.AppConfig.WIREGUARD_LOCAL_ADDRESS_V4
import com.v2ray.ang.AppConfig.WIREGUARD_LOCAL_MTU
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.extension.alertError
import com.v2ray.ang.extension.alertSuccess
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.CertificateFingerprintManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.SoftInputAssist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerActivity : BaseActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val createConfigType by lazy {
        EConfigType.fromInt(intent.getIntExtra("createConfigType", EConfigType.VMESS.value))
            ?: EConfigType.VMESS
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    private val securitys: Array<out String> by lazy { resources.getStringArray(R.array.securitys) }
    private val shadowsocksSecuritys: Array<out String> by lazy { resources.getStringArray(R.array.ss_securitys) }
    private val flows: Array<out String> by lazy { resources.getStringArray(R.array.flows) }
    private val networks: Array<out String> by lazy { resources.getStringArray(R.array.networks) }
    private val tcpTypes: Array<out String> by lazy { resources.getStringArray(R.array.header_type_tcp) }
    private val kcpAndQuicTypes: Array<out String> by lazy { resources.getStringArray(R.array.header_type_kcp_and_quic) }
    private val grpcModes: Array<out String> by lazy { resources.getStringArray(R.array.mode_type_grpc) }
    private val streamSecuritys: Array<out String> by lazy { resources.getStringArray(R.array.streamsecurityxs) }
    private val allowinsecures: Array<out String> by lazy { resources.getStringArray(R.array.allowinsecures) }
    private val uTlsItems: Array<out String> by lazy { resources.getStringArray(R.array.streamsecurity_utls) }
    private val alpns: Array<out String> by lazy { resources.getStringArray(R.array.streamsecurity_alpn) }
    private val xhttpMode: Array<out String> by lazy { resources.getStringArray(R.array.xhttp_mode) }
    private val browserDialerModes: Array<out String> by lazy {
        resources.getStringArray(R.array.browser_dialer_mode)
    }

    private val et_remarks: EditText by lazy { findViewById(R.id.et_remarks) }
    private val et_address: EditText by lazy { findViewById(R.id.et_address) }
    private val et_port: EditText by lazy { findViewById(R.id.et_port) }
    private val et_id: EditText by lazy { findViewById(R.id.et_id) }
    private val et_security: EditText? by lazy { findViewById(R.id.et_security) }
    
    private val sp_flow: AutoCompleteTextView? by lazy { findViewById(R.id.sp_flow) }
    private val sp_security: AutoCompleteTextView? by lazy { findViewById(R.id.sp_security) }
    private val sp_stream_security: AutoCompleteTextView? by lazy { findViewById(R.id.sp_stream_security) }
    private val sp_allow_insecure: AutoCompleteTextView? by lazy { findViewById(R.id.sp_allow_insecure) }
    private val sp_stream_fingerprint: AutoCompleteTextView? by lazy { findViewById(R.id.sp_stream_fingerprint) }
    private val sp_network: AutoCompleteTextView? by lazy { findViewById(R.id.sp_network) }
    private val sp_header_type: AutoCompleteTextView? by lazy { findViewById(R.id.sp_header_type) }
    private val sp_stream_alpn: AutoCompleteTextView? by lazy { findViewById(R.id.sp_stream_alpn) }

    private val container_allow_insecure: View? by lazy { findViewById(R.id.lay_allow_insecure) }
    private val et_sni: EditText? by lazy { findViewById(R.id.et_sni) }
    private val container_sni: View? by lazy { findViewById(R.id.lay_sni) }
    private val container_fingerprint: View? by lazy { findViewById(R.id.lay_stream_fingerprint) }
    private val container_alpn: View? by lazy { findViewById(R.id.lay_stream_alpn) }
    private val container_public_key: View? by lazy { findViewById(R.id.lay_public_key) }
    private val container_short_id: View? by lazy { findViewById(R.id.lay_short_id) }
    private val container_spider_x: View? by lazy { findViewById(R.id.lay_spider_x) }
    private val container_mldsa65_verify: View? by lazy { findViewById(R.id.lay_mldsa65_verify) }
    private val layout_kcp: View? by lazy { findViewById(R.id.layout_kcp) }
    private val layout_extra: View? by lazy { findViewById(R.id.layout_extra) }
    private val container_ech_config_list: View? by lazy { findViewById(R.id.lay_ech_config_list) }
    private val container_verify_peer_cert_by_name: View? by lazy { findViewById(R.id.lay_verify_peer_cert_by_name) }
    private val container_pinned_ca256: View? by lazy { findViewById(R.id.lay_pinned_ca256) }
    private val layout_browser_dialer: com.google.android.material.textfield.TextInputLayout? by lazy { findViewById(R.id.layout_browser_dialer) }
    private val sp_browser_dialer_mode: AutoCompleteTextView? by lazy { findViewById(R.id.sp_browser_dialer_mode) }

    private val til_header_type: TextInputLayout? by lazy { findViewById(R.id.til_header_type) }
    private val til_request_host: TextInputLayout? by lazy { findViewById(R.id.til_request_host) }
    private val til_path: TextInputLayout? by lazy { findViewById(R.id.til_path) }

    private val et_request_host: EditText? by lazy { findViewById(R.id.et_request_host) }
    private val et_path: EditText? by lazy { findViewById(R.id.et_path) }
    private val et_public_key: EditText? by lazy { findViewById(R.id.et_public_key) }
    private val et_preshared_key: EditText? by lazy { findViewById(R.id.et_preshared_key) }
    private val et_short_id: EditText? by lazy { findViewById(R.id.et_short_id) }
    private val et_spider_x: EditText? by lazy { findViewById(R.id.et_spider_x) }
    private val et_mldsa65_verify: EditText? by lazy { findViewById(R.id.et_mldsa65_verify) }
    private val et_reserved1: EditText? by lazy { findViewById(R.id.et_reserved1) }
    private val et_local_address: EditText? by lazy { findViewById(R.id.et_local_address) }
    private val et_local_mtu: EditText? by lazy { findViewById(R.id.et_local_mtu) }
    private val et_obfs_password: EditText? by lazy { findViewById(R.id.et_obfs_password) }
    private val et_port_hop: EditText? by lazy { findViewById(R.id.et_port_hop) }
    private val et_port_hop_interval: EditText? by lazy { findViewById(R.id.et_port_hop_interval) }
    private val et_bandwidth_down: EditText? by lazy { findViewById(R.id.et_bandwidth_down) }
    private val et_bandwidth_up: EditText? by lazy { findViewById(R.id.et_bandwidth_up) }
    private val et_kcp_mtu: EditText? by lazy { findViewById(R.id.et_kcp_mtu) }
    private val et_kcp_tti: EditText? by lazy { findViewById(R.id.et_kcp_tti) }
    private val et_extra: EditText? by lazy { findViewById(R.id.et_extra) }
    private val et_fm: EditText? by lazy { findViewById(R.id.et_fm) }
    private val et_ech_config_list: EditText? by lazy { findViewById(R.id.et_ech_config_list) }
    private val et_verify_peer_cert_by_name: EditText? by lazy { findViewById(R.id.et_verify_peer_cert_by_name) }
    private val et_pinned_ca256: EditText? by lazy { findViewById(R.id.et_pinned_ca256) }
    private val btn_pinned_ca256_action: Button? by lazy { findViewById(R.id.btn_pinned_ca256_action) }
    
    private lateinit var softInputAssist: SoftInputAssist

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = MmkvManager.decodeServerConfig(editGuid)

        val layoutId = when (config?.configType ?: createConfigType) {
            EConfigType.VMESS -> R.layout.activity_server_vmess
            EConfigType.CUSTOM -> null
            EConfigType.SHADOWSOCKS -> R.layout.activity_server_shadowsocks
            EConfigType.SOCKS, EConfigType.HTTP -> R.layout.activity_server_socks
            EConfigType.VLESS -> R.layout.activity_server_vless
            EConfigType.TROJAN -> R.layout.activity_server_trojan
            EConfigType.WIREGUARD -> R.layout.activity_server_wireguard
            EConfigType.HYSTERIA2 -> R.layout.activity_server_hysteria2
            EConfigType.POLICYGROUP -> null
            else -> null
        } ?: return
        
        setContentView(layoutId)
        
        softInputAssist = SoftInputAssist(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = (config?.configType ?: createConfigType).toString())

        sp_network?.setOnItemClickListener { parent, _, position, _ ->
            val selectedItem = parent.getItemAtPosition(position).toString()
            updateNetworkUI(selectedItem, config)
        }

        sp_stream_security?.setOnItemClickListener { parent, _, position, _ ->
            val selectedItem = parent.getItemAtPosition(position).toString()
            updateStreamSecurityUI(selectedItem)
        }

        btn_pinned_ca256_action?.setOnClickListener {
            fetchPinnedCA256ForCurrentConfig()
        }

        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
        }
    }

    private fun updateNetworkUI(network: String, config: ProfileItem?) {
        val types = transportTypes(network)
        sp_header_type?.isEnabled = types.size > 1
        
        val adapter = ArrayAdapter(this@ServerActivity, android.R.layout.simple_dropdown_item_1line, types)
        sp_header_type?.setAdapter(adapter)

        til_header_type?.hint = when (network) {
            NetworkType.GRPC.type -> getString(R.string.server_lab_mode_type)
            NetworkType.XHTTP.type -> getString(R.string.server_lab_xhttp_mode)
            else -> getString(R.string.server_lab_head_type)
        }

        val headerTypeStr = when (network) {
            NetworkType.GRPC.type -> config?.mode
            NetworkType.XHTTP.type -> config?.xhttpMode
            else -> config?.headerType
        }.orEmpty()

        val typePos = Utils.arrayFind(types, headerTypeStr)
        val finalTypeIndex = if (typePos >= 0) typePos else 0
        sp_header_type?.setText(if (types.isNotEmpty()) types[finalTypeIndex] else "", false)

        et_request_host?.text = Utils.getEditable(
            when (network) {
                NetworkType.GRPC.type -> config?.authority
                else -> config?.host
            }.orEmpty()
        )
        et_path?.text = Utils.getEditable(
            when (network) {
                NetworkType.KCP.type -> config?.seed
                NetworkType.GRPC.type -> config?.serviceName
                else -> config?.path
            }.orEmpty()
        )

        til_request_host?.hint = getString(
            when (network) {
                NetworkType.TCP.type -> R.string.server_lab_request_host_http
                NetworkType.WS.type -> R.string.server_lab_request_host_ws
                NetworkType.HTTP_UPGRADE.type -> R.string.server_lab_request_host_httpupgrade
                NetworkType.XHTTP.type -> R.string.server_lab_request_host_xhttp
                NetworkType.H2.type -> R.string.server_lab_request_host_h2
                NetworkType.GRPC.type -> R.string.server_lab_request_host_grpc
                else -> R.string.server_lab_request_host
            }
        )

        til_path?.hint = getString(
            when (network) {
                NetworkType.KCP.type -> R.string.server_lab_path_kcp
                NetworkType.WS.type -> R.string.server_lab_path_ws
                NetworkType.HTTP_UPGRADE.type -> R.string.server_lab_path_httpupgrade
                NetworkType.XHTTP.type -> R.string.server_lab_path_xhttp
                NetworkType.H2.type -> R.string.server_lab_path_h2
                NetworkType.GRPC.type -> R.string.server_lab_path_grpc
                else -> R.string.server_lab_path
            }
        )

        et_extra?.text = Utils.getEditable(
            when (network) {
                NetworkType.XHTTP.type -> config?.xhttpExtra
                else -> null
            }.orEmpty()
        )
        et_fm?.text = Utils.getEditable(config?.finalMask)

        layout_kcp?.visibility = when (network) {
            NetworkType.KCP.type -> View.VISIBLE
            else -> View.GONE
        }
        et_kcp_mtu?.text = Utils.getEditable(config?.kcpMtu?.toString().orEmpty())
        et_kcp_tti?.text = Utils.getEditable(config?.kcpTti?.toString().orEmpty())

        layout_extra?.visibility = when (network) {
            NetworkType.XHTTP.type -> View.VISIBLE
            else -> View.GONE
        }

        layout_browser_dialer?.visibility =
            when (network) {
                NetworkType.WS.type -> View.VISIBLE
                NetworkType.XHTTP.type -> View.VISIBLE
                else -> View.GONE
            }
    }

    private fun updateStreamSecurityUI(security: String) {
        val isBlank = security.isBlank()
        val isTLS = security.equals(TLS, ignoreCase = true)

        when {
            isBlank -> {
                listOf(
                    container_sni,
                    container_fingerprint,
                    container_alpn,
                    container_allow_insecure,
                    container_public_key,
                    container_short_id,
                    container_spider_x,
                    container_mldsa65_verify,
                    container_ech_config_list,
                    container_verify_peer_cert_by_name,
                    container_pinned_ca256,
                    btn_pinned_ca256_action
                ).forEach { it?.visibility = View.GONE }
            }
            isTLS -> {
                listOf(
                    container_sni,
                    container_fingerprint,
                    container_alpn,
                    container_allow_insecure,
                    container_ech_config_list,
                    container_verify_peer_cert_by_name,
                    container_pinned_ca256,
                    btn_pinned_ca256_action
                ).forEach { it?.visibility = View.VISIBLE }
                listOf(
                    container_public_key,
                    container_short_id,
                    container_spider_x,
                    container_mldsa65_verify
                ).forEach { it?.visibility = View.GONE }
            }
            else -> {
                // REALITY dan lainnya
                listOf(
                    container_sni,
                    container_fingerprint,
                    container_public_key,
                    container_short_id,
                    container_spider_x,
                    container_mldsa65_verify
                ).forEach { it?.visibility = View.VISIBLE }
                listOf(
                    container_alpn,
                    container_allow_insecure,
                    container_ech_config_list,
                    container_verify_peer_cert_by_name,
                    container_pinned_ca256,
                    btn_pinned_ca256_action
                ).forEach { it?.visibility = View.GONE }
            }
        }
    }

    private fun bindingServer(config: ProfileItem): Boolean {
        et_remarks.text = Utils.getEditable(config.remarks)
        et_address.text = Utils.getEditable(config.server.orEmpty())
        et_port.text = Utils.getEditable(config.serverPort ?: DEFAULT_PORT.toString())
        et_id.text = Utils.getEditable(config.password.orEmpty())

        if (config.configType == EConfigType.SOCKS || config.configType == EConfigType.HTTP) {
            et_security?.text = Utils.getEditable(config.username.orEmpty())
        } else if (config.configType == EConfigType.VLESS) {
            et_security?.text = Utils.getEditable(config.method.orEmpty())
            val flow = Utils.arrayFind(flows, config.flow.orEmpty())
            if (flow >= 0) sp_flow?.setText(flows[flow], false)
        } else if (config.configType == EConfigType.WIREGUARD) {
            et_id.text = Utils.getEditable(config.secretKey.orEmpty())
            et_public_key?.text = Utils.getEditable(config.publicKey.orEmpty())
            et_preshared_key?.visibility = View.VISIBLE
            et_preshared_key?.text = Utils.getEditable(config.preSharedKey.orEmpty())
            et_reserved1?.text = Utils.getEditable(config.reserved ?: "0,0,0")
            et_local_address?.text = Utils.getEditable(config.localAddress ?: WIREGUARD_LOCAL_ADDRESS_V4)
            et_local_mtu?.text = Utils.getEditable(config.mtu?.toString() ?: WIREGUARD_LOCAL_MTU)
        } else if (config.configType == EConfigType.HYSTERIA2) {
            et_obfs_password?.text = Utils.getEditable(config.obfsPassword)
            et_port_hop?.text = Utils.getEditable(config.portHopping)
            et_port_hop_interval?.text = Utils.getEditable(config.portHoppingInterval)
            et_bandwidth_down?.text = Utils.getEditable(config.bandwidthDown)
            et_bandwidth_up?.text = Utils.getEditable(config.bandwidthUp)
        }
        val securityEncryptions = if (config.configType == EConfigType.SHADOWSOCKS) shadowsocksSecuritys else securitys
        val security = Utils.arrayFind(securityEncryptions, config.method.orEmpty())
        if (security >= 0) sp_security?.setText(securityEncryptions[security], false)

        val streamSecurity = Utils.arrayFind(streamSecuritys, config.security.orEmpty())
        if (streamSecurity >= 0) {
            sp_stream_security?.setText(streamSecuritys[streamSecurity], false)
            updateStreamSecurityUI(streamSecuritys[streamSecurity])
            
            et_sni?.text = Utils.getEditable(config.sni)
            config.fingerPrint?.let {
                val utlsIndex = Utils.arrayFind(uTlsItems, it)
                if (utlsIndex >= 0) sp_stream_fingerprint?.setText(uTlsItems[utlsIndex], false)
            }
            config.alpn?.let {
                val alpnIndex = Utils.arrayFind(alpns, it)
                if (alpnIndex >= 0) sp_stream_alpn?.setText(alpns[alpnIndex], false)
            }
            
            if (config.security == TLS) {
                val allowinsecure = Utils.arrayFind(allowinsecures, config.insecure.toString())
                if (allowinsecure >= 0) sp_allow_insecure?.setText(allowinsecures[allowinsecure], false)
                et_ech_config_list?.text = Utils.getEditable(config.echConfigList)
                et_verify_peer_cert_by_name?.text = Utils.getEditable(config.verifyPeerCertByName)
                et_pinned_ca256?.text = Utils.getEditable(config.pinnedCA256)
            } else if (config.security == REALITY) {
                et_public_key?.text = Utils.getEditable(config.publicKey.orEmpty())
                et_short_id?.text = Utils.getEditable(config.shortId.orEmpty())
                et_spider_x?.text = Utils.getEditable(config.spiderX.orEmpty())
                et_mldsa65_verify?.text = Utils.getEditable(config.mldsa65Verify.orEmpty())
            }
        } else {
            sp_stream_security?.setText("", false)
            updateStreamSecurityUI("")
        }

        val network = Utils.arrayFind(networks, config.network.orEmpty())
        if (network >= 0) {
            sp_network?.setText(networks[network], false)
            updateNetworkUI(networks[network], config)
        } else {
            updateNetworkUI("", config)
        }

        val browserDialerMode = Utils.arrayFind(browserDialerModes, config.browserDialerMode.orEmpty())
        if (browserDialerMode >= 0) {
            sp_browser_dialer_mode?.setText(browserDialerModes[browserDialerMode], false)
        }

        return true
    }

    private fun clearServer(): Boolean {
        et_remarks.text = null
        et_address.text = null
        et_port.text = Utils.getEditable(DEFAULT_PORT.toString())
        et_id.text = null
        sp_security?.setText(securitys[0], false)
        
        sp_network?.setText(networks[0], false)
        updateNetworkUI(networks[0], null)

        sp_stream_security?.setText(streamSecuritys[0], false)
        updateStreamSecurityUI(streamSecuritys[0])
        
        sp_allow_insecure?.setText(allowinsecures[0], false)
        et_sni?.text = null
        sp_flow?.setText(flows[0], false)
        
        et_public_key?.text = null
        et_reserved1?.text = Utils.getEditable("0,0,0")
        et_local_address?.text = Utils.getEditable(WIREGUARD_LOCAL_ADDRESS_V4)
        et_local_mtu?.text = Utils.getEditable(WIREGUARD_LOCAL_MTU)
        sp_browser_dialer_mode?.setText(browserDialerModes[0], false)
        return true
    }

    private fun saveServer(): Boolean {
        if (TextUtils.isEmpty(et_remarks.text.toString())) {
            alertError(
                getString(R.string.server_lab_remarks), 
                title = getString(R.string.title_alerter_error)
            )
            return false
        }
        if (TextUtils.isEmpty(et_address.text.toString())) {
            alertError(
                getString(R.string.server_lab_address), 
                title = getString(R.string.title_alerter_error)
            )
            return false
        }
        if (createConfigType != EConfigType.HYSTERIA2) {
            if (Utils.parseInt(et_port.text.toString()) <= 0) {
                alertError(
                    getString(R.string.server_lab_port), 
                    title = getString(R.string.title_alerter_error)
                )
                return false
            }
        }
        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(createConfigType)
        
        if (config.configType != EConfigType.SOCKS
            && config.configType != EConfigType.HTTP
            && TextUtils.isEmpty(et_id.text.toString())
        ) {
            if (config.configType == EConfigType.TROJAN
                || config.configType == EConfigType.SHADOWSOCKS
                || config.configType == EConfigType.HYSTERIA2
            ) {
                alertError(
                    getString(R.string.server_lab_id3), 
                    title = getString(R.string.title_alerter_error)
                )
            } else {
                alertError(
                    getString(R.string.server_lab_id), 
                    title = getString(R.string.title_alerter_error)
                )
            }
            return false
        }
        sp_stream_security?.let {
            val secPos = Utils.arrayFind(streamSecuritys, it.text.toString())
            if (config.configType == EConfigType.TROJAN && (secPos < 0 || TextUtils.isEmpty(streamSecuritys[secPos]))) {
                alertError(
                    getString(R.string.server_lab_stream_security), 
                    title = getString(R.string.title_alerter_error)
                )
                return false
            }
        }
        if (et_extra?.text?.toString().isNotNullEmpty()) {
            if (JsonUtil.parseString(et_extra?.text?.toString()) == null) {
                alertError(
                    getString(R.string.server_lab_xhttp_extra), 
                    title = getString(R.string.title_alerter_error)
                )
                return false
            }
        }

        if (et_fm?.text?.toString().isNotNullEmpty()) {
            if (JsonUtil.parseString(et_fm?.text?.toString()) == null) {
                alertError(
                    getString(R.string.server_lab_final_mask), 
                    title = getString(R.string.title_alerter_error)
                )
                return false
            }
        }

        saveCommon(config)
        saveStreamSettings(config)
        saveTls(config)

        config.description = AngConfigManager.generateDescription(config)

        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }
        MmkvManager.encodeServerConfig(editGuid, config)
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }

    private fun saveCommon(config: ProfileItem) {
        config.remarks = et_remarks.text.toString().trim()
        config.server = et_address.text.toString().trim()
        config.serverPort = et_port.text.toString().trim()
        config.password = et_id.text.toString().trim()

        if (config.configType == EConfigType.VMESS) {
            val secPos = Utils.arrayFind(securitys, sp_security?.text.toString())
            config.method = securitys[if (secPos >= 0) secPos else 0]
        } else if (config.configType == EConfigType.VLESS) {
            config.method = et_security?.text.toString().trim()
            val flowPos = Utils.arrayFind(flows, sp_flow?.text.toString())
            config.flow = flows[if (flowPos >= 0) flowPos else 0]
        } else if (config.configType == EConfigType.SHADOWSOCKS) {
            val secPos = Utils.arrayFind(shadowsocksSecuritys, sp_security?.text.toString())
            config.method = shadowsocksSecuritys[if (secPos >= 0) secPos else 0]
        } else if (config.configType == EConfigType.SOCKS || config.configType == EConfigType.HTTP) {
            if (!TextUtils.isEmpty(et_security?.text) || !TextUtils.isEmpty(et_id.text)) {
                config.username = et_security?.text.toString().trim()
            }
        } else if (config.configType == EConfigType.WIREGUARD) {
            config.secretKey = et_id.text.toString().trim()
            config.publicKey = et_public_key?.text.toString().trim()
            config.preSharedKey = et_preshared_key?.text.toString().trim()
            config.reserved = et_reserved1?.text.toString().trim()
            config.localAddress = et_local_address?.text.toString().trim()
            config.mtu = Utils.parseInt(et_local_mtu?.text.toString())
        } else if (config.configType == EConfigType.HYSTERIA2) {
            config.obfsPassword = et_obfs_password?.text?.toString()
            config.portHopping = et_port_hop?.text?.toString()
            config.portHoppingInterval = et_port_hop_interval?.text?.toString()?.trim()
            config.bandwidthDown = et_bandwidth_down?.text?.toString()
            config.bandwidthUp = et_bandwidth_up?.text?.toString()
        }
    }

    private fun saveStreamSettings(profileItem: ProfileItem) {
        val networkPos = Utils.arrayFind(networks, sp_network?.text.toString())
        if (networkPos < 0) return
        
        val types = transportTypes(networks[networkPos])
        val typePos = Utils.arrayFind(types, sp_header_type?.text.toString())
        if (typePos < 0) return

        val requestHost = et_request_host?.text?.toString()?.trim() ?: return
        val path = et_path?.text?.toString()?.trim() ?: return

        profileItem.network = networks[networkPos]
        profileItem.headerType = types[typePos]
        profileItem.host = requestHost
        profileItem.path = path
        profileItem.seed = path
        profileItem.quicSecurity = requestHost
        profileItem.quicKey = path
        profileItem.mode = types[typePos]
        profileItem.serviceName = path
        profileItem.authority = requestHost
        profileItem.xhttpMode = types[typePos]
        profileItem.xhttpExtra = et_extra?.text?.toString()?.trim().nullIfBlank()
        profileItem.finalMask = et_fm?.text?.toString()?.trim()?.nullIfBlank()
        profileItem.kcpMtu = et_kcp_mtu?.text?.toString()?.toIntOrNull()
        profileItem.kcpTti = et_kcp_tti?.text?.toString()?.toIntOrNull()

        if (networkPos >= 0 && (networks[networkPos] == NetworkType.WS.type || networks[networkPos] == NetworkType.XHTTP.type)) {
            val browserDialerMode = sp_browser_dialer_mode?.text?.toString() ?: browserDialerModes[0]
            if (browserDialerMode != browserDialerModes[0]) {
                profileItem.browserDialerMode = browserDialerMode
            } else {
                profileItem.browserDialerMode = null
            }
        } else {
            profileItem.browserDialerMode = null
        }
    }

    private fun saveTls(config: ProfileItem) {
        val streamSecPos = Utils.arrayFind(streamSecuritys, sp_stream_security?.text.toString())
        if (streamSecPos < 0) return

        val sniField = et_sni?.text?.toString()?.trim()
        val allowInsecurePos = Utils.arrayFind(allowinsecures, sp_allow_insecure?.text.toString())
        val utlsPos = Utils.arrayFind(uTlsItems, sp_stream_fingerprint?.text.toString())
        val alpnPos = Utils.arrayFind(alpns, sp_stream_alpn?.text.toString())

        val publicKey = et_public_key?.text?.toString()
        val shortId = et_short_id?.text?.toString()
        val spiderX = et_spider_x?.text?.toString()
        val mldsa65Verify = et_mldsa65_verify?.text?.toString()
        val echConfigList = et_ech_config_list?.text?.toString()
        val verifyPeerCertByName = et_verify_peer_cert_by_name?.text?.toString()
        val pinnedCA256 = et_pinned_ca256?.text?.toString()

        val allowInsecure =
            if (allowInsecurePos < 0 || allowinsecures[allowInsecurePos].isBlank()) {
                false
            } else {
                allowinsecures[allowInsecurePos].toBoolean()
            }

        config.security = streamSecuritys[streamSecPos]
        config.insecure = allowInsecure
        config.sni = sniField
        config.fingerPrint = uTlsItems[if (utlsPos >= 0) utlsPos else 0]
        config.alpn = alpns[if (alpnPos >= 0) alpnPos else 0]
        config.publicKey = publicKey
        config.shortId = shortId
        config.spiderX = spiderX
        config.mldsa65Verify = mldsa65Verify
        config.echConfigList = echConfigList
        config.verifyPeerCertByName = verifyPeerCertByName
        config.pinnedCA256 = pinnedCA256
    }

    private fun fetchPinnedCA256ForCurrentConfig() {
        val config = buildCurrentProfileForCertificateFetch() ?: return

        lifecycleScope.launch {
            btn_pinned_ca256_action?.isEnabled = false
            try {
                val sha256 = withContext(Dispatchers.IO) {
                    CertificateFingerprintManager.fetchForManualFill(config)
                }
                if (sha256.isNullOrBlank()) {
                    alertError(
                        getString(R.string.toast_fetch_cert_sha256_failed),
                        title = getString(R.string.title_alerter_error)
                    )
                } else {
                    et_pinned_ca256?.text = Utils.getEditable(sha256)
                    alertSuccess(
                        getString(R.string.toast_fetch_cert_sha256_success),
                        title = getString(R.string.title_alerter_success)
                    )
                }
            } finally {
                btn_pinned_ca256_action?.isEnabled = true
            }
        }
    }

    private fun buildCurrentProfileForCertificateFetch(): ProfileItem? {
        if (TextUtils.isEmpty(et_address.text.toString())) {
            alertError(
                getString(R.string.server_lab_address),
                title = getString(R.string.title_alerter_error)
            )
            return null
        }

        val configType = MmkvManager.decodeServerConfig(editGuid)?.configType ?: createConfigType
        if (configType != EConfigType.HYSTERIA2 && Utils.parseInt(et_port.text.toString()) <= 0) {
            alertError(
                getString(R.string.server_lab_port),
                title = getString(R.string.title_alerter_error)
            )
            return null
        }

        val config = ProfileItem.create(configType)
        saveCommon(config)
        saveStreamSettings(config)
        saveTls(config)

        return config
    }


    private fun transportTypes(network: String?): Array<out String> {
        return when (network) {
            NetworkType.TCP.type -> tcpTypes
            NetworkType.KCP.type -> kcpAndQuicTypes
            NetworkType.GRPC.type -> grpcModes
            NetworkType.XHTTP.type -> xhttpMode
            else -> arrayOf("---")
        }
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
                alertError(
                    getString(R.string.toast_action_not_allowed), 
                    title = getString(R.string.title_alerter_error)
                )
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
    
    override fun onResume() {
        if (::softInputAssist.isInitialized) {
            softInputAssist.onResume()
        }
        super.onResume()
    }

    override fun onPause() {
        if (::softInputAssist.isInitialized) {
            softInputAssist.onPause()
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (::softInputAssist.isInitialized) {
            softInputAssist.onDestroy()
        }
        super.onDestroy()
    } 
}
