package com.miku.ray.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

class CenterCropVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VideoView(context, attrs, defStyleAttr) {

    private var videoWidth = 0
    private var videoHeight = 0

    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        updateCenterCropScale()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateCenterCropScale()
    }

    override fun onDetachedFromWindow() {
        scaleX = 1f
        scaleY = 1f
        super.onDetachedFromWindow()
    }

    private fun updateCenterCropScale() {
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return

        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
        val viewAspect = width.toFloat() / height.toFloat()
        if (videoAspect > viewAspect) {
            scaleX = 1f
            scaleY = videoAspect / viewAspect
        } else {
            scaleX = viewAspect / videoAspect
            scaleY = 1f
        }
    }
}

