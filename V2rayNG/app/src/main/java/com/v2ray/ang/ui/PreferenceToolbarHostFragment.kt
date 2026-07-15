package com.v2ray.ang.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceFragmentCompat
import com.v2ray.ang.R

abstract class PreferenceToolbarHostFragment(
    private val layoutId: Int = R.layout.fragment_settings
) : ToolbarFragment() {

    protected abstract fun getTitle(): CharSequence

    protected abstract fun createPreferenceFragment(): PreferenceFragmentCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupToolbar(title = getTitle())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(layoutId, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.preference_container, createPreferenceFragment())
                .commit()
        }
    }
}
