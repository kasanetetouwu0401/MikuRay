package com.v2ray.ang.blur3;

import androidx.annotation.ColorInt;

import com.v2ray.ang.blur3.drawable.color.BlurredBackgroundProvider;

/**
 * MikuRay-specific replacement for Telegram's BlurredBackgroundColorProviderThemed /
 * BlurredBackgroundProviderBuilder, which pulled shadow/stroke/tint colors out of
 * Telegram's Theme.ResourcesProvider (a whole separate theming engine MikuRay doesn't
 * have). MikuRay is Material You / MD3, so this just takes the tint color the caller
 * already resolved (e.g. from a theme attribute) plus a light/dark flag, and supplies
 * a subtle glass-edge stroke and drop shadow around it - the same visual role the
 * skipped Telegram presets played, with MikuRay-appropriate numbers.
 */
public class LiquidGlassColorProvider implements BlurredBackgroundProvider {

    @ColorInt
    private int backgroundColor;
    private boolean dark;

    public LiquidGlassColorProvider(@ColorInt int backgroundColor, boolean dark) {
        this.backgroundColor = backgroundColor;
        this.dark = dark;
    }

    public void setColor(@ColorInt int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public void setDark(boolean dark) {
        this.dark = dark;
    }

    @ColorInt
    @Override
    public int getBackgroundColor() {
        return backgroundColor;
    }

    @ColorInt
    @Override
    public int getShadowColor() {
        return dark ? 0x40000000 : 0x1F000000;
    }

    @ColorInt
    @Override
    public int getStrokeColorTop() {
        return dark ? 0x1AFFFFFF : 0x14FFFFFF;
    }

    @ColorInt
    @Override
    public int getStrokeColorBottom() {
        return dark ? 0x33FFFFFF : 0x1F000000;
    }

    @Override
    public float getStrokeWidthTop() {
        return Blur3Compat.dpf2(0.5f);
    }

    @Override
    public float getStrokeWidthBottom() {
        return Blur3Compat.dpf2(2f / 3f);
    }

    @Override
    public float getShadowRadius() {
        return Blur3Compat.dpf2(2f);
    }

    @Override
    public float getShadowDx() {
        return 0f;
    }

    @Override
    public float getShadowDy() {
        return Blur3Compat.dpf2(0.5f);
    }
}
