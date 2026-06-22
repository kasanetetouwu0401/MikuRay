package es.dmoral.toasty;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import androidx.annotation.CheckResult;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.v2ray.ang.R;

@SuppressLint("InflateParams")
public class Toasty {

    private static boolean tintIcon = true;
    private static boolean allowQueue = true;
    private static int toastGravity = -1;
    private static int xOffset = -1;
    private static int yOffset = -1;
    private static boolean isRTL = false;

    private static Toast lastToast = null;

    public static final int LENGTH_SHORT = Toast.LENGTH_SHORT;
    public static final int LENGTH_LONG = Toast.LENGTH_LONG;

    private Toasty() {
        // avoiding instantiation
    }

    @CheckResult
    public static Toast normal(@NonNull Context context, @StringRes int message) {
        return normal(context, context.getString(message), Toast.LENGTH_SHORT, null, false);
    }

    @CheckResult
    public static Toast normal(@NonNull Context context, @NonNull CharSequence message) {
        return normal(context, message, Toast.LENGTH_SHORT, null, false);
    }

    @CheckResult
    public static Toast normal(@NonNull Context context, @StringRes int message, Drawable icon) {
        return normal(context, context.getString(message), Toast.LENGTH_SHORT, icon, true);
    }

    @CheckResult
    public static Toast normal(@NonNull Context context, @NonNull CharSequence message, Drawable icon) {
        return normal(context, message, Toast.LENGTH_SHORT, icon, true);
    }

    @CheckResult
    public static Toast normal(@NonNull Context context, @StringRes int message, int duration) {
        return normal(context, context.getString(message), duration, null, false);
    }

    @CheckResult
    public static Toast normal(@NonNull Context context, @NonNull CharSequence message, int duration) {
        return normal(context, message, duration, null, false);
    }

    @CheckResult
    public static Toast normal(@NonNull Context context, @StringRes int message, int duration,
                               Drawable icon) {
        return normal(context, context.getString(message), duration, icon, true);
    }

    @CheckResult
    public static Toast normal(@NonNull Context context, @NonNull CharSequence message, int duration,
                               Drawable icon) {
        return normal(context, message, duration, icon, true);
    }

    @CheckResult
    public static Toast normal(@NonNull Context context, @StringRes int message, int duration,
                               Drawable icon, boolean withIcon) {
        return normal(context, context.getString(message), duration, icon, withIcon);
    }

    @CheckResult
    public static Toast normal(@NonNull Context context, @NonNull CharSequence message, int duration,
                               Drawable icon, boolean withIcon) {
        return custom(context, message, icon,
                ToastyUtils.getColorAttr(context, "colorSurfaceInverse", 0),
                ToastyUtils.getColorAttr(context, "colorOnSurfaceInverse", 0),
                duration, withIcon, true);
    }

    @CheckResult
    public static Toast warning(@NonNull Context context, @StringRes int message) {
        return warning(context, context.getString(message), Toast.LENGTH_SHORT, true);
    }

    @CheckResult
    public static Toast warning(@NonNull Context context, @NonNull CharSequence message) {
        return warning(context, message, Toast.LENGTH_SHORT, true);
    }

    @CheckResult
    public static Toast warning(@NonNull Context context, @StringRes int message, int duration) {
        return warning(context, context.getString(message), duration, true);
    }

    @CheckResult
    public static Toast warning(@NonNull Context context, @NonNull CharSequence message, int duration) {
        return warning(context, message, duration, true);
    }

    @CheckResult
    public static Toast warning(@NonNull Context context, @StringRes int message, int duration, boolean withIcon) {
        return warning(context, context.getString(message), duration, withIcon);
    }

    @CheckResult
    public static Toast warning(@NonNull Context context, @NonNull CharSequence message, int duration, boolean withIcon) {
        return custom(context, message, ToastyUtils.getDrawable(context, R.drawable.ic_warning),
                ToastyUtils.getColorAttr(context, "colorTertiary", 0),
                ToastyUtils.getColorAttr(context, "colorOnTertiary", 0),
                duration, withIcon, true);
    }

    @CheckResult
    public static Toast info(@NonNull Context context, @StringRes int message) {
        return info(context, context.getString(message), Toast.LENGTH_SHORT, true);
    }

    @CheckResult
    public static Toast info(@NonNull Context context, @NonNull CharSequence message) {
        return info(context, message, Toast.LENGTH_SHORT, true);
    }

    @CheckResult
    public static Toast info(@NonNull Context context, @StringRes int message, int duration) {
        return info(context, context.getString(message), duration, true);
    }

    @CheckResult
    public static Toast info(@NonNull Context context, @NonNull CharSequence message, int duration) {
        return info(context, message, duration, true);
    }

    @CheckResult
    public static Toast info(@NonNull Context context, @StringRes int message, int duration, boolean withIcon) {
        return info(context, context.getString(message), duration, withIcon);
    }

