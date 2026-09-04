package com.miku.ray.ui.base

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.AngApplication
import com.miku.ray.R
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import com.miku.ray.helper.CustomDividerItemDecoration
import com.miku.ray.util.DPIController
import com.miku.ray.util.FontSizeController
import com.miku.ray.util.CustomFontManager
import com.miku.ray.util.GoogleSansFlexManager
import com.miku.ray.util.WindowBlurUtils
import com.qmdeve.blurview.widget.BlurView
import com.miku.ray.util.ThemeStateManager

abstract class BaseActivity : AppCompatActivity() {
    private var loadingOverlay: FrameLayout? = null
    private var loadingBlurMode: LoadingBlurMode? = null
    private var systemLoadingDialog: Dialog? = null

    private enum class LoadingBlurMode { BLUR_VIEW, DIM }

    private lateinit var themeStateManager: ThemeStateManager

    private var toolbarSubtitle: CharSequence? = null
    private var collapsingToolbarRef: CollapsingToolbarLayout? = null

    private var appBarRef: AppBarLayout? = null
    private var gradientHeaderView: View? = null
    private var gradientOffsetListener: AppBarLayout.OnOffsetChangedListener? = null
    private var defaultContentScrim: android.graphics.drawable.Drawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            AngApplication.application.applyActivityTheme(this)
        }
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        themeStateManager = ThemeStateManager(this)

        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                    if (f is DialogFragment) {
                        WindowBlurUtils.applyWindowBlur(f.dialog?.window)
                    }
                }
            },
            true
        )
    }

    override fun onResume() {
        super.onResume()
        com.miku.ray.handler.SettingsManager.refreshAutoNightModeIfNeeded()
        themeStateManager.checkThemeChangedAndRecreate()
        if (collapsingToolbarRef != null) {
            applyToolbarStyle()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        AngApplication.getCustomTypeface(this)?.let { typeface ->
            findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)?.apply {
                setExpandedTitleTypeface(typeface)
                setCollapsedTitleTypeface(typeface)
                setExpandedSubtitleTypeface(typeface)
                setCollapsedSubtitleTypeface(typeface)
            }
            CustomFontManager.applyToViewTree(typeface, window.decorView)
        }
        GoogleSansFlexManager.getBoldTypeface()?.let { typeface ->
            findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)?.apply {
                setExpandedTitleTypeface(typeface)
                setCollapsedTitleTypeface(typeface)
                setExpandedSubtitleTypeface(typeface)
                setCollapsedSubtitleTypeface(typeface)
            }
        }
        GoogleSansFlexManager.applyToBoldText(window.decorView)
    }

    override fun onContentChanged() {
        super.onContentChanged()

        AngApplication.getCustomTypeface(this)?.let { typeface ->
            CustomFontManager.applyToViewTree(typeface, window.decorView)
        }
        GoogleSansFlexManager.applyToBoldText(window.decorView)

        val root = findViewById<android.view.View>(R.id.main_content) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val topInset = maxOf(systemBars.top, displayCutout.top)
            val gradientEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_TOOLBAR_GRADIENT_HEADER, false)

            if (gradientEnabled) {
                view.updatePadding(top = 0, bottom = 0)
                findViewById<Toolbar>(R.id.toolbar)?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = topInset
                }
            } else {
                view.updatePadding(top = topInset, bottom = 0)
                findViewById<Toolbar>(R.id.toolbar)?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = 0
                }
            }

            view.updatePadding(
                left  = maxOf(systemBars.left,  displayCutout.left),
                right = maxOf(systemBars.right, displayCutout.right)
            )

            insets
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            onBackPressedDispatcher.onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun attachBaseContext(newBase: Context?) {
        val base = newBase ?: return
        val dpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
        val fontScale = MmkvManager.decodeSettingsFloat(AppConfig.PREF_APP_FONT_SIZE, AppConfig.FONT_SIZE_DEFAULT)
        val dpiWrapped = if (dpi > 0) DPIController.wrapWithDpi(base, dpi) else base
        val finalContext = FontSizeController.wrapWithFontScale(dpiWrapped, fontScale)
        super.attachBaseContext(finalContext)
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode

            val dpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
            if (dpi > 0) {
                overrideConfiguration.densityDpi = dpi
            }

            val fontScale = MmkvManager.decodeSettingsFloat(AppConfig.PREF_APP_FONT_SIZE, AppConfig.FONT_SIZE_DEFAULT)
            if (fontScale > 0f) {
                overrideConfiguration.fontScale = fontScale
            }
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    protected fun addCustomDividerToRecyclerView(recyclerView: RecyclerView, context: Context?, drawableResId: Int, orientation: Int = DividerItemDecoration.VERTICAL) {
        val drawable = ContextCompat.getDrawable(context!!, drawableResId)
        requireNotNull(drawable) { "Drawable resource not found" }
        val dividerItemDecoration = CustomDividerItemDecoration(drawable, orientation)
        recyclerView.addItemDecoration(dividerItemDecoration)
    }

    protected fun setupToolbar(toolbar: Toolbar?, showHomeAsUp: Boolean = true, title: CharSequence? = null, subtitle: CharSequence? = null) {
        val tb = toolbar ?: findViewById<Toolbar?>(R.id.toolbar)
        tb?.let {
            setSupportActionBar(it)
            supportActionBar?.setDisplayHomeAsUpEnabled(showHomeAsUp)
            title?.let { t -> this.title = t }

            toolbarSubtitle = subtitle
            collapsingToolbarRef = findViewById(R.id.collapsing_toolbar)
            appBarRef = findViewById(R.id.app_bar)

            applyToolbarStyle()
        }
    }

    fun refreshToolbarStyle() {
        applyToolbarStyle()
    }

    private fun applyToolbarStyle() {
        val collapsingToolbar = collapsingToolbarRef ?: return
        val centerSubtitle = MmkvManager.decodeSettingsBool(AppConfig.PREF_TOOLBAR_CENTER_SUBTITLE_MODE, false)
        val subtitleText = if (centerSubtitle) toolbarSubtitle else null

        supportActionBar?.subtitle = null
        collapsingToolbar.subtitle = null

        if (centerSubtitle) {
            collapsingToolbar.titleCollapseMode = CollapsingToolbarLayout.TITLE_COLLAPSE_MODE_FADE
            collapsingToolbar.setExpandedTitleGravity(Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM)
        } else {
            collapsingToolbar.titleCollapseMode = CollapsingToolbarLayout.TITLE_COLLAPSE_MODE_SCALE
            collapsingToolbar.setExpandedTitleGravity(Gravity.START or Gravity.BOTTOM)
        }

        val customSubtitleView = findViewById<TextView>(R.id.custom_expanded_subtitle)
        val density = resources.displayMetrics.density

        if (!subtitleText.isNullOrEmpty()) {
            customSubtitleView?.text = subtitleText
            customSubtitleView?.visibility = View.VISIBLE

            if (customSubtitleView != null) {
                val screenWidth = resources.displayMetrics.widthPixels
                val marginPx = (48 * density).toInt()
                val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(screenWidth - marginPx, View.MeasureSpec.AT_MOST)
                val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

                customSubtitleView.measure(widthMeasureSpec, heightMeasureSpec)

                val subtitleHeight = customSubtitleView.measuredHeight
                val spacing = (32 * density).toInt()
                collapsingToolbar.expandedTitleMarginBottom = subtitleHeight + spacing
            }
        } else {
            customSubtitleView?.visibility = View.GONE
            collapsingToolbar.expandedTitleMarginBottom = (24 * density).toInt()
        }

        customSubtitleView?.gravity = if (centerSubtitle) Gravity.CENTER else Gravity.START

        collapsingToolbar.requestLayout()
        collapsingToolbar.invalidate()

        setupGradientHeaderEffect(collapsingToolbar)
    }

    private fun setupGradientHeaderEffect(collapsingToolbar: CollapsingToolbarLayout) {
        val appBar = appBarRef ?: findViewById(R.id.app_bar)
        appBarRef = appBar

        val enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_TOOLBAR_GRADIENT_HEADER, false)

        // Cache CTL's original contentScrim once, so it can be restored if the
        // gradient header is turned off again without recreating the Activity.
        if (defaultContentScrim == null) {
            defaultContentScrim = collapsingToolbar.contentScrim
        }

        if (!enabled || appBar == null) {
            gradientHeaderView?.visibility = View.GONE
            gradientOffsetListener?.let { appBar.removeOnOffsetChangedListener(it) }
            gradientOffsetListener = null
            // Restore CTL's own scrim animation for the non-gradient look.
            collapsingToolbar.contentScrim = defaultContentScrim
            return
        }

        // CollapsingToolbarLayout's built-in contentScrim fades in on its OWN
        // time-based ValueAnimator (scrimAnimationDuration), triggered once the
        // toolbar height crosses scrimVisibleHeightTrigger — completely
        // independent from the scroll-position-based alpha we drive below for
        // gradientView. On a fast fling the two curves fall out of sync and the
        // opaque scrim (drawn on top of gradientView by CTL) pops in/out
        // abruptly, which is the flicker/seam you see. Null it out so there is
        // only one source of truth (scroll fraction) driving both layers.
        collapsingToolbar.contentScrim = null

        val gradientView = gradientHeaderView ?: View(this).apply {
            id = View.NO_ID
            setBackgroundResource(R.drawable.bg_toolbar_gradient_header)
            isClickable = false
            isFocusable = false
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }.also {
            gradientHeaderView = it
        }

        if (gradientView.parent == null) {
            val lp = CollapsingToolbarLayout.LayoutParams(
                CollapsingToolbarLayout.LayoutParams.MATCH_PARENT,
                CollapsingToolbarLayout.LayoutParams.MATCH_PARENT
            ).apply {
                collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
            }
            collapsingToolbar.addView(gradientView, 0, lp)
        }

        gradientView.visibility = View.VISIBLE

        val range = appBar.totalScrollRange
        if (range > 0) {
            val currentOffset = Math.abs(appBar.top)
            gradientView.alpha = (1f - (currentOffset.toFloat() / range)).coerceIn(0f, 1f)
        } else {
            gradientView.alpha = 1f
        }

        if (gradientOffsetListener == null) {
            val listener = AppBarLayout.OnOffsetChangedListener { layout, verticalOffset ->
                val scrollRange = layout.totalScrollRange
                if (scrollRange > 0) {
                    val fraction = 1f - (-verticalOffset / scrollRange.toFloat())
                    gradientView.alpha = fraction.coerceIn(0f, 1f)
                }
            }
            appBar.addOnOffsetChangedListener(listener)
            gradientOffsetListener = listener
        }
    }

    protected fun setContentViewWithToolbar(layoutResId: Int, showHomeAsUp: Boolean = true, title: CharSequence? = null, subtitle: CharSequence? = null) {
        val base = LayoutInflater.from(this).inflate(R.layout.activity_base, null)
        val container = base.findViewById<FrameLayout>(R.id.content_container)
        LayoutInflater.from(this).inflate(layoutResId, container, true)
        super.setContentView(base)
        setupBaseToolbar(base, showHomeAsUp, title, subtitle)
    }

    protected fun setContentViewWithToolbar(childView: View, showHomeAsUp: Boolean = true, title: CharSequence? = null, subtitle: CharSequence? = null) {
        val base = LayoutInflater.from(this).inflate(R.layout.activity_base, null)
        val container = base.findViewById<FrameLayout>(R.id.content_container)
        container.addView(childView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        super.setContentView(base)
        setupBaseToolbar(base, showHomeAsUp, title, subtitle)
    }

    private fun setupBaseToolbar(baseRoot: View, showHomeAsUp: Boolean, title: CharSequence?, subtitle: CharSequence?) {
        val toolbar = baseRoot.findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp, title, subtitle)
    }

    private fun getOrCreateLoadingOverlay(): FrameLayout {
        val blurMode = resolveFallbackLoadingBlurMode()
        loadingOverlay?.let { existingOverlay ->
            if (loadingBlurMode == blurMode) return existingOverlay
            (existingOverlay.parent as? ViewGroup)?.removeView(existingOverlay)
            loadingOverlay = null
        }

        val overlay = FrameLayout(this@BaseActivity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
            elevation = 0f

            when (blurMode) {
                LoadingBlurMode.BLUR_VIEW -> {
                    val blurView = BlurView(this@BaseActivity, null).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        setBlurRadius(
                            MmkvManager.decodeSettingsInt(
                                AppConfig.PREF_BLUR_RADIUS,
                                AppConfig.DEFAULT_BLUR_RADIUS
                            ).toFloat()
                        )
                        setBlurRounds(
                            MmkvManager.decodeSettingsInt(
                                AppConfig.PREF_BLUR_ROUNDS,
                                AppConfig.DEFAULT_BLUR_ROUNDS
                            )
                        )
                        setOverlayColor(Color.argb(120, 0, 0, 0))
                    }
                    addView(blurView)
                }
                LoadingBlurMode.DIM -> setBackgroundColor(Color.argb(120, 0, 0, 0))
            }

            addLoadingIndicator(this)
            visibility = View.GONE
        }

        val decorView = window.decorView as ViewGroup
        decorView.addView(overlay)
        loadingOverlay = overlay
        loadingBlurMode = blurMode
        return overlay
    }

    private fun resolveFallbackLoadingBlurMode(): LoadingBlurMode =
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_BLUR, false)) {
            LoadingBlurMode.BLUR_VIEW
        } else {
            LoadingBlurMode.DIM
        }

    private fun addLoadingIndicator(container: FrameLayout) {
        val customLoadingView = LayoutInflater.from(this@BaseActivity)
            .inflate(R.layout.layout_custom_loading, container, false)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        container.addView(customLoadingView, params)
    }

    private fun showSystemLoadingDialog() {
        if (systemLoadingDialog?.isShowing == true) return
        dismissFallbackLoadingOverlay()
        systemLoadingDialog?.dismiss()

        val content = FrameLayout(this@BaseActivity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
            addLoadingIndicator(this)
        }
        val dialog = Dialog(this@BaseActivity).apply {
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            setContentView(content)
            show()
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                WindowBlurUtils.applyWindowBlur(this)
            }
        }
        systemLoadingDialog = dialog
    }

    private fun dismissSystemLoadingDialog() {
        systemLoadingDialog?.let { dialog ->
            try {
                if (dialog.isShowing) dialog.dismiss()
            } catch (_: Exception) {
            }
        }
        systemLoadingDialog = null
    }

    private fun dismissFallbackLoadingOverlay() {
        loadingOverlay?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
        loadingOverlay = null
        loadingBlurMode = null
    }

    protected fun showLoading() {
        runOnUiThread {
            if (WindowBlurUtils.isSystemBlurAvailable(this@BaseActivity)) {
                showSystemLoadingDialog()
            } else {
                dismissSystemLoadingDialog()
                val overlay = getOrCreateLoadingOverlay()
                if (overlay.visibility != View.VISIBLE) {
                    overlay.visibility = View.VISIBLE
                }
            }
        }
    }

    protected fun hideLoading() {
        runOnUiThread {
            dismissSystemLoadingDialog()
            loadingOverlay?.let { overlay ->
                if (overlay.visibility == View.VISIBLE) {
                    overlay.visibility = View.GONE
                }
            }
        }
    }

    protected fun isLoadingVisible(): Boolean {
        return systemLoadingDialog?.isShowing == true || loadingOverlay?.visibility == View.VISIBLE
    }

    override fun onDestroy() {
        gradientOffsetListener?.let { appBarRef?.removeOnOffsetChangedListener(it) }
        gradientOffsetListener = null
        gradientHeaderView = null
        dismissSystemLoadingDialog()
        dismissFallbackLoadingOverlay()
        super.onDestroy()
    }
}
