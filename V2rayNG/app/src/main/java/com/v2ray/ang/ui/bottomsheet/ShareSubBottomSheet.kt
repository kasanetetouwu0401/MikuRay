package com.v2ray.ang.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.BannerImageCache

class ShareSubBottomSheet : BaseBottomSheetFragment() {

    interface OnShareSubOptionClickListener {
        fun onShareSubOptionClicked(optionId: Int, url: String)
    }

    private var mListener: OnShareSubOptionClickListener? = null
    private var subUrl: String = ""

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnShareSubOptionClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnShareSubOptionClickListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subUrl = arguments?.getString(ARG_URL) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.uwu_bottom_sheet_share_sub, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val particlesView = view.findViewById<View>(R.id.ParticlesView)
        if (particlesView != null) {
            val disabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_PARTICLES_SHEET, false)
            particlesView.visibility = if (disabled) View.GONE else View.VISIBLE
        }
        loadBanner(view)

        val clickListener = View.OnClickListener {
            mListener?.onShareSubOptionClicked(it.id, subUrl)
            dismiss()
        }

        view.findViewById<View>(R.id.share_qrcode)?.setOnClickListener(clickListener)
        view.findViewById<View>(R.id.share_clipboard)?.setOnClickListener(clickListener)
    }

    private fun loadBanner(view: View) {
        val bannerImageView = view.findViewById<ImageView>(R.id.img_banner_sheet) ?: return
        bannerImageView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_SHEET_BANNER_URI)
        BannerImageCache.load(
            context = requireContext(),
            target = bannerImageView,
            namespace = "sheet",
            uriString = uriString,
            defaultDrawableRes = R.drawable.uwu_banner_sheet
        )
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {
        const val TAG = "ShareSubBottomSheet"
        private const val ARG_URL = "arg_url"

        fun newInstance(url: String): ShareSubBottomSheet {
            return ShareSubBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                }
            }
        }
    }
}
