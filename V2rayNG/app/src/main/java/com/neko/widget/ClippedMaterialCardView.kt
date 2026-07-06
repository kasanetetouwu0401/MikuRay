package com.neko.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import com.google.android.material.card.MaterialCardView

class ClippedMaterialCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr) {

    init {
        clipToOutline = true
        clipChildren = true
        preventCornerOverlap = false
        setLayerType(View.LAYER_TYPE_NONE, null)
        outlineProvider = ViewOutlineProvider.BACKGROUND
    }
}
