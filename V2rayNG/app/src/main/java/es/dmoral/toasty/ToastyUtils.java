package es.dmoral.toasty;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.appcompat.content.res.AppCompatResources;
import android.view.View;
import com.v2ray.ang.R;

final class ToastyUtils {
    private ToastyUtils() {
    }

    static Drawable tintIcon(@NonNull Drawable drawable, @ColorInt int tintColor) {
        drawable.mutate().setColorFilter(tintColor, PorterDuff.Mode.SRC_IN);
        return drawable;
    }

    static Drawable tint9PatchDrawableFrame(@NonNull Context context, @ColorInt int tintColor) {
        Drawable toastDrawable = getDrawable(context, R.drawable.uwu_bg_sin);
        if (toastDrawable != null) {
            return tintIcon(toastDrawable, tintColor);
        }
        return null;
    }

    static void setBackground(@NonNull View view, Drawable drawable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            view.setBackground(drawable);
        else
            view.setBackgroundDrawable(drawable);
    }

    static Drawable getDrawable(@NonNull Context context, @DrawableRes int id) {
        return AppCompatResources.getDrawable(context, id);
    }

    static int getColor(@NonNull Context context, @ColorRes int color){
        return ContextCompat.getColor(context, color);
    }

    static int getColorAttr(@NonNull Context context, @NonNull String attrName, @ColorInt int fallbackColor) {
        int resId = context.getResources().getIdentifier(attrName, "attr", context.getPackageName());
        if (resId == 0) {
            return fallbackColor;
        }
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(resId, typedValue, true)) {
            return typedValue.data;
        }
        return fallbackColor;
    }
}
