package io.github.expirt.storageredirect;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
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
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Scroller;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

/**
 * Neo Backup UI Component Library.
 * Provides pixel-perfect implementations of Neo Backup's UI/UX components:
 * TopBar with animated search and progress indicator, Bottom Navigation Suite,
 * ExpandableBlock, Grouped Preference Cards, Action Buttons, and Bottom Sheets.
 */
public final class NeoUi {

    public static final int FILLED = 0;
    public static final int TONAL = 1;
    public static final int OUTLINED = 2;
    public static final int TEXT = 3;
    public static final int TEXT_ERROR = 4;

    public static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);

    public final Context ctx;
    public final NeoTheme c;
    private final float density;

    public NeoUi(Context ctx) {
        this.ctx = ctx;
        this.c = NeoTheme.of(ctx);
        this.density = ctx.getResources().getDisplayMetrics().density;
    }

    public static void applyTheme(Activity a) {
        boolean night = NeoTheme.isNight(a);
        a.setTheme(night ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
    }

    public int dp(float v) {
        return (int) (v * density + 0.5f);
    }

    public void setupWindow(Activity a) {
        Window w = a.getWindow();
        w.setStatusBarColor(c.surface);
        w.setNavigationBarColor(c.surfaceContainerLow);
        if (!c.night) {
            View d = w.getDecorView();
            d.setSystemUiVisibility(d.getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | 0x10); // View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    // ------------------------------------------------------------- Shapes
    public GradientDrawable shape(int fill, float r) {
        return shape(fill, r, r, r, r);
    }

    public GradientDrawable shape(int fill, float tl, float tr, float br, float bl) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadii(new float[]{dp(tl), dp(tl), dp(tr), dp(tr), dp(br), dp(br), dp(bl), dp(bl)});
        return g;
    }

    public Drawable tile(int fill, float r, int rippleColor) {
        return tile(fill, r, r, r, r, rippleColor);
    }

    public Drawable tile(int fill, float tl, float tr, float br, float bl, int rippleColor) {
        int rippleAlpha = (rippleColor & 0x00FFFFFF) | 0x24000000;
        return new RippleDrawable(ColorStateList.valueOf(rippleAlpha),
                shape(fill, tl, tr, br, bl), shape(0xFFFFFFFF, tl, tr, br, bl));
    }

    public void squircle(View v, final float radiusDp) {
        final float px = dp(radiusDp);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px);
            }
        });
        v.setClipToOutline(true);
    }

    /**
     * Tactile scale feedback animation on press matching Neo Backup cards and buttons.
     */
    public void attachTouchFeedback(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    view.animate().scaleX(0.975f).scaleY(0.975f).setDuration(110)
                            .setInterpolator(new DecelerateInterpolator()).start();
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160)
                            .setInterpolator(new OvershootInterpolator(1.3f)).start();
                }
                return false;
            }
        });
    }

    // --------------------------------------------------------------- Text
    public TextView text(String s, int sp, int color, boolean medium) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(medium ? MEDIUM : REGULAR);
        return t;
    }

    public TextView titleLarge(String s) {
        TextView t = text(s, 22, c.onSurface, true);
        t.setLetterSpacing(-0.01f);
        return t;
    }

    public TextView sectionLabel(String s) {
        TextView t = text(s.toUpperCase(), 12, c.primary, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(16), dp(20), dp(16), dp(8));
        return t;
    }

    // ------------------------------------------------------------- Layout
    public LinearLayout vbox() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public LinearLayout hbox() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public LinearLayout card(int fill, float radiusDp) {
        LinearLayout l = vbox();
        l.setPadding(dp(18), dp(16), dp(18), dp(16));
        l.setBackground(shape(fill, radiusDp));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        l.setLayoutParams(lp);
        return l;
    }

    public LinearLayout card(int fill) {
        return card(fill, 20f);
    }

    /**
     * Grouped preference card container matching Neo Backup's BasePreference shapes.
     * Top item has 20dp top corners, intermediate items have 4dp corners, bottom has 20dp bottom corners.
     */
    public LinearLayout group(List<View> rows) {
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

    // ------------------------------------------------------------ Buttons
    public TextView button(String label, int kind, View.OnClickListener l) {
        return buttonWithIcon(label, -1, kind, l);
    }

    public TextView buttonWithIcon(String label, int iconType, int kind, View.OnClickListener l) {
        int fill = 0, fg;
        switch (kind) {
            case FILLED:
                fill = c.primary; fg = c.onPrimary; break;
            case TONAL:
                fill = c.primaryContainer; fg = c.onPrimaryContainer; break;
            case TEXT_ERROR:
                fg = c.error; break;
            default: // TEXT, OUTLINED
                fg = c.primary; break;
        }

        TextView b = text(label, 14, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(44));
        b.setPadding(dp(20), dp(10), dp(20), dp(10));
        b.setClickable(true);

        if (iconType > 0) {
            Drawable icon = Phosphor.get(iconType, fg, dp(18));
            b.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
            b.setCompoundDrawablePadding(dp(8));
        }

        if (kind == OUTLINED) {
            GradientDrawable g = shape(0, 22);
            g.setStroke(dp(1), c.outline);
            b.setBackground(new RippleDrawable(ColorStateList.valueOf((fg & 0x00FFFFFF) | 0x24000000),
                    g, shape(0xFFFFFFFF, 22)));
        } else if (kind == TEXT || kind == TEXT_ERROR) {
            b.setBackground(tile(0, 22, fg));
        } else {
            b.setBackground(tile(fill, 22, fg));
        }
        b.setOnClickListener(l);
        return b;
    }

    /**
     * Circular or rounded pill icon button matching Neo Backup's RoundButton.
     */
    public FrameLayout roundButton(int iconType, int color, int bgColor, boolean filled, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(ctx);
        int size = dp(40);
        f.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        if (filled) {
            f.setBackground(tile(bgColor, 20f, color));
        } else {
            f.setBackground(tile(0, 20f, color));
        }
        ImageView iv = new ImageView(ctx);
        iv.setImageDrawable(Phosphor.get(iconType, color, dp(22)));
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        f.addView(iv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        f.setClickable(true);
        f.setOnClickListener(l);
        return f;
    }

    public FrameLayout roundButton(int iconType, View.OnClickListener l) {
        return roundButton(iconType, c.onSurface, c.surfaceContainerHigh, false, l);
    }

    /**
     * Status / Pill badge chip (e.g. "Custom", "Active").
     */
    public TextView chip(String text, int bg, int fg) {
        TextView t = text(text, 11, fg, true);
        t.setBackground(shape(bg, 12));
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    // ------------------------------------------------------------- Inputs
    public EditText field(String value, String hint) {
        final EditText e = new EditText(ctx);
        e.setText(value);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setTextColor(c.onSurface);
        e.setHintTextColor(c.outline);
        e.setPadding(dp(16), dp(12), dp(16), dp(12));
        e.setBackground(outline(false));
        e.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean focus) {
                e.setBackground(outline(focus));
            }
        });
        return e;
    }

    public EditText area(String value, String hint, int lines) {
        EditText e = field(value, hint);
        e.setSingleLine(false);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setMinLines(lines);
        e.setGravity(Gravity.TOP | Gravity.START);
        return e;
    }

    public Drawable outline(boolean focused) {
        GradientDrawable g = shape(c.surfaceContainerLow, 16);
        g.setStroke(dp(focused ? 2 : 1), focused ? c.primary : c.outlineVariant);
        return g;
    }

    public Switch toggle(boolean checked) {
        Switch s = new Switch(ctx);
        s.setChecked(checked);
        if (Build.VERSION.SDK_INT >= 23) {
            int[][] st = {{android.R.attr.state_checked}, {}};
            s.setThumbTintList(new ColorStateList(st, new int[]{c.onPrimary, c.outline}));
            s.setTrackTintList(new ColorStateList(st, new int[]{c.primary, c.surfaceContainerHighest}));
        }
        return s;
    }

    // ------------------------------------------------------- ExpandableBlock
    /**
     * Expandable section container matching Neo Backup's ExpandableBlock.
     */
    public static final class ExpandableBlock {
        public final LinearLayout root;
        public final LinearLayout content;
        private final ImageView caret;
        private final NeoUi ui;
        private boolean expanded;

        public ExpandableBlock(final NeoUi ui, String heading, int iconType, boolean preExpanded) {
            this.ui = ui;
            this.expanded = preExpanded;

            root = ui.vbox();
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.bottomMargin = ui.dp(12);
            root.setLayoutParams(rlp);
            root.setBackground(ui.shape(preExpanded ? ui.c.surfaceContainerHigh : ui.c.surfaceContainer, 20f));

            LinearLayout header = ui.hbox();
            header.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
            header.setClickable(true);

            if (iconType > 0) {
                ImageView iv = new ImageView(ui.ctx);
                iv.setImageDrawable(Phosphor.get(iconType, ui.c.primary, ui.dp(22)));
                header.addView(iv, new LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)));
                View space = new View(ui.ctx);
                header.addView(space, new LinearLayout.LayoutParams(ui.dp(12), 1));
            }

            TextView title = ui.text(heading, 15, ui.c.onSurface, true);
            header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

            caret = new ImageView(ui.ctx);
            caret.setImageDrawable(Phosphor.get(Phosphor.CARET_UP_DOWN, ui.c.onSurfaceVariant, ui.dp(20)));
            caret.setRotation(preExpanded ? 180f : 0f);
            header.addView(caret, new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)));
            root.addView(header);

            content = ui.vbox();
            content.setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(16));
            content.setVisibility(preExpanded ? View.VISIBLE : View.GONE);
            root.addView(content);

            header.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggle();
                }
            });
        }

        public void toggle() {
            setExpanded(!expanded, true);
        }

        public void setExpanded(boolean exp) {
            setExpanded(exp, true);
        }

        public void setExpanded(final boolean exp, boolean animate) {
            if (this.expanded == exp) return;
            this.expanded = exp;

            if (!animate) {
                caret.setRotation(exp ? 180f : 0f);
                root.setBackground(ui.shape(exp ? ui.c.surfaceContainerHigh : ui.c.surfaceContainer, 20f));
                content.setVisibility(exp ? View.VISIBLE : View.GONE);
                return;
            }

            // Neo Backup Caret rotation
            caret.animate()
                    .rotation(exp ? 180f : 0f)
                    .setDuration(260)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .start();

            // Neo Backup Surface color transition
            int fromColor = exp ? ui.c.surfaceContainer : ui.c.surfaceContainerHigh;
            int toColor = exp ? ui.c.surfaceContainerHigh : ui.c.surfaceContainer;
            ValueAnimator bgAnim = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
            bgAnim.setDuration(260);
            bgAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    root.setBackground(ui.shape((Integer) a.getAnimatedValue(), 20f));
                }
            });
            bgAnim.start();

            // Neo Backup animateContentSize: Height & alpha animation
            if (exp) {
                content.setVisibility(View.VISIBLE);
                content.setAlpha(0f);
                int width = root.getWidth();
                if (width <= 0) width = ui.ctx.getResources().getDisplayMetrics().widthPixels - ui.dp(32);
                content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int targetHeight = content.getMeasuredHeight();
                if (targetHeight <= 0) targetHeight = ui.dp(220);

                content.getLayoutParams().height = 0;
                content.requestLayout();

                ValueAnimator hAnim = ValueAnimator.ofInt(0, targetHeight);
                hAnim.setDuration(280);
                hAnim.setInterpolator(new DecelerateInterpolator(1.8f));
                hAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator a) {
                        content.getLayoutParams().height = (Integer) a.getAnimatedValue();
                        content.requestLayout();
                    }
                });
                hAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator a) {
                        content.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        content.requestLayout();
                    }
                });
                hAnim.start();
                content.animate().alpha(1f).setDuration(220).setStartDelay(50).start();
            } else {
                int initialHeight = content.getHeight();
                content.animate().alpha(0f).setDuration(150).start();

                ValueAnimator hAnim = ValueAnimator.ofInt(initialHeight, 0);
                hAnim.setDuration(240);
                hAnim.setInterpolator(new DecelerateInterpolator(1.8f));
                hAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator a) {
                        content.getLayoutParams().height = (Integer) a.getAnimatedValue();
                        content.requestLayout();
                    }
                });
                hAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator a) {
                        content.setVisibility(View.GONE);
                        content.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        content.setAlpha(1f);
                    }
                });
                hAnim.start();
            }
        }

        public boolean isExpanded() {
            return expanded;
        }
    }

    public ExpandableBlock expandableBlock(String heading, int iconType, boolean preExpanded) {
        return new ExpandableBlock(this, heading, iconType, preExpanded);
    }

    // ------------------------------------------------------------- TopBar
    /**
     * Main TopBar matching Neo Backup's MainTopBar:
     * Title, live status indicator, animated search expand/collapse, refresh button.
     */
    public static final class TopBar {
        public final LinearLayout root;
        public final TextView titleView;
        public final LinearLayout contentRow;
        public final LinearLayout actionsHolder;
        public final FrameLayout searchBox;
        public final EditText searchInput;
        public final ProgressBar progressIndicator;
        private final NeoUi ui;
        private final ImageView refreshIcon;
        private ValueAnimator spinAnim;

        public TopBar(final NeoUi ui, String titleText, final SearchListener searchListener,
                      final Runnable onRefresh, final Runnable onSettings) {
            this.ui = ui;
            root = ui.vbox();
            root.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            root.setBackgroundColor(ui.c.surface);

            // Action row
            FrameLayout topRow = new FrameLayout(ui.ctx);
            topRow.setLayoutParams(new LinearLayout.LayoutParams(-1, ui.dp(68)));
            topRow.setPadding(ui.dp(16), ui.dp(8), ui.dp(12), ui.dp(4));

            // Default content: Title + Actions
            contentRow = ui.hbox();
            contentRow.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

            titleView = ui.text(titleText, 24, ui.c.onSurface, true);
            titleView.setLetterSpacing(-0.01f);
            contentRow.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

            actionsHolder = ui.hbox();
            if (searchListener != null) {
                FrameLayout btnSearch = ui.roundButton(Phosphor.SEARCH, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setSearchVisible(true);
                    }
                });
                actionsHolder.addView(btnSearch);
            }

            if (onRefresh != null) {
                FrameLayout btnRefresh = new FrameLayout(ui.ctx);
                int size = ui.dp(40);
                btnRefresh.setLayoutParams(new LinearLayout.LayoutParams(size, size));
                btnRefresh.setBackground(ui.tile(0, 20f, ui.c.onSurface));

                refreshIcon = new ImageView(ui.ctx);
                refreshIcon.setImageDrawable(Phosphor.get(Phosphor.REFRESH, ui.c.onSurface, ui.dp(22)));
                refreshIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                btnRefresh.addView(refreshIcon, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
                btnRefresh.setClickable(true);
                btnRefresh.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        spinRefresh();
                        onRefresh.run();
                    }
                });
                actionsHolder.addView(btnRefresh);
            } else {
                refreshIcon = null;
            }

            if (onSettings != null) {
                FrameLayout btnSettings = ui.roundButton(Phosphor.GEAR, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onSettings.run();
                    }
                });
                actionsHolder.addView(btnSettings);
            }

            contentRow.addView(actionsHolder);
            topRow.addView(contentRow);

            // Search overlay (WideSearchField)
            searchBox = new FrameLayout(ui.ctx);
            searchBox.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
            searchBox.setVisibility(View.GONE);

            LinearLayout searchInner = ui.hbox();
            searchInner.setBackground(ui.shape(ui.c.surfaceContainerHigh, 24f));
            searchInner.setPadding(ui.dp(14), 0, ui.dp(8), 0);
            searchInner.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

            ImageView searchMag = new ImageView(ui.ctx);
            searchMag.setImageDrawable(Phosphor.get(Phosphor.SEARCH, ui.c.onSurfaceVariant, ui.dp(20)));
            searchInner.addView(searchMag, new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)));

            searchInput = new EditText(ui.ctx);
            searchInput.setHint("Search apps\u2026");
            searchInput.setHintTextColor(ui.c.outline);
            searchInput.setTextColor(ui.c.onSurface);
            searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            searchInput.setBackgroundColor(0);
            searchInput.setSingleLine(true);
            searchInput.setPadding(ui.dp(12), 0, ui.dp(12), 0);
            searchInner.addView(searchInput, new LinearLayout.LayoutParams(0, -1, 1f));

            FrameLayout btnClear = ui.roundButton(Phosphor.X, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (searchInput.getText().length() > 0) {
                        searchInput.setText("");
                    } else {
                        setSearchVisible(false);
                        if (searchListener != null) searchListener.onQuery("");
                    }
                }
            });
            searchInner.addView(btnClear);

            searchBox.addView(searchInner);
            topRow.addView(searchBox);

            root.addView(topRow);

            if (searchListener != null) {
                searchInput.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override
                    public void afterTextChanged(Editable s) {
                        searchListener.onQuery(s.toString().trim());
                    }
                });
            }

            // TopBar linear progress indicator
            progressIndicator = new ProgressBar(ui.ctx, null, android.R.attr.progressBarStyleHorizontal);
            progressIndicator.setIndeterminate(true);
            progressIndicator.setVisibility(View.GONE);
            progressIndicator.setLayoutParams(new LinearLayout.LayoutParams(-1, ui.dp(3)));
            root.addView(progressIndicator);
        }

        public void setSearchVisible(final boolean visible) {
            if (visible) {
                searchBox.setVisibility(View.VISIBLE);
                searchBox.setAlpha(0f);
                searchBox.setTranslationX(ui.dp(80));
                searchBox.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setDuration(260)
                        .setInterpolator(new DecelerateInterpolator(1.8f))
                        .start();

                contentRow.animate()
                        .alpha(0f)
                        .translationX(-ui.dp(40))
                        .setDuration(200)
                        .setInterpolator(new DecelerateInterpolator(1.8f))
                        .withEndAction(new Runnable() {
                            @Override public void run() { contentRow.setVisibility(View.INVISIBLE); }
                        })
                        .start();

                searchInput.requestFocus();
                searchInput.post(new Runnable() {
                    @Override
                    public void run() {
                        InputMethodManager imm = (InputMethodManager) ui.ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
                    }
                });
            } else {
                InputMethodManager imm = (InputMethodManager) ui.ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);

                searchBox.animate()
                        .alpha(0f)
                        .translationX(ui.dp(80))
                        .setDuration(200)
                        .setInterpolator(new DecelerateInterpolator(1.8f))
                        .withEndAction(new Runnable() {
                            @Override public void run() { searchBox.setVisibility(View.GONE); }
                        })
                        .start();

                contentRow.setVisibility(View.VISIBLE);
                contentRow.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setDuration(260)
                        .setInterpolator(new DecelerateInterpolator(1.8f))
                        .start();

                searchInput.clearFocus();
            }
        }

        public void setProgress(boolean busy) {
            progressIndicator.setVisibility(busy ? View.VISIBLE : View.GONE);
        }

        public void spinRefresh() {
            if (refreshIcon == null) return;
            if (spinAnim != null && spinAnim.isRunning()) return;
            spinAnim = ValueAnimator.ofFloat(0f, 360f);
            spinAnim.setDuration(700);
            spinAnim.setInterpolator(new DecelerateInterpolator());
            spinAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    refreshIcon.setRotation((Float) a.getAnimatedValue());
                }
            });
            spinAnim.start();
        }
    }

    public interface SearchListener {
        void onQuery(String query);
    }

    public TopBar topBar(String title, SearchListener search, Runnable onRefresh, Runnable onSettings) {
        return new TopBar(this, title, search, onRefresh, onSettings);
    }

    // ----------------------------------------------------- Bottom Navigation
    /**
     * Bottom Navigation Bar matching Neo Backup's NeoNavigationSuiteScaffold.
     */
    public static final class BottomNav {
        public final LinearLayout root;
        private final NeoUi ui;
        private final FrameLayout[] tabViews;
        private final ImageView[] iconViews;
        private final TextView[] labelViews;
        private final View[] pillViews;
        private int selectedIndex = 0;
        private OnTabSelectedListener listener;

        public interface OnTabSelectedListener {
            void onTabSelected(int index);
        }

        public BottomNav(final NeoUi ui, String[] labels, int[] icons, OnTabSelectedListener l) {
            this.ui = ui;
            this.listener = l;
            int n = labels.length;
            tabViews = new FrameLayout[n];
            iconViews = new ImageView[n];
            labelViews = new TextView[n];
            pillViews = new View[n];

            root = ui.hbox();
            root.setLayoutParams(new LinearLayout.LayoutParams(-1, ui.dp(72)));
            root.setBackgroundColor(ui.c.surfaceContainerLow);
            root.setGravity(Gravity.CENTER);

            for (int i = 0; i < n; i++) {
                final int idx = i;
                FrameLayout tab = new FrameLayout(ui.ctx);
                tab.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));
                tab.setClickable(true);

                LinearLayout col = ui.vbox();
                col.setGravity(Gravity.CENTER);
                col.setLayoutParams(new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

                // Pill container for icon
                FrameLayout pill = new FrameLayout(ui.ctx);
                pill.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(56), ui.dp(30)));
                pill.setBackground(ui.shape(idx == 0 ? ui.c.primaryContainer : 0, 16f));

                ImageView iv = new ImageView(ui.ctx);
                iv.setImageDrawable(Phosphor.get(icons[i], idx == 0 ? ui.c.onPrimaryContainer : ui.c.onSurfaceVariant, ui.dp(22)));
                iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                pill.addView(iv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
                col.addView(pill);

                TextView tv = ui.text(labels[i], 12, idx == 0 ? ui.c.primary : ui.c.onSurfaceVariant, idx == 0);
                tv.setPadding(0, ui.dp(4), 0, 0);
                col.addView(tv);

                tab.addView(col);
                tab.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        select(idx);
                        if (listener != null) listener.onTabSelected(idx);
                    }
                });

                tabViews[i] = tab;
                iconViews[i] = iv;
                labelViews[i] = tv;
                pillViews[i] = pill;
                root.addView(tab);
            }
        }

        public void select(int index) {
            if (index == selectedIndex) return;
            final int prevIndex = selectedIndex;
            selectedIndex = index;
            for (int i = 0; i < tabViews.length; i++) {
                final boolean sel = (i == index);
                final boolean wasSel = (i == prevIndex);
                final int idx = i;
                final int iconType = (iconViews[idx].getTag() instanceof Integer)
                        ? (Integer) iconViews[idx].getTag() : Phosphor.HOUSE;

                if (sel) {
                    // Pill bounce scale animation matching Neo NavigationSuite
                    pillViews[idx].setScaleX(0.85f);
                    pillViews[idx].setScaleY(0.85f);
                    pillViews[idx].animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(280)
                            .setInterpolator(new OvershootInterpolator(1.4f))
                            .start();

                    // Pill container background color transition
                    ValueAnimator bgAnim = ValueAnimator.ofObject(new ArgbEvaluator(), 0, ui.c.primaryContainer);
                    bgAnim.setDuration(240);
                    bgAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override public void onAnimationUpdate(ValueAnimator a) {
                            pillViews[idx].setBackground(ui.shape((Integer) a.getAnimatedValue(), 16f));
                        }
                    });
                    bgAnim.start();

                    // Label bounce & color
                    labelViews[idx].setTranslationY(ui.dp(2));
                    labelViews[idx].animate().translationY(0f).setDuration(240).setInterpolator(new OvershootInterpolator(1.2f)).start();
                    labelViews[idx].setTextColor(ui.c.primary);
                    labelViews[idx].setTypeface(MEDIUM);

                    iconViews[idx].setImageDrawable(Phosphor.get(iconType, ui.c.onPrimaryContainer, ui.dp(22)));
                } else if (wasSel) {
                    // Unselect animation
                    ValueAnimator bgAnim = ValueAnimator.ofObject(new ArgbEvaluator(), ui.c.primaryContainer, 0);
                    bgAnim.setDuration(200);
                    bgAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override public void onAnimationUpdate(ValueAnimator a) {
                            pillViews[idx].setBackground(ui.shape((Integer) a.getAnimatedValue(), 16f));
                        }
                    });
                    bgAnim.start();

                    pillViews[idx].animate().scaleX(1.0f).scaleY(1.0f).setDuration(180).start();
                    labelViews[idx].setTextColor(ui.c.onSurfaceVariant);
                    labelViews[idx].setTypeface(REGULAR);

                    iconViews[idx].setImageDrawable(Phosphor.get(iconType, ui.c.onSurfaceVariant, ui.dp(22)));
                } else {
                    pillViews[idx].setBackground(ui.shape(0, 16f));
                    labelViews[idx].setTextColor(ui.c.onSurfaceVariant);
                    labelViews[idx].setTypeface(REGULAR);
                    iconViews[idx].setImageDrawable(Phosphor.get(iconType, ui.c.onSurfaceVariant, ui.dp(22)));
                }
            }
        }

        public void setIconTag(int index, int iconType) {
            iconViews[index].setTag(iconType);
            boolean sel = (index == selectedIndex);
            iconViews[index].setImageDrawable(Phosphor.get(iconType,
                    sel ? ui.c.onPrimaryContainer : ui.c.onSurfaceVariant, ui.dp(22)));
        }
    }

    public BottomNav bottomNav(String[] labels, int[] icons, BottomNav.OnTabSelectedListener l) {
        BottomNav nav = new BottomNav(this, labels, icons, l);
        for (int i = 0; i < icons.length; i++) {
            nav.setIconTag(i, icons[i]);
        }
        return nav;
    }

    // --------------------------------------------------------- SwipePager
    /**
     * Horizontal Swipe Paging container matching Neo Backup's HorizontalPager.
     * Supports touch swipe gestures between tabs with inertia, boundary resistance,
     * smooth animated snapping, and conflict-free nested vertical scroll handling.
     */
    public static final class SwipePager extends ViewGroup {

        public interface OnPageChangeListener {
            void onPageSelected(int position);
        }

        private final Scroller scroller;
        private final int touchSlop;
        private final int minFlingVelocity;
        private final int maxFlingVelocity;

        private float initialX, initialY;
        private float lastX;
        private boolean isBeingDragged;
        private VelocityTracker velocityTracker;
        private int currentPage = 0;
        private boolean firstLayout = true;
        private OnPageChangeListener pageChangeListener;

        public SwipePager(Context context) {
            super(context);
            setWillNotDraw(false);
            ViewConfiguration vc = ViewConfiguration.get(context);
            touchSlop = vc.getScaledTouchSlop();
            minFlingVelocity = vc.getScaledMinimumFlingVelocity();
            maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
            scroller = new Scroller(context, new DecelerateInterpolator(1.6f));
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            // Do not allow child views (ListView or ScrollView) to prevent horizontal swipe paging
        }

        public void setOnPageChangeListener(OnPageChangeListener listener) {
            this.pageChangeListener = listener;
        }

        public int getCurrentPage() {
            return currentPage;
        }

        public void smoothScrollToPage(int page) {
            page = Math.max(0, Math.min(getChildCount() - 1, page));
            int width = getWidth();
            if (width <= 0) {
                currentPage = page;
                return;
            }
            int targetX = page * width;
            int dx = targetX - getScrollX();
            int duration = Math.min(400, Math.max(200, Math.abs(dx) * 320 / width));

            scroller.startScroll(getScrollX(), 0, dx, 0, duration);
            postInvalidateOnAnimation();

            if (page != currentPage) {
                currentPage = page;
                if (pageChangeListener != null) {
                    pageChangeListener.onPageSelected(page);
                }
            }
        }

        public void setCurrentPage(int page) {
            page = Math.max(0, Math.min(getChildCount() - 1, page));
            currentPage = page;
            int width = getWidth();
            if (width > 0) {
                scrollTo(page * width, 0);
            }
            if (pageChangeListener != null) {
                pageChangeListener.onPageSelected(page);
            }
        }

        @Override
        public void computeScroll() {
            if (scroller.computeScrollOffset()) {
                scrollTo(scroller.getCurrX(), scroller.getCurrY());
                postInvalidateOnAnimation();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            setMeasuredDimension(width, height);

            int childWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            int childHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            int count = getChildCount();
            for (int i = 0; i < count; i++) {
                getChildAt(i).measure(childWidthSpec, childHeightSpec);
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l;
            int height = b - t;
            int count = getChildCount();
            for (int i = 0; i < count; i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != GONE) {
                    child.layout(i * width, 0, (i + 1) * width, height);
                }
            }
            if (firstLayout && width > 0) {
                firstLayout = false;
                scrollTo(currentPage * width, 0);
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                isBeingDragged = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                return false;
            }

            if (action != MotionEvent.ACTION_DOWN && isBeingDragged) {
                return true;
            }

            float x = ev.getX();
            float y = ev.getY();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    lastX = initialX = x;
                    initialY = y;
                    isBeingDragged = !scroller.isFinished();
                    if (isBeingDragged) {
                        scroller.abortAnimation();
                    }
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    } else {
                        velocityTracker.clear();
                    }
                    velocityTracker.addMovement(ev);
                    break;

                case MotionEvent.ACTION_MOVE:
                    float dx = x - initialX;
                    float dy = y - initialY;
                    float absDx = Math.abs(dx);
                    float absDy = Math.abs(dy);

                    if (absDx > touchSlop && absDx > absDy) {
                        boolean canScrollLeft = dx < 0 && currentPage < getChildCount() - 1;
                        boolean canScrollRight = dx > 0 && currentPage > 0;
                        if (canScrollLeft || canScrollRight) {
                            isBeingDragged = true;
                            lastX = x;
                            ViewParent parent = getParent();
                            if (parent != null) {
                                parent.requestDisallowInterceptTouchEvent(true);
                            }
                        }
                    }
                    if (isBeingDragged && velocityTracker != null) {
                        velocityTracker.addMovement(ev);
                    }
                    break;
            }
            return isBeingDragged;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }
            velocityTracker.addMovement(ev);

            int action = ev.getActionMasked();
            float x = ev.getX();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    if (!scroller.isFinished()) {
                        scroller.abortAnimation();
                    }
                    lastX = initialX = x;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (!isBeingDragged) {
                        float dx = x - initialX;
                        float dy = ev.getY() - initialY;
                        if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                            isBeingDragged = true;
                            lastX = x;
                            ViewParent parent = getParent();
                            if (parent != null) {
                                parent.requestDisallowInterceptTouchEvent(true);
                            }
                        }
                    }
                    if (isBeingDragged) {
                        float deltaX = lastX - x;
                        lastX = x;

                        int maxScroll = Math.max(0, (getChildCount() - 1) * getWidth());
                        float currentScrollX = getScrollX();
                        float newScrollX = currentScrollX + deltaX;

                        if (newScrollX < 0) {
                            newScrollX = currentScrollX + deltaX * 0.35f;
                        } else if (newScrollX > maxScroll) {
                            newScrollX = currentScrollX + deltaX * 0.35f;
                        }

                        scrollTo((int) newScrollX, 0);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isBeingDragged) {
                        isBeingDragged = false;
                        velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                        float vx = velocityTracker.getXVelocity();
                        settlePage(vx);
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                    break;
            }
            return true;
        }

        private void settlePage(float vx) {
            int width = getWidth();
            if (width <= 0) return;

            int scrollX = getScrollX();
            int currentTarget = currentPage * width;
            int deltaScrolled = scrollX - currentTarget;
            int targetPage = currentPage;

            if (Math.abs(vx) > minFlingVelocity) {
                if (vx < 0 && currentPage < getChildCount() - 1) {
                    targetPage = currentPage + 1;
                } else if (vx > 0 && currentPage > 0) {
                    targetPage = currentPage - 1;
                }
            } else if (Math.abs(deltaScrolled) > width / 5) {
                if (deltaScrolled > 0 && currentPage < getChildCount() - 1) {
                    targetPage = currentPage + 1;
                } else if (deltaScrolled < 0 && currentPage > 0) {
                    targetPage = currentPage - 1;
                }
            } else {
                targetPage = (scrollX + width / 2) / width;
                targetPage = Math.max(0, Math.min(getChildCount() - 1, targetPage));
            }

            smoothScrollToPage(targetPage);
        }
    }

    // -------------------------------------------------------- Bottom Sheet
    /**
     * Neo Backup styled bottom sheet modal dialog with 28dp top radius and drag handle.
     */
    public Dialog bottomSheet(Activity act, String title, View body, String positive,
                              final Runnable onPositive, String negative) {
        final Dialog d = new Dialog(act);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = vbox();
        box.setPadding(dp(20), dp(12), dp(20), dp(24));
        box.setBackground(shape(c.surfaceContainerHigh, 28f, 28f, 0f, 0f));

        // Drag handle
        View handle = new View(act);
        handle.setBackground(shape(c.outlineVariant, 3f));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(36), dp(4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = dp(16);
        box.addView(handle, hlp);

        if (title != null && !title.isEmpty()) {
            TextView t = text(title, 20, c.onSurface, true);
            t.setPadding(0, 0, 0, dp(14));
            box.addView(t);
        }

        if (body != null) {
            ScrollView sv = new ScrollView(act);
            sv.addView(body);
            box.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        }

        if (positive != null || negative != null) {
            LinearLayout actions = hbox();
            actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            actions.setPadding(0, dp(16), 0, 0);

            if (negative != null) {
                actions.addView(button(negative, TEXT, new View.OnClickListener() {
                    @Override public void onClick(View v) { d.dismiss(); }
                }));
            }
            if (positive != null) {
                actions.addView(button(positive, TONAL, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        d.dismiss();
                        if (onPositive != null) onPositive.run();
                    }
                }));
            }
            box.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        }

        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
        }
        d.show();
        return d;
    }

    // ------------------------------------------------------------- Dialog
    /**
     * Neo Backup styled dialog with 28dp surface, Phosphor icon header, and action buttons.
     */
    public Dialog dialog(Activity act, int iconType, String title, String message, View body,
                         String positive, final Runnable onPositive, String negative) {
        final Dialog d = new Dialog(act);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = vbox();
        box.setPadding(dp(24), dp(24), dp(24), dp(16));
        box.setBackground(shape(c.surfaceContainerHigh, 28));

        if (iconType > 0) {
            ImageView iv = new ImageView(act);
            iv.setImageDrawable(Phosphor.get(iconType, c.primary, dp(32)));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(36), dp(36));
            ilp.bottomMargin = dp(14);
            box.addView(iv, ilp);
        }

        TextView t = text(title, 22, c.onSurface, true);
        box.addView(t);

        LinearLayout content = vbox();
        content.setPadding(0, dp(12), 0, dp(8));
        if (message != null && !message.isEmpty()) {
            TextView m = text(message, 14, c.onSurfaceVariant, false);
            m.setLineSpacing(0, 1.15f);
            content.addView(m);
        }
        if (body != null) {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
            blp.topMargin = dp(8);
            content.addView(body, blp);
        }

        ScrollView sv = new ScrollView(act);
        sv.addView(content);
        box.addView(sv, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = hbox();
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(12), 0, 0);

        if (negative != null) {
            actions.addView(button(negative, TEXT, new View.OnClickListener() {
                @Override public void onClick(View v) { d.dismiss(); }
            }));
        }
        if (positive != null) {
            actions.addView(button(positive, TONAL, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    d.dismiss();
                    if (onPositive != null) onPositive.run();
                }
            }));
        }
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int max = Math.min(ctx.getResources().getDisplayMetrics().widthPixels - dp(32), dp(560));
            w.setLayout(max, ViewGroup.LayoutParams.WRAP_CONTENT);
            final View decor = w.getDecorView();
            decor.setScaleX(0.92f);
            decor.setScaleY(0.92f);
            decor.setAlpha(0f);
            decor.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(240)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .start();
        }
        d.show();
        return d;
    }
}
