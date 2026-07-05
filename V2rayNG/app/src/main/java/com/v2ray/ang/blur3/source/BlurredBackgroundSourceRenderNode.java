package com.v2ray.ang.blur3.source;

import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.v2ray.ang.blur3.drawable.BlurredBackgroundDrawable;
import com.v2ray.ang.blur3.drawable.BlurredBackgroundDrawableRenderNode;

/**
 * Ported from Telegram's blur3 "Liquid Glass" package and adapted for MikuRay.
 *
 * Simplified vs. upstream: the generic multi-region scroll-noise suppression
 * (DownscaleScrollableNoiseSuppressor), the hash-based conditional re-record
 * skip (RenderNodeWithHash) and the weak-reference drawable registry
 * (me.vkryl ReferenceList) were all built for Telegram's arbitrarily nested,
 * fast-scrolling chat UI. MikuRay only needs to blur its own window's decor
 * content behind a dialog or a status card, so LiquidGlassBlurView drives
 * beginRecording()/endRecording() directly every frame instead.
 */
@RequiresApi(api = Build.VERSION_CODES.Q)
public class BlurredBackgroundSourceRenderNode implements BlurredBackgroundSource {
    private final BlurredBackgroundSource fallbackSource;
    private final RenderNode renderNode;

    public BlurredBackgroundSource underSource;

    public BlurredBackgroundSourceRenderNode(BlurredBackgroundSource fallbackSource) {
        this.fallbackSource = fallbackSource;

        renderNode = new RenderNode(null);
    }

    public void setSize(int width, int height) {
        renderNode.setPosition(0, 0, width, height);
    }

    public void setUnderSource(BlurredBackgroundSource underSource) {
        this.underSource = underSource;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void setBlur(float radius) {
        renderNode.setRenderEffect(radius > 0 ? RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) : null);
    }

    private boolean inRecording;
    private RecordingCanvas recordingCanvas;

    public boolean needUpdateDisplayList(int width, int height) {
        return !renderNode.hasDisplayList() || renderNode.getWidth() != width || renderNode.getHeight() != height;
    }

    public RecordingCanvas beginRecording(int width, int height) {
        if (inRecording) {
            throw new IllegalStateException();
        }

        inRecording = true;

        renderNode.setPosition(0, 0, width, height);
        recordingCanvas = renderNode.beginRecording(width, height);
        return recordingCanvas;
    }

    public void endRecording() {
        if (!inRecording) {
            throw new IllegalStateException();
        }

        renderNode.endRecording();
        inRecording = false;
        recordingCanvas = null;
    }

    public boolean isRecordingCanvas(Canvas canvas) {
        return canvas != null && canvas == recordingCanvas;
    }

    public boolean inRecording() {
        return inRecording;
    }

    @Override
    public void draw(Canvas canvas, float left, float top, float right, float bottom) {
        if (!canvas.isHardwareAccelerated()) {
            if (fallbackSource != null) {
                fallbackSource.draw(canvas, left, top, right, bottom);
            }
            return;
        }

        if (inRecording) {
            throw new IllegalStateException();
        }

        if (underSource != null) {
            underSource.draw(canvas, left, top, right, bottom);
        }
        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        canvas.drawRenderNode(renderNode);
        canvas.restore();
    }

    public BlurredBackgroundSource getFallbackSource() {
        return fallbackSource;
    }

    private Runnable onDrawablesRelativePositionChangeListener;
    public void setOnDrawablesRelativePositionChangeListener(Runnable callback) {
        onDrawablesRelativePositionChangeListener = callback;
    }

    @Override
    public void dispatchOnDrawablesRelativePositionChange() {
        if (onDrawablesRelativePositionChangeListener != null) {
            onDrawablesRelativePositionChangeListener.run();
        }
    }

    @Override
    public BlurredBackgroundDrawable createDrawable() {
        return new BlurredBackgroundDrawableRenderNode(this);
    }
}
