package com.v2ray.ang.blur3;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Small stand-ins for the Telegram-only helpers the original blur3 package
 * relied on (org.telegram.messenger.AndroidUtilities.dp()/dpf2()/readRes()
 * and org.telegram.ui.ActionBar.Theme.multAlpha()). None of those classes
 * exist in MikuRay, so this gives the ported drawables the same small
 * surface without pulling in Telegram's app framework.
 */
public final class Blur3Compat {
    private Blur3Compat() {
    }

    private static volatile float density = -1f;

    /**
     * Optional early hint for the cached density (e.g. called once from a
     * View that already has a Context). Safe to skip: density() below will
     * lazily fall back to Resources.getSystem() on first use.
     */
    public static void ensureInit(Context context) {
        if (density < 0f && context != null) {
            density = context.getApplicationContext().getResources().getDisplayMetrics().density;
        }
    }

    private static float density() {
        float d = density;
        if (d < 0f) {
            d = Resources.getSystem().getDisplayMetrics().density;
            density = d;
        }
        return d;
    }

    public static int dp(float value) {
        if (value == 0) return 0;
        return (int) Math.ceil(density() * value);
    }

    public static float dpf2(float value) {
        if (value == 0) return 0f;
        return density() * value;
    }

    /** Multiplies just the alpha channel of an ARGB color by {@code factor}, clamped to [0,255]. */
    public static int multAlpha(int color, float factor) {
        if (factor == 1f) return color;
        int a = Math.round(Color.alpha(color) * factor);
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** Reads a raw resource (e.g. an .agsl shader source) fully as UTF-8 text. */
    public static String readRawResource(Context context, int rawResId) {
        final StringBuilder sb = new StringBuilder();
        try (InputStream is = context.getResources().openRawResource(rawResId);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read blur3 raw resource " + rawResId, e);
        }
        return sb.toString();
    }
}
