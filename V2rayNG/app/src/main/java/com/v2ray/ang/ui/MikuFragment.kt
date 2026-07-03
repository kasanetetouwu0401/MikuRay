package com.v2ray.ang.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.R

/**
 * Base class for the screens that used to be standalone Activities launched from
 * [com.v2ray.ang.ui.bottomsheet.MainMenuBottomSheet] (Sub setting, Routing setting,
 * Settings, Logcat, Backup/restore, About). They now live as fragments swapped into
 * MainActivity's `fragment_holder`, mirroring MikuBox's ToolbarFragment pattern.
 */
abstract class MikuFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null
    protected val binding: VB
        get() = _binding!!

    protected abstract fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = inflateBinding(inflater, container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyInsets(view)
    }

    /**
     * Whether the bottom system-bar/cutout inset should be applied as padding. Most of these
     * screens relied on BaseActivity's centralized onContentChanged() default (bottom = 0);
     * RoutingSettingActivity/SubSettingActivity opted into their own listener with the real
     * bottom inset, so those fragments override this to true.
     */
    protected open val applyBottomInset: Boolean = false

    private fun applyInsets(root: View) {
        val target = root.findViewById<View>(R.id.main_content) ?: root
        ViewCompat.setOnApplyWindowInsetsListener(target) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(
                top = maxOf(systemBars.top, displayCutout.top),
                bottom = if (applyBottomInset) maxOf(systemBars.bottom, displayCutout.bottom) else 0,
                left = maxOf(systemBars.left, displayCutout.left),
                right = maxOf(systemBars.right, displayCutout.right)
            )
            insets
        }
    }

    /**
     * Sets up a fragment-local toolbar whose navigation icon opens the main menu bottom
     * sheet again (same as MainActivity's home button), instead of a plain back arrow.
     * Closing/going back to the server list is still handled by the system back button.
     */
    protected fun setupToolbar(toolbar: MaterialToolbar?, title: CharSequence? = null) {
        toolbar ?: return
        toolbar.setNavigationIcon(R.drawable.ic_navigation_menu)
        toolbar.setNavigationOnClickListener {
            com.v2ray.ang.ui.bottomsheet.MainMenuBottomSheet().show(
                parentFragmentManager,
                com.v2ray.ang.ui.bottomsheet.MainMenuBottomSheet.TAG
            )
        }
        title?.let { toolbar.title = it }
    }

    protected fun closeThisFragment() {
        (activity as? MainActivity)?.closeMikuFragment()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
