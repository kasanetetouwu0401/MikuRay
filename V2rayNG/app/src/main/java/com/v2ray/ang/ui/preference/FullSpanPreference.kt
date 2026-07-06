package com.v2ray.ang.ui.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.StaggeredGridLayoutManager

class FullSpanPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        
        val layoutParams = holder.itemView.layoutParams
        
        if (layoutParams is StaggeredGridLayoutManager.LayoutParams) {
            layoutParams.isFullSpan = true
        }
    }
}
