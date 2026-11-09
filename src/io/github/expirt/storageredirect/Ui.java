package io.github.expirt.storageredirect;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

/**
 * Material 3 / Neo Backup presentation layer adapter.
 * Modernized to use NeoTheme, Phosphor vector icons, and Neo Backup component geometry.
 */
final class Ui {

    static final int FILLED = 0, TONAL = 1, TEXT = 2, OUTLINED = 3, TEXT_ERROR = 4;
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);

    final Context ctx;
    final M3 c;
    final NeoUi neo;
    private final float density;

    Ui(Context ctx) {
        this.ctx = ctx;
        this.c = M3.of(ctx);
        this.neo = new NeoUi(ctx);
        this.density = ctx.getResources().getDisplayMetrics().density;
    }

    static void applyTheme(Activity a) {
        NeoUi.applyTheme(a);
    }

    int dp(float v) {
        return (int) (v * density + 0.5f);
    }

    /** Paints status/navigation bars matching Neo Backup surface colors. */
    void window(Activity a) {
        neo.setupWindow(a);
    }

    // ----------------------------------------------------------------- shapes
    GradientDrawable shape(int fill, float r) {
        return shape(fill, r, r, r, r);
    }

    GradientDrawable shape(int fill, float tl, float tr, float br, float bl) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadii(new float[]{dp(tl), dp(tl), dp(tr), dp(tr), dp(br), dp(br), dp(bl), dp(bl)});
        return g;
    }

    /** Rounded background with a ripple (clipped to the same shape). */
    Drawable tile(int fill, float tl, float tr, float br, float bl, int rippleBase) {
        int ripple = (rippleBase & 0x00FFFFFF) | 0x24000000;
        return new RippleDrawable(ColorStateList.valueOf(ripple),
                shape(fill, tl, tr, br, bl), shape(0xFFFFFFFF, tl, tr, br, bl));
    }

    Drawable tile(int fill, float r, int rippleBase) {
        return tile(fill, r, r, r, r, rippleBase);
    }

    void squircle(View v, final float radiusDp) {
        final float px = dp(radiusDp);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px);
            }
        });
        v.setClipToOutline(true);
    }

    // ------------------------------------------------------------------- text
    TextView text(String s, int sp, int color, boolean medium) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(medium ? MEDIUM : REGULAR);
        return t;
    }

    TextView title(String s) {
        TextView t = text(s, 24, c.onSurface, true);
        t.setLetterSpacing(-0.01f);
        return t;
    }

    TextView label(String s) {
        TextView t = text(s.toUpperCase(), 12, c.primary, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(16), dp(20), dp(16), dp(8));
        return t;
    }

    // ----------------------------------------------------------------- layout
    LinearLayout vbox() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout hbox() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** Standalone rounded card matching Neo Backup. */
    LinearLayout card(int fill) {
        LinearLayout l = vbox();
        l.setPadding(dp(18), dp(16), dp(18), dp(16));
        l.setBackground(shape(fill, 20f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        l.setLayoutParams(lp);
        return l;
    }

    /**
     * Stacks views as a segmented group with Neo Backup corner radii:
     * 20dp on outer corners, 4dp between items.
     */
    LinearLayout group(List<View> rows) {
        LinearLayout g = vbox();
        int n = rows.size();
        for (int i = 0; i < n; i++) {
            View v = rows.get(i);
            float top = (i == 0) ? 20f : 4f;
            float bottom = (i == n - 1) ? 20f : 4f;
            v.setBackground(tile(c.surfaceContainer, top, top, bottom, bottom, c.onSurface));
            v.setPadding(dp(18), dp(14), dp(18), dp(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            if (i < n - 1) lp.bottomMargin = dp(2);
            g.addView(v, lp);
        }
        return g;
    }

    // ---------------------------------------------------------------- buttons
    TextView button(String label, int kind, View.OnClickListener l) {
        return neo.button(label, kind, l);
    }

    // ------------------------------------------------------------------ input
    EditText field(String value, String hint) {
        return neo.field(value, hint);
    }

    EditText area(String value, String hint, int lines) {
        return neo.area(value, hint, lines);
    }

    Drawable outline(boolean focused) {
        return neo.outline(focused);
    }

    Switch toggle(boolean checked) {
        return neo.toggle(checked);
    }

    // ------------------------------------------------------------------ icons
    View iconTile(int icon, int bg, int fg, int sizeDp) {
        FrameLayout f = new FrameLayout(ctx);
        f.setBackground(shape(bg, sizeDp * 0.30f));
        ImageView iv = new ImageView(ctx);

        int phosphorType;
        switch (icon) {
            case Icon.FOLDER: phosphorType = Phosphor.FOLDER; break;
            case Icon.SLIDERS: phosphorType = Phosphor.SLIDERS; break;
            case Icon.CHECK: phosphorType = Phosphor.CHECK; break;
            default: phosphorType = Phosphor.WARNING; break;
        }

        int iconPx = dp(sizeDp * 0.52f);
        iv.setImageDrawable(Phosphor.get(phosphorType, fg, iconPx));
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        f.addView(iv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return f;
    }

    static final class Icon {
        static final int FOLDER = 0, SLIDERS = 1, CHECK = 2, WARNING = 3;
    }

    // ----------------------------------------------------------------- dialog
    Dialog dialog(Activity act, String title, String message, View body,
                  String positive, final Runnable onPositive, String negative) {
        return neo.dialog(act, 0, title, message, body, positive, onPositive, negative);
    }
}