    @CheckResult
    public static Toast info(@NonNull Context context, @NonNull CharSequence message, int duration, boolean withIcon) {
        return custom(context, message, ToastyUtils.getDrawable(context, R.drawable.ic_about_24dp),
                ToastyUtils.getColorAttr(context, "colorSurfaceInverse", 0),
                ToastyUtils.getColorAttr(context, "colorOnSurfaceInverse", 0),
                duration, withIcon, true);
    }

    @CheckResult
    public static Toast success(@NonNull Context context, @StringRes int message) {
        return success(context, context.getString(message), Toast.LENGTH_SHORT, true);
    }

    @CheckResult
    public static Toast success(@NonNull Context context, @NonNull CharSequence message) {
        return success(context, message, Toast.LENGTH_SHORT, true);
    }

    @CheckResult
    public static Toast success(@NonNull Context context, @StringRes int message, int duration) {
        return success(context, context.getString(message), duration, true);
    }

    @CheckResult
    public static Toast success(@NonNull Context context, @NonNull CharSequence message, int duration) {
        return success(context, message, duration, true);
    }

    @CheckResult
    public static Toast success(@NonNull Context context, @StringRes int message, int duration, boolean withIcon) {
        return success(context, context.getString(message), duration, withIcon);
    }

    @CheckResult
    public static Toast success(@NonNull Context context, @NonNull CharSequence message, int duration, boolean withIcon) {
        return custom(context, message, ToastyUtils.getDrawable(context, R.drawable.ic_check_circle),
                ToastyUtils.getColorAttr(context, "colorPrimary", 0),
                ToastyUtils.getColorAttr(context, "colorOnPrimary", 0),
                duration, withIcon, true);
    }

    @CheckResult
    public static Toast error(@NonNull Context context, @StringRes int message) {
        return error(context, context.getString(message), Toast.LENGTH_SHORT, true);
    }

    @CheckResult
    public static Toast error(@NonNull Context context, @NonNull CharSequence message) {
        return error(context, message, Toast.LENGTH_SHORT, true);
    }

    @CheckResult
    public static Toast error(@NonNull Context context, @StringRes int message, int duration) {
        return error(context, context.getString(message), duration, true);
    }

    @CheckResult
    public static Toast error(@NonNull Context context, @NonNull CharSequence message, int duration) {
        return error(context, message, duration, true);
    }

    @CheckResult
    public static Toast error(@NonNull Context context, @StringRes int message, int duration, boolean withIcon) {
        return error(context, context.getString(message), duration, withIcon);
    }

    @CheckResult
    public static Toast error(@NonNull Context context, @NonNull CharSequence message, int duration, boolean withIcon) {
        return custom(context, message, ToastyUtils.getDrawable(context, R.drawable.ic_warning),
                ToastyUtils.getColorAttr(context, "colorError", 0),
                ToastyUtils.getColorAttr(context, "colorOnError", 0),
                duration, withIcon, true);
    }

    @CheckResult
    public static Toast custom(@NonNull Context context, @StringRes int message, Drawable icon,
                               int duration, boolean withIcon) {
        return custom(context, context.getString(message), icon, -1, 
                ToastyUtils.getColorAttr(context, "colorOnSurfaceInverse", 0),
                duration, withIcon, false);
    }

    @CheckResult
    public static Toast custom(@NonNull Context context, @NonNull CharSequence message, Drawable icon,
                               int duration, boolean withIcon) {
        return custom(context, message, icon, -1, 
                ToastyUtils.getColorAttr(context, "colorOnSurfaceInverse", 0),
                duration, withIcon, false);
    }

    @CheckResult
    public static Toast custom(@NonNull Context context, @StringRes int message, @DrawableRes int iconRes,
                               @ColorRes int tintColorRes, int duration,
                               boolean withIcon, boolean shouldTint) {
        return custom(context, context.getString(message), ToastyUtils.getDrawable(context, iconRes),
                ToastyUtils.getColor(context, tintColorRes), 
                ToastyUtils.getColorAttr(context, "colorOnSurfaceInverse", 0),
                duration, withIcon, shouldTint);
    }

    @CheckResult
    public static Toast custom(@NonNull Context context, @NonNull CharSequence message, @DrawableRes int iconRes,
                               @ColorRes int tintColorRes, int duration,
                               boolean withIcon, boolean shouldTint) {
        return custom(context, message, ToastyUtils.getDrawable(context, iconRes),
                ToastyUtils.getColor(context, tintColorRes), 
                ToastyUtils.getColorAttr(context, "colorOnSurfaceInverse", 0),
                duration, withIcon, shouldTint);
    }

    @CheckResult
    public static Toast custom(@NonNull Context context, @StringRes int message, Drawable icon,
                               @ColorRes int tintColorRes, int duration,
                               boolean withIcon, boolean shouldTint) {
        return custom(context, context.getString(message), icon, ToastyUtils.getColor(context, tintColorRes),
                ToastyUtils.getColorAttr(context, "colorOnSurfaceInverse", 0), duration, withIcon, shouldTint);
    }

