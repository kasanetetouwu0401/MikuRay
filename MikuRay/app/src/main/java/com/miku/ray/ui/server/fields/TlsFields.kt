package com.miku.ray.ui.server.fields

import android.content.res.Resources
import android.text.TextUtils
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import com.miku.ray.AppConfig.REALITY
import com.miku.ray.AppConfig.TLS
import com.miku.ray.R
import com.miku.ray.databinding.LayoutTlsBinding
import com.miku.ray.databinding.LayoutTlsHysteria2Binding
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.util.Utils

class TlsFields private constructor(
    private val resources: Resources,
    views: Views
) {

    private data class Views(
        val spStreamSecurity: AutoCompleteTextView,
        val spAllowInsecure: AutoCompleteTextView?,
        val spStreamFingerprint: AutoCompleteTextView?,
        val spStreamAlpn: AutoCompleteTextView?,
        val etSni: EditText?,
        val etPublicKey: EditText?,
        val etShortId: EditText?,
        val etSpiderX: EditText?,
        val etMldsa65Verify: EditText?,
        val etEchConfigList: EditText?,
        val etVerifyPeerCertByName: EditText?,
        val etPinnedCa256: EditText?,
        val btnPinnedCa256Action: Button?,
        val containerAllowInsecure: View?,
        val containerSni: View?,
        val containerFingerprint: View?,
        val containerAlpn: View?,
        val containerPublicKey: View?,
        val containerShortId: View?,
        val containerSpiderX: View?,
        val containerMldsa65Verify: View?,
        val containerEchConfigList: View?,
        val containerVerifyPeerCertByName: View?,
        val containerPinnedCa256: View?
    )

    constructor(binding: LayoutTlsBinding) : this(
        binding.root.resources,
        Views(
            spStreamSecurity = binding.spStreamSecurity,
            spAllowInsecure = binding.spAllowInsecure,
            spStreamFingerprint = binding.spStreamFingerprint,
            spStreamAlpn = binding.spStreamAlpn,
            etSni = binding.etSni,
            etPublicKey = binding.etPublicKey,
            etShortId = binding.etShortId,
            etSpiderX = binding.etSpiderX,
            etMldsa65Verify = binding.etMldsa65Verify,
            etEchConfigList = binding.etEchConfigList,
            etVerifyPeerCertByName = binding.etVerifyPeerCertByName,
            etPinnedCa256 = binding.etPinnedCa256,
            btnPinnedCa256Action = binding.btnPinnedCa256Action,
            containerAllowInsecure = binding.layAllowInsecure,
            containerSni = binding.laySni,
            containerFingerprint = binding.layStreamFingerprint,
            containerAlpn = binding.layStreamAlpn,
            containerPublicKey = binding.layPublicKey,
            containerShortId = binding.layShortId,
            containerSpiderX = binding.laySpiderX,
            containerMldsa65Verify = binding.layMldsa65Verify,
            containerEchConfigList = binding.layEchConfigList,
            containerVerifyPeerCertByName = binding.layVerifyPeerCertByName,
            containerPinnedCa256 = binding.layPinnedCa256
        )
    )

    constructor(binding: LayoutTlsHysteria2Binding) : this(
        binding.root.resources,
        Views(
            spStreamSecurity = binding.spStreamSecurity,
            spAllowInsecure = binding.spAllowInsecure,
            spStreamFingerprint = null,
            spStreamAlpn = null,
            etSni = binding.etSni,
            etPublicKey = null,
            etShortId = null,
            etSpiderX = null,
            etMldsa65Verify = null,
            etEchConfigList = null,
            etVerifyPeerCertByName = null,
            etPinnedCa256 = binding.etPinnedCa256,
            btnPinnedCa256Action = binding.btnPinnedCa256Action,
            containerAllowInsecure = binding.layAllowInsecure,
            containerSni = binding.laySni,
            containerFingerprint = null,
            containerAlpn = null,
            containerPublicKey = null,
            containerShortId = null,
            containerSpiderX = null,
            containerMldsa65Verify = null,
            containerEchConfigList = null,
            containerVerifyPeerCertByName = null,
            containerPinnedCa256 = binding.layPinnedCa256
        )
    )

    private val spStreamSecurity = views.spStreamSecurity
    private val spAllowInsecure = views.spAllowInsecure
    private val spStreamFingerprint = views.spStreamFingerprint
    private val spStreamAlpn = views.spStreamAlpn
    private val etSni = views.etSni
    private val etPublicKey = views.etPublicKey
    private val etShortId = views.etShortId
    private val etSpiderX = views.etSpiderX
    private val etMldsa65Verify = views.etMldsa65Verify
    private val etEchConfigList = views.etEchConfigList
    private val etVerifyPeerCertByName = views.etVerifyPeerCertByName
    private val etPinnedCa256 = views.etPinnedCa256
    private val btnPinnedCa256Action = views.btnPinnedCa256Action
    private val containerAllowInsecure = views.containerAllowInsecure
    private val containerSni = views.containerSni
    private val containerFingerprint = views.containerFingerprint
    private val containerAlpn = views.containerAlpn
    private val containerPublicKey = views.containerPublicKey
    private val containerShortId = views.containerShortId
    private val containerSpiderX = views.containerSpiderX
    private val containerMldsa65Verify = views.containerMldsa65Verify
    private val containerEchConfigList = views.containerEchConfigList
    private val containerVerifyPeerCertByName = views.containerVerifyPeerCertByName
    private val containerPinnedCa256 = views.containerPinnedCa256

    private val streamSecuritys: Array<out String> = resources.getStringArray(R.array.streamsecurityxs)
    private val allowinsecures: Array<out String> = resources.getStringArray(R.array.allowinsecures)
    private val uTlsItems: Array<out String> = resources.getStringArray(R.array.streamsecurity_utls)
    private val alpns: Array<out String> = resources.getStringArray(R.array.streamsecurity_alpn)

    val selectedSecurityText: String get() = spStreamSecurity.text?.toString().orEmpty()
    val pinnedCa256Text: String? get() = etPinnedCa256?.text?.toString()

    fun setPinnedCa256Text(value: String) {
        etPinnedCa256?.text = Utils.getEditable(value)
    }

    fun setFetchButtonEnabled(enabled: Boolean) {
        btnPinnedCa256Action?.isEnabled = enabled
    }

    fun setOnSecurityChanged(onChanged: (security: String) -> Unit) {
        spStreamSecurity.setOnItemClickListener { parent, _, position, _ ->
            onChanged(parent.getItemAtPosition(position).toString())
        }
    }

    fun setOnFetchCertClick(onClick: () -> Unit) {
        btnPinnedCa256Action?.setOnClickListener { onClick() }
    }

    fun isSecuritySelected(): Boolean {
        val pos = Utils.arrayFind(streamSecuritys, selectedSecurityText)
        return pos >= 0 && !TextUtils.isEmpty(streamSecuritys[pos])
    }

    fun updateForSecurity(security: String) {
        val isBlank = security.isBlank()
        val isTLS = security.equals(TLS, ignoreCase = true)

        when {
            isBlank -> {
                listOf(
                    containerSni,
                    containerFingerprint,
                    containerAlpn,
                    containerAllowInsecure,
                    containerPublicKey,
                    containerShortId,
                    containerSpiderX,
                    containerMldsa65Verify,
                    containerEchConfigList,
                    containerVerifyPeerCertByName,
                    containerPinnedCa256,
                    btnPinnedCa256Action
                ).forEach { it?.visibility = View.GONE }
            }
            isTLS -> {
                listOf(
                    containerSni,
                    containerFingerprint,
                    containerAlpn,
                    containerAllowInsecure,
                    containerEchConfigList,
                    containerVerifyPeerCertByName,
                    containerPinnedCa256,
                    btnPinnedCa256Action
                ).forEach { it?.visibility = View.VISIBLE }
                listOf(
                    containerPublicKey,
                    containerShortId,
                    containerSpiderX,
                    containerMldsa65Verify
                ).forEach { it?.visibility = View.GONE }
            }
            else -> {
                listOf(
                    containerSni,
                    containerFingerprint,
                    containerPublicKey,
                    containerShortId,
                    containerSpiderX,
                    containerMldsa65Verify
                ).forEach { it?.visibility = View.VISIBLE }
                listOf(
                    containerAlpn,
                    containerAllowInsecure,
                    containerEchConfigList,
                    containerVerifyPeerCertByName,
                    containerPinnedCa256,
                    btnPinnedCa256Action
                ).forEach { it?.visibility = View.GONE }
            }
        }
    }

    fun bind(config: ProfileItem) {
        val streamSecurity = Utils.arrayFind(streamSecuritys, config.security.orEmpty())
        if (streamSecurity < 0) {
            spStreamSecurity.setText("", false)
            updateForSecurity("")
            return
        }

        spStreamSecurity.setText(streamSecuritys[streamSecurity], false)
        updateForSecurity(streamSecuritys[streamSecurity])

        etSni?.text = Utils.getEditable(config.sni)
        config.fingerPrint?.let {
            val utlsIndex = Utils.arrayFind(uTlsItems, it)
            if (utlsIndex >= 0) spStreamFingerprint?.setText(uTlsItems[utlsIndex], false)
        }
        config.alpn?.let {
            val alpnIndex = Utils.arrayFind(alpns, it)
            if (alpnIndex >= 0) spStreamAlpn?.setText(alpns[alpnIndex], false)
        }

        if (config.security == TLS) {
            val allowinsecure = Utils.arrayFind(allowinsecures, config.insecure.toString())
            if (allowinsecure >= 0) spAllowInsecure?.setText(allowinsecures[allowinsecure], false)
            etEchConfigList?.text = Utils.getEditable(config.echConfigList)
            etVerifyPeerCertByName?.text = Utils.getEditable(config.verifyPeerCertByName)
            etPinnedCa256?.text = Utils.getEditable(config.pinnedCA256)
        } else if (config.security == REALITY) {
            etPublicKey?.text = Utils.getEditable(config.publicKey.orEmpty())
            etShortId?.text = Utils.getEditable(config.shortId.orEmpty())
            etSpiderX?.text = Utils.getEditable(config.spiderX.orEmpty())
            etMldsa65Verify?.text = Utils.getEditable(config.mldsa65Verify.orEmpty())
        }
    }

    fun clear() {
        if (streamSecuritys.isNotEmpty()) {
            spStreamSecurity.setText(streamSecuritys[0], false)
            updateForSecurity(streamSecuritys[0])
        }
        if (allowinsecures.isNotEmpty()) {
            spAllowInsecure?.setText(allowinsecures[0], false)
        }
        etSni?.text = null
        etPublicKey?.text = null
    }

    fun save(config: ProfileItem): Boolean {
        val streamSecPos = Utils.arrayFind(streamSecuritys, selectedSecurityText)
        if (streamSecPos < 0) return false

        val sniField = etSni?.text?.toString()?.trim()
        val allowInsecurePos = Utils.arrayFind(allowinsecures, spAllowInsecure?.text.toString())
        val utlsPos = Utils.arrayFind(uTlsItems, spStreamFingerprint?.text.toString())
        val alpnPos = Utils.arrayFind(alpns, spStreamAlpn?.text.toString())

        val publicKey = etPublicKey?.text?.toString()
        val shortId = etShortId?.text?.toString()
        val spiderX = etSpiderX?.text?.toString()
        val mldsa65Verify = etMldsa65Verify?.text?.toString()
        val echConfigList = etEchConfigList?.text?.toString()
        val verifyPeerCertByName = etVerifyPeerCertByName?.text?.toString()
        val pinnedCA256 = etPinnedCa256?.text?.toString()

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

        return true
    }
}
