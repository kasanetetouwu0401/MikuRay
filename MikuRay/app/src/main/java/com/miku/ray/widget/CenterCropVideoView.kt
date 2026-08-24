package com.miku.ray.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView
import kotlin.math.roundToInt

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
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (videoWidth <= 0 || videoHeight <= 0) {
            translationX = 0f
            translationY = 0f
            return
        }

        val measuredViewportWidth = measuredWidth
        val measuredViewportHeight = measuredHeight
        if (measuredViewportWidth <= 0 || measuredViewportHeight <= 0) return

        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
        val viewportAspect = measuredViewportWidth.toFloat() / measuredViewportHeight.toFloat()

        val croppedWidth: Int
        val croppedHeight: Int
        if (videoAspect > viewportAspect) {
            croppedWidth = (measuredViewportHeight * videoAspect).roundToInt()
            croppedHeight = measuredViewportHeight
        } else {
            croppedWidth = measuredViewportWidth
            croppedHeight = (measuredViewportWidth / videoAspect).roundToInt()
        }

        setMeasuredDimension(croppedWidth, croppedHeight)
        translationX = (measuredViewportWidth - croppedWidth) / 2f
        translationY = (measuredViewportHeight - croppedHeight) / 2f
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (videoWidth <= 0 || videoHeight <= 0) {
            translationX = 0f
            translationY = 0f
        }
    }

    override fun onDetachedFromWindow() {
        translationX = 0f
        translationY = 0f
        super.onDetachedFromWindow()
    }
}
