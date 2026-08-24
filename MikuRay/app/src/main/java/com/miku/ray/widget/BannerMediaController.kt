package com.miku.ray.widget

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.miku.ray.R

/**
 * Displays image/GIF banners with Glide and video banners with a looping, muted VideoView.
 * The profile banner intentionally does not use this controller.
 */
class BannerMediaController(
    private val imageView: ImageView,
    private val defaultDrawableRes: Int
) {

    private val context: Context = imageView.context
    private val videoView: CenterCropVideoView? = createVideoView()

    private fun createVideoView(): CenterCropVideoView? {
        val parent = imageView.parent as? ViewGroup ?: return null
        parent.clipChildren = true
        parent.clipToPadding = true
        val video = CenterCropVideoView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            isClickable = false
            isFocusable = false
        }
        val imageIndex = parent.indexOfChild(imageView)
        parent.addView(video, (imageIndex + 1).coerceAtMost(parent.childCount))
        return video
    }

    fun load(uriString: String?) {
        val uri = uriString?.takeUnless { it.isBlank() }?.let(Uri::parse)
        val targetTag = uriString?.takeUnless { it.isBlank() } ?: DEFAULT_TAG
        if (imageView.tag == targetTag) return

        if (uri != null && isVideo(uri, uriString)) {
            loadVideo(uri)
        } else if (uri != null) {
            stopVideo()
            imageView.visibility = View.VISIBLE
            Glide.with(imageView)
                .load(uri)
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .error(defaultDrawableRes)
                .into(imageView)
        } else {
            stopVideo()
            imageView.visibility = View.VISIBLE
            Glide.with(imageView).clear(imageView)
            imageView.setImageResource(defaultDrawableRes)
        }
        imageView.tag = targetTag
    }

    fun hide() {
        stopVideo()
        Glide.with(imageView).clear(imageView)
        imageView.visibility = View.GONE
        imageView.setImageDrawable(null)
        imageView.tag = HIDDEN_TAG
    }

    fun resume() {
        if (videoView?.visibility == View.VISIBLE) videoView.start()
    }

    fun pause() {
        if (videoView?.visibility == View.VISIBLE) videoView.pause()
    }

    fun release() {
        stopVideo()
        Glide.with(context.applicationContext).clear(imageView)
        videoView?.stopPlayback()
        videoView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        imageView.setImageDrawable(null)
        imageView.tag = null
        imageView.setTag(CONTROLLER_TAG_KEY, null)
    }

    private fun loadVideo(uri: Uri) {
        val video = videoView
        if (video == null) {
            imageView.visibility = View.VISIBLE
            imageView.setImageResource(defaultDrawableRes)
            return
        }

        Glide.with(imageView).clear(imageView)
        imageView.visibility = View.GONE
        video.visibility = View.VISIBLE
        video.setOnPreparedListener { player ->
            player.isLooping = true
            player.setVolume(0f, 0f)
            video.setVideoSize(player.videoWidth, player.videoHeight)
            video.start()
        }
        video.setOnErrorListener { _, _, _ ->
            stopVideo()
            imageView.visibility = View.VISIBLE
            imageView.setImageResource(defaultDrawableRes)
            true
        }
        try {
            video.setVideoURI(uri)
            video.start()
        } catch (_: Exception) {
            stopVideo()
            imageView.visibility = View.VISIBLE
            imageView.setImageResource(defaultDrawableRes)
        }
    }

    private fun stopVideo() {
        videoView?.stopPlayback()
        videoView?.visibility = View.GONE
    }

    private fun isVideo(uri: Uri, uriString: String): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType?.startsWith("video/") == true) return true
        return VIDEO_EXTENSIONS.any(uriString.lowercase()::endsWith)
    }

    companion object {
        private const val DEFAULT_TAG = "DEFAULT_BANNER_MEDIA"
        private const val HIDDEN_TAG = "HIDDEN_BANNER_MEDIA"
        private val VIDEO_EXTENSIONS = listOf(".mp4", ".webm", ".mkv", ".3gp", ".mov")
        private val CONTROLLER_TAG_KEY = "banner_media_controller".hashCode()

        fun forImageView(imageView: ImageView, defaultDrawableRes: Int): BannerMediaController {
            return (imageView.getTag(CONTROLLER_TAG_KEY) as? BannerMediaController)
                ?: BannerMediaController(imageView, defaultDrawableRes).also {
                    imageView.setTag(CONTROLLER_TAG_KEY, it)
                }
        }
    }
}