    @CheckResult
    public static Toast custom(@NonNull Context context, @StringRes int message, Drawable icon,
                               @ColorRes int tintColorRes, @ColorRes int textColorRes, int duration,
                               boolean withIcon, boolean shouldTint) {
        return custom(context, context.getString(message), icon, ToastyUtils.getColor(context, tintColorRes),
                ToastyUtils.getColor(context, textColorRes), duration, withIcon, shouldTint);
    }

    @SuppressLint("ShowToast")
    @CheckResult
    public static Toast custom(@NonNull Context context, @NonNull CharSequence message, Drawable icon,
                               @ColorInt int tintColor, @ColorInt int textColor, int duration,
                               boolean withIcon, boolean shouldTint) {
        final Toast currentToast = Toast.makeText(context, "", duration);
        final View toastLayout = ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                .inflate(R.layout.toast_layout, null);
        final LinearLayout toastRoot = toastLayout.findViewById(R.id.toast_root);
        final ImageView toastIcon = toastLayout.findViewById(R.id.toast_icon);
        final TextView toastTextView = toastLayout.findViewById(R.id.toast_text);
        Drawable drawableFrame;

        if (shouldTint) {
            try {
                drawableFrame = ToastyUtils.tint9PatchDrawableFrame(context, tintColor);
            } catch (ClassCastException e) {
                // Safeguard against custom XML backgrounds like RippleDrawable
                drawableFrame = ToastyUtils.getDrawable(context, R.drawable.uwu_bg_sin);
                if (drawableFrame != null) {
                    drawableFrame = drawableFrame.mutate();
                    drawableFrame.setColorFilter(tintColor, android.graphics.PorterDuff.Mode.SRC_IN);
                }
            }
        } else {
            drawableFrame = ToastyUtils.getDrawable(context, R.drawable.uwu_bg_sin);
        }
        ToastyUtils.setBackground(toastLayout, drawableFrame);

        if (withIcon) {
            if (icon == null)
                throw new IllegalArgumentException("Avoid passing 'icon' as null if 'withIcon' is set to true");
            if (isRTL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                toastRoot.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            ToastyUtils.setBackground(toastIcon, tintIcon ? ToastyUtils.tintIcon(icon, textColor) : icon);
        } else {
            toastIcon.setVisibility(View.GONE);
        }

        toastTextView.setText(message);
        toastTextView.setTextColor(textColor);

        currentToast.setView(toastLayout);

        if (!allowQueue) {
            if (lastToast != null)
                lastToast.cancel();
            lastToast = currentToast;
        }

        if (toastGravity == -1) {
            int marginPx = (int) (120f * context.getResources().getDisplayMetrics().density);
            currentToast.setGravity(Gravity.BOTTOM, 0, marginPx);
        } else {
            currentToast.setGravity(
                    toastGravity,
                    xOffset == -1 ? currentToast.getXOffset() : xOffset,
                    yOffset == -1 ? currentToast.getYOffset() : yOffset
            );
        }

        return currentToast;
    }

    public static class Config {

        private boolean tintIcon = Toasty.tintIcon;
        private boolean allowQueue = true;
        private int toastGravity = Toasty.toastGravity;
        private int xOffset = Toasty.xOffset;
        private int yOffset = Toasty.yOffset;
        private boolean isRTL = false;

        private Config() {
            // avoiding instantiation
        }

        @CheckResult
        public static Config getInstance() {
            return new Config();
        }

        public static void reset() {
            Toasty.tintIcon = true;
            Toasty.allowQueue = true;
            Toasty.toastGravity = -1;
            Toasty.xOffset = -1;
            Toasty.yOffset = -1;
            Toasty.isRTL = false;
        }

        @CheckResult
        public Config tintIcon(boolean tintIcon) {
            this.tintIcon = tintIcon;
            return this;
        }

        @CheckResult
        public Config allowQueue(boolean allowQueue) {
            this.allowQueue = allowQueue;
            return this;
        }
      
        @CheckResult
        public Config setGravity(int gravity, int xOffset, int yOffset) {
            this.toastGravity = gravity;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            return this;
        }
      
        @CheckResult
        public Config setGravity(int gravity) {
            this.toastGravity = gravity;
            return this;
        }

        @CheckResult
        @Deprecated
        public Config supportDarkTheme(boolean supportDarkTheme) {
            return this;
        }
         
        public Config setRTL(boolean isRTL) {
            this.isRTL = isRTL;
            return this;
        }

        public void apply() {
            Toasty.tintIcon = tintIcon;
            Toasty.allowQueue = allowQueue;
            Toasty.toastGravity = toastGravity;
            Toasty.xOffset = xOffset;
            Toasty.yOffset = yOffset;
            Toasty.isRTL = isRTL;
        }
    }
}
