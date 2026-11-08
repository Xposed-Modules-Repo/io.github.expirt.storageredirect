package io.github.expirt.storageredirect;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
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

/** Material 3 (Expressive-style) building blocks: rounded-square tiles, grouped lists, tonal buttons. */
final class Ui {

    static final int FILLED = 0, TONAL = 1, TEXT = 2, OUTLINED = 3, TEXT_ERROR = 4;
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    final Context ctx;
    final M3 c;
    private final float density;

    Ui(Context ctx) {
        this.ctx = ctx;
        this.c = M3.of(ctx);
        this.density = ctx.getResources().getDisplayMetrics().density;
    }

    static void applyTheme(Activity a) {
        a.setTheme(M3.isNight(a) ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
    }

    int dp(float v) {
        return (int) (v * density + 0.5f);
    }

    /** Paints status/navigation bars in the surface color with matching icon colors. */
    void window(Activity a) {
        Window w = a.getWindow();
        w.setStatusBarColor(c.surface);
        w.setNavigationBarColor(c.surface);
        if (!c.night) {
            View d = w.getDecorView();
            // LIGHT_STATUS_BAR (API 23) | LIGHT_NAVIGATION_BAR (API 26, 0x10)
            d.setSystemUiVisibility(d.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | 0x10);
        }
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
        int ripple = (rippleBase & 0x00FFFFFF) | 0x33000000;
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
        if (medium) t.setTypeface(MEDIUM);
        return t;
    }

    TextView title(String s) {
        TextView t = text(s, 30, c.onSurface, true);
        t.setLineSpacing(0, 1.05f);
        return t;
    }

    TextView label(String s) {
        TextView t = text(s.toUpperCase(), 12, c.primary, true);
        t.setLetterSpacing(0.1f);
        t.setPadding(dp(16), dp(22), 0, dp(8));
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

    /** A standalone rounded container. */
    LinearLayout card(int fill) {
        LinearLayout l = vbox();
        l.setPadding(dp(20), dp(16), dp(20), dp(16));
        l.setBackground(shape(fill, 28));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(4);
        l.setLayoutParams(lp);
        return l;
    }

    /**
     * Stacks views as a segmented group: big radius on the outer corners, small radius between
     * the items, 2dp gaps - the Material 3 Expressive list look.
     */
    LinearLayout group(List<View> rows) {
        LinearLayout g = vbox();
        int n = rows.size();
        for (int i = 0; i < n; i++) {
            View v = rows.get(i);
            float top = i == 0 ? 28 : 6, bottom = i == n - 1 ? 28 : 6;
            v.setBackground(tile(c.surfaceContainer, top, top, bottom, bottom, c.onSurface));
            v.setPadding(dp(20), dp(14), dp(20), dp(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            if (i < n - 1) lp.bottomMargin = dp(2);
            g.addView(v, lp);
        }
        return g;
    }

    // ---------------------------------------------------------------- buttons
    TextView button(String label, int kind, View.OnClickListener l) {
        int fill = 0, fg;
        switch (kind) {
            case FILLED: fill = c.primary; fg = c.onPrimary; break;
            case TONAL: fill = c.secondaryContainer; fg = c.onSecondaryContainer; break;
            case TEXT_ERROR: fg = c.error; break;
            default: fg = c.primary;
        }
        TextView b = text(label, 14, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(44));
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        b.setClickable(true);
        if (kind == OUTLINED) {
            GradientDrawable g = shape(0, 16);
            g.setStroke(dp(1), c.outline);
            b.setBackground(new RippleDrawable(ColorStateList.valueOf((fg & 0x00FFFFFF) | 0x33000000), g,
                    shape(0xFFFFFFFF, 16)));
        } else {
            b.setBackground(tile(fill, 16, fg));
        }
        b.setOnClickListener(l);
        return b;
    }

    // ------------------------------------------------------------------ input
    EditText field(String value, String hint) {
        final EditText e = new EditText(ctx);
        e.setText(value);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setTextColor(c.onSurface);
        e.setHintTextColor(c.outline);
        e.setPadding(dp(16), dp(12), dp(16), dp(12));
        e.setBackground(outline(false));
        e.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean focus) {
                e.setBackground(outline(focus));
            }
        });
        return e;
    }

    /** Multi-line variant of field(). */
    EditText area(String value, String hint, int lines) {
        EditText e = field(value, hint);
        e.setSingleLine(false);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setMinLines(lines);
        e.setGravity(Gravity.TOP | Gravity.START);
        return e;
    }

    private Drawable outline(boolean focused) {
        GradientDrawable g = shape(0, 16);
        g.setStroke(dp(focused ? 2 : 1), focused ? c.primary : c.outline);
        return g;
    }

    Switch toggle(boolean checked) {
        Switch s = new Switch(ctx);
        s.setChecked(checked);
        if (Build.VERSION.SDK_INT >= 23) {      // Switch tinting needs API 23
            int[][] st = {{android.R.attr.state_checked}, {}};
            s.setThumbTintList(new ColorStateList(st, new int[]{c.onPrimary, c.outline}));
            s.setTrackTintList(new ColorStateList(st, new int[]{c.primary, c.surfaceContainerHigh}));
        }
        return s;
    }

    // ------------------------------------------------------------------ icons
    /** A rounded-square tile holding a drawn icon. */
    View iconTile(int icon, int bg, int fg, int sizeDp) {
        FrameLayout f = new FrameLayout(ctx);
        f.setBackground(shape(bg, sizeDp * 0.32f));
        ImageView iv = new ImageView(ctx);
        iv.setImageDrawable(new Icon(icon, fg));
        int pad = dp(sizeDp * 0.22f);
        iv.setPadding(pad, pad, pad, pad);
        f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return f;
    }

    static final class Icon extends Drawable {
        static final int FOLDER = 0, SLIDERS = 1, CHECK = 2, WARNING = 3;
        private final int type;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Icon(int type, int color) {
            this.type = type;
            p.setColor(color);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override public void draw(Canvas cv) {
            Rect b = getBounds();
            float u = Math.min(b.width(), b.height()) / 24f;
            cv.save();
            cv.translate(b.left, b.top);
            cv.scale(u, u);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            Path path = new Path();
            switch (type) {
                case FOLDER:
                    path.moveTo(3, 7); path.lineTo(3, 6); path.lineTo(9, 6); path.lineTo(11, 8.2f);
                    cv.drawPath(path, p);
                    cv.drawRoundRect(new RectF(3, 8, 21, 19), 2.5f, 2.5f, p);
                    break;
                case SLIDERS:
                    cv.drawLine(4, 6, 20, 6, p);
                    cv.drawLine(4, 12, 20, 12, p);
                    cv.drawLine(4, 18, 20, 18, p);
                    p.setStyle(Paint.Style.FILL);
                    cv.drawCircle(9, 6, 2.6f, p);
                    cv.drawCircle(15, 12, 2.6f, p);
                    cv.drawCircle(8, 18, 2.6f, p);
                    break;
                case CHECK:
                    p.setStrokeWidth(2.6f);
                    path.moveTo(5, 12.5f); path.lineTo(10, 17.5f); path.lineTo(19, 7);
                    cv.drawPath(path, p);
                    break;
                default: // WARNING
                    path.moveTo(12, 4); path.lineTo(21, 20); path.lineTo(3, 20); path.close();
                    cv.drawPath(path, p);
                    cv.drawLine(12, 10, 12, 14, p);
                    p.setStyle(Paint.Style.FILL);
                    cv.drawCircle(12, 17, 1.2f, p);
            }
            cv.restore();
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ----------------------------------------------------------------- dialog
    /** Material 3 dialog: 28dp surface, title, message and/or custom body, text buttons. */
    Dialog dialog(Activity act, String title, String message, View body,
                  String positive, final Runnable onPositive, String negative) {
        final Dialog d = new Dialog(act);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = vbox();
        box.setPadding(dp(24), dp(24), dp(24), dp(12));
        box.setBackground(shape(c.surfaceContainerHigh, 28));

        TextView t = text(title, 22, c.onSurface, false);
        box.addView(t);

        LinearLayout content = vbox();
        content.setPadding(0, dp(14), 0, dp(8));
        if (message != null) content.addView(text(message, 14, c.onSurfaceVariant, false));
        if (body != null) content.addView(body);
        ScrollView sv = new ScrollView(act);
        sv.addView(content);
        box.addView(sv, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = hbox();
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (negative != null)
            actions.addView(button(negative, TEXT, new View.OnClickListener() {
                @Override public void onClick(View v) { d.dismiss(); }
            }));
        if (positive != null)
            actions.addView(button(positive, TEXT, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    d.dismiss();
                    if (onPositive != null) onPositive.run();
                }
            }));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = dp(8);
        box.addView(actions, alp);

        d.setContentView(box);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        d.show();
        int max = Math.min(ctx.getResources().getDisplayMetrics().widthPixels - dp(40), dp(560));
        w.setLayout(max, ViewGroup.LayoutParams.WRAP_CONTENT);
        return d;
    }
}
