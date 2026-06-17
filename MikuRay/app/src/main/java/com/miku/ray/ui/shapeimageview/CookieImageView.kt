package com.miku.ray.ui.shapeimageview

import android.content.Context
import android.util.AttributeSet
import com.miku.ray.ui.shapeimageview.shader.ShaderHelper
import com.miku.ray.ui.shapeimageview.shader.SvgShader
import com.miku.ray.ui.shapeimageview.ShaderImageView
import com.miku.ray.R

class CookieImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ShaderImageView(context, attrs, defStyle) {

    override fun createImageViewHelper(): ShaderHelper {
        return SvgShader(R.raw.uwu_shape_cookie)
    }
}
