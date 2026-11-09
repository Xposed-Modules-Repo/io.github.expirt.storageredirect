package io.github.expirt.storageredirect;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * SettingsActivity: Redesigned following Neo Backup's AppPage component hierarchy.
 * Manages per-application redirection settings or global defaults with 100% feature preservation.
 */
public class SettingsActivity extends Activity {

    public static final String EXTRA_PKG = "pkg";

    private static final int K_INTERNAL = 1, K_PREFS = 2, K_EXTERNAL = 3, K_STORAGE = 4, K_RAW = 5,
            K_OVERRIDE = 6, K_FAKE = 7, K_DEBUG = 8;

    private NeoUi ui;
    private String sd;
    private String pkg;
    private RedirectConfig cfg;
    private boolean overrideOn;
    private boolean building = true;

    private Switch prefsSwitch;
    private Switch rawSwitch;
    private final EditText[] pathEdits = new EditText[3];
    private final TextView[] resolved = new TextView[3];
    private EditText blockEdit;
    private EditText excludeEdit;
    private List<String[]> rulePairs = new ArrayList<String[]>();
    private LinearLayout rulesHolder;
    private final List<View> ruleControls = new ArrayList<View>();
    private LinearLayout warnCard;
    private TextView warnText;
    private BackupCard backupCard;
    private final List<View> controls = new ArrayList<View>();

    private static final class Row {
        LinearLayout view;
        Switch sw;
    }

    @Override
    protected void onCreate(Bundle b) {
        NeoUi.applyTheme(this);
        super.onCreate(b);
        RedirectConfig.init(this);
        ui = new NeoUi(this);
        ui.setupWindow(this);
        sd = Environment.getExternalStorageDirectory().getAbsolutePath();
        pkg = getIntent().getStringExtra(EXTRA_PKG);
        overrideOn = RedirectConfig.hasOverride(sd, pkg);
        cfg = RedirectConfig.load(sd, pkg);
        setContentView(buildUi());
        building = false;
        applyEnabled();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!building && backupCard != null) backupCard.refresh();
    }

    private boolean editable() {
        return pkg == null || overrideOn;
    }

    void reloadAfterRestore() {
        overrideOn = RedirectConfig.hasOverride(sd, pkg);
        cfg = RedirectConfig.load(sd, pkg);
        rebuild();
    }

    private void rebuild() {
        building = true;
        setContentView(buildUi());
        building = false;
        applyEnabled();
    }

    private View buildUi() {
        controls.clear();
        ruleControls.clear();

        LinearLayout rootLayout = ui.vbox();
        rootLayout.setBackgroundColor(ui.c.surface);

        // ---- Neo Backup TopBar
        String screenTitle = (pkg != null) ? "App Configuration" : "Default Settings";
        FrameLayout topBarView = new FrameLayout(this);
        topBarView.setLayoutParams(new LinearLayout.LayoutParams(-1, ui.dp(64)));
        topBarView.setPadding(ui.dp(12), ui.dp(8), ui.dp(16), ui.dp(8));

        LinearLayout topBarRow = ui.hbox();
        topBarRow.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

        FrameLayout btnBack = ui.roundButton(Phosphor.ARROW_LEFT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        topBarRow.addView(btnBack);

        TextView topTitle = ui.text(screenTitle, 20, ui.c.onSurface, true);
        topTitle.setPadding(ui.dp(12), 0, 0, 0);
        topBarRow.addView(topTitle, new LinearLayout.LayoutParams(0, -2, 1f));

        if (pkg != null) {
            FrameLayout btnReset = ui.roundButton(Phosphor.RESTORE, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ui.dialog(SettingsActivity.this, Phosphor.WARNING, "Reset to defaults?",
                            "Remove custom configuration for this app and revert to the global defaults?",
                            null, "Reset", new Runnable() {
                        @Override public void run() { setOverride(false); }
                    }, "Cancel");
                }
            });
            topBarRow.addView(btnReset);
        }

        topBarView.addView(topBarRow);
        rootLayout.addView(topBarView);

        // ---- Scrollable body
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);

        LinearLayout col = ui.vbox();
        col.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(40));
        sv.addView(col, new ViewGroup.LayoutParams(-1, -2));

        // ---- Header Card (App Icon, Title, Package)
        LinearLayout headerCard = ui.card(ui.c.surfaceContainerLow, 22f);
        LinearLayout head = ui.hbox();
        String title = "Default settings";
        String sub = "Global rules applied to every app without an override";
        String verInfo = "";

        if (pkg != null) {
            title = pkg;
            sub = pkg;
            try {
                PackageManager pm = getPackageManager();
                title = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                PackageInfo pi = pm.getPackageInfo(pkg, 0);
                if (pi.versionName != null) verInfo = "v" + pi.versionName;
                Drawable icon = pm.getApplicationIcon(pkg);
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(icon);
                ui.squircle(iv, 16);
                head.addView(iv, new LinearLayout.LayoutParams(ui.dp(56), ui.dp(56)));
            } catch (Throwable ignored) {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(Phosphor.get(Phosphor.HOUSE, ui.c.primary, ui.dp(36)));
                head.addView(iv, new LinearLayout.LayoutParams(ui.dp(56), ui.dp(56)));
            }
        } else {
            FrameLayout iconBox = new FrameLayout(this);
            iconBox.setBackground(ui.shape(ui.c.primaryContainer, 16f));
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(Phosphor.get(Phosphor.SLIDERS, ui.c.onPrimaryContainer, ui.dp(28)));
            iconBox.addView(iv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
            head.addView(iconBox, new LinearLayout.LayoutParams(ui.dp(56), ui.dp(56)));
        }

        LinearLayout names = ui.vbox();
        names.setPadding(ui.dp(16), 0, 0, 0);
        names.addView(ui.text(title, 22, ui.c.onSurface, true));
        names.addView(ui.text(sub, 12, ui.c.onSurfaceVariant, false));
        if (!verInfo.isEmpty()) {
            TextView vt = ui.chip(verInfo, ui.c.surfaceContainerHigh, ui.c.onSurfaceVariant);
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
            vlp.topMargin = ui.dp(4);
            names.addView(vt, vlp);
        }
        head.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        headerCard.addView(head);
        col.addView(headerCard);

        // ---- Per-app override toggle
        if (pkg != null) {
            Row ov = toggle("Custom settings for this app",
                    overrideOn ? "This app uses customized redirection rules."
                            : "Off \u2014 this app follows the global default settings.",
                    overrideOn, K_OVERRIDE, false);
            LinearLayout one = ui.card(overrideOn ? ui.c.primaryContainer : ui.c.surfaceContainer, 20f);
            one.setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(14));
            one.addView(ov.view);
            col.addView(one);
        }

        // ---- Expandable Block: What to redirect
        NeoUi.ExpandableBlock redirectBlock = ui.expandableBlock("What to redirect", Phosphor.SLIDERS, true);
        List<View> rows = new ArrayList<View>();
        rows.add(toggle("Internal data", "/data/data/<pkg> \u2192 files, cache, databases, app_*",
                cfg.internal, K_INTERNAL, true).view);
        Row prefs = toggle("SharedPreferences", "Also redirect shared_prefs (requires Internal data)",
                cfg.prefs, K_PREFS, true);
        prefsSwitch = prefs.sw;
        rows.add(prefs.view);
        rows.add(toggle("App external data", "/Android/data/<pkg> and /Android/obb/<pkg>",
                cfg.external, K_EXTERNAL, true).view);
        rows.add(toggle("Main storage", "/storage/emulated/0 \u2192 private virtual storage per app",
                cfg.storage, K_STORAGE, true).view);
        Row raw = toggle("Hardcoded paths", "Rewrite raw \u201C/data/data/\u2026\u201D and \u201C/sdcard/\u2026\u201D strings",
                cfg.rawPaths, K_RAW, true);
        rawSwitch = raw.sw;
        rows.add(raw.view);
        redirectBlock.content.addView(ui.group(rows));
        col.addView(redirectBlock.root);

        // ---- Expandable Block: Destination folders
        NeoUi.ExpandableBlock destBlock = ui.expandableBlock("Destination folders", Phosphor.FOLDER, true);
        TextView destDesc = ui.text("Absolute path, or relative to " + sd + ". Use {pkg} for the package name.",
                12, ui.c.onSurfaceVariant, false);
        destDesc.setPadding(0, 0, 0, ui.dp(8));
        destBlock.content.addView(destDesc);

        List<View> paths = new ArrayList<View>();
        paths.add(pathRow("Internal data folder", 0, cfg.internalPath, RedirectConfig.DEF_INTERNAL));
        paths.add(pathRow("App external data folder", 1, cfg.externalPath, RedirectConfig.DEF_EXTERNAL));
        paths.add(pathRow("Main storage folder", 2, cfg.storagePath, RedirectConfig.DEF_STORAGE));
        destBlock.content.addView(ui.group(paths));

        warnCard = ui.card(ui.c.errorContainer, 16f);
        warnText = ui.text("", 12, ui.c.onErrorContainer, false);
        warnCard.addView(warnText);
        LinearLayout.LayoutParams wlp = (LinearLayout.LayoutParams) warnCard.getLayoutParams();
        wlp.topMargin = ui.dp(6);
        wlp.bottomMargin = 0;
        destBlock.content.addView(warnCard);
        col.addView(destBlock.root);
        updatePathInfo();

        // ---- Expandable Block: Fake standard paths & Diagnostics
        NeoUi.ExpandableBlock fakeBlock = ui.expandableBlock("Standard paths & Diagnostics", Phosphor.SHIELD_CHECKERED, false);
        List<View> fakeRows = new ArrayList<View>();
        fakeRows.add(toggle("Show standard paths to the app",
                "The app continues seeing /data/user/0/<pkg>, /storage/emulated/0 and /Android/data/<pkg>; "
                        + "file operations are redirected underneath. (Java I/O only).",
                cfg.fakePaths, K_FAKE, true).view);
        fakeRows.add(toggle("Detailed diagnostics",
                "Log original path, destination, matching rule, and operation status to logcat.",
                cfg.debug, K_DEBUG, true).view);
        fakeBlock.content.addView(ui.group(fakeRows));
        col.addView(fakeBlock.root);

        // ---- Expandable Block: Path-to-Path redirects
        NeoUi.ExpandableBlock rulesBlock = ui.expandableBlock("Path-to-Path redirects", Phosphor.REFRESH, true);
        TextView rd = ui.text("Redirect any file or directory to a custom destination. Both paths must start with / "
                + "({pkg} = package name). Wildcards: * = single folder name, ** = recursive folders. "
                + "Use {1}, {2}\u2026 in redirect path. Rules higher in the list take precedence.",
                12, ui.c.onSurfaceVariant, false);
        rd.setLineSpacing(0, 1.15f);
        rd.setPadding(0, 0, 0, ui.dp(12));
        rulesBlock.content.addView(rd);

        rulePairs = parseRules(cfg.pathRules);
        rulesHolder = ui.vbox();
        rulesBlock.content.addView(rulesHolder);

        TextView addRule = ui.buttonWithIcon("Add redirect", Phosphor.PLUS_CIRCLE, NeoUi.TONAL, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!editable()) return;
                rulePairs.add(new String[]{"", ""});
                fillRules(true);
            }
        });
        controls.add(addRule);
        LinearLayout.LayoutParams arlp = new LinearLayout.LayoutParams(-1, -2);
        arlp.topMargin = ui.dp(8);
        rulesBlock.content.addView(addRule, arlp);
        col.addView(rulesBlock.root);
        fillRules(false);

        // ---- Expandable Block: Never redirect & Block creation
        NeoUi.ExpandableBlock blockBlock = ui.expandableBlock("Excluded & Blocked paths", Phosphor.PROHIBIT, false);

        blockBlock.content.addView(ui.text("Never redirect", 14, ui.c.onSurface, true));
        TextView ed2 = ui.text("Paths that must never be redirected. One per line; absolute, or relative to " + sd
                + ". {pkg} and wildcards (* and **) are supported.", 12, ui.c.onSurfaceVariant, false);
        ed2.setPadding(0, ui.dp(2), 0, ui.dp(8));
        blockBlock.content.addView(ed2);

        excludeEdit = ui.area(cfg.excludePaths, "Android/data/{pkg}/files/keep\n/storage/emulated/0/DCIM/**", 3);
        excludeEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable ed) {
                if (building || !editable()) return;
                cfg.excludePaths = ed.toString();
                persist();
            }
        });
        controls.add(excludeEdit);
        blockBlock.content.addView(excludeEdit);

        TextView bt = ui.text("Block creation", 14, ui.c.onSurface, true);
        bt.setPadding(0, ui.dp(14), 0, 0);
        blockBlock.content.addView(bt);

        TextView bd = ui.text("The app cannot create a file or folder at these paths (permission denied). "
                + "One per line. Absolute, or relative to " + sd + ". Supports * and ** wildcards.",
                12, ui.c.onSurfaceVariant, false);
        bd.setPadding(0, ui.dp(2), 0, ui.dp(8));
        blockBlock.content.addView(bd);

        blockEdit = ui.area(cfg.blockPaths, "Android/data/{pkg}/files/.nomedia\n/storage/emulated/0/DCIM/.thumbnails", 3);
        blockEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable ed) {
                if (building || !editable()) return;
                cfg.blockPaths = ed.toString();
                persist();
            }
        });
        controls.add(blockEdit);
        blockBlock.content.addView(blockEdit);
        col.addView(blockBlock.root);

        // ---- Backups Section (Per App)
        backupCard = null;
        if (pkg != null) {
            NeoUi.ExpandableBlock bkBlock = ui.expandableBlock("Backups", Phosphor.FILE_ZIP, true);
            backupCard = new BackupCard(this, sd, pkg, new Runnable() {
                @Override public void run() { reloadAfterRestore(); }
            });
            bkBlock.content.addView(backupCard.view());
            col.addView(bkBlock.root);
        }

        // Footer note
        TextView note = ui.text("Changes apply the next time the app starts \u2014 force stop it first.",
                12, ui.c.onSurfaceVariant, false);
        note.setPadding(ui.dp(8), ui.dp(16), 0, 0);
        col.addView(note);

        rootLayout.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        return rootLayout;
    }

    private Row toggle(String title, String desc, boolean init, final int key, boolean isSetting) {
        Row r = new Row();
        LinearLayout row = ui.hbox();
        LinearLayout tx = ui.vbox();
        tx.addView(ui.text(title, 15, key == K_OVERRIDE && overrideOn ? ui.c.onPrimaryContainer : ui.c.onSurface, true));
        TextView d = ui.text(desc, 12, key == K_OVERRIDE && overrideOn ? ui.c.onPrimaryContainer : ui.c.onSurfaceVariant, false);
        d.setPadding(0, ui.dp(2), ui.dp(12), 0);
        tx.addView(d);
        row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));

        Switch s = ui.toggle(init);
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean on) {
                if (building) return;
                if (key == K_OVERRIDE) { setOverride(on); return; }
                if (!editable()) return;
                switch (key) {
                    case K_INTERNAL: cfg.internal = on; break;
                    case K_PREFS: cfg.prefs = on; break;
                    case K_EXTERNAL: cfg.external = on; break;
                    case K_STORAGE: cfg.storage = on; break;
                    case K_RAW: cfg.rawPaths = on; break;
                    case K_FAKE: cfg.fakePaths = on; break;
                    case K_DEBUG: cfg.debug = on; break;
                }
                persist();
                updatePathInfo();
                applyEnabled();
            }
        });
        if (isSetting) controls.add(s);
        row.addView(s);
        r.view = row;
        r.sw = s;
        return r;
    }

    private View pathRow(String title, final int which, String value, final String def) {
        LinearLayout box = ui.vbox();
        TextView t = ui.text(title, 13, ui.c.onSurfaceVariant, true);
        t.setPadding(0, 0, 0, ui.dp(6));
        box.addView(t);
        EditText e = ui.field(value, def);
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable ed) {
                if (building || !editable()) return;
                String v = ed.toString().trim();
                if (v.length() == 0) v = def;
                if (which == 0) cfg.internalPath = v;
                else if (which == 1) cfg.externalPath = v;
                else cfg.storagePath = v;
                persist();
                updatePathInfo();
            }
        });
        pathEdits[which] = e;
        controls.add(e);
        box.addView(e);
        TextView r = ui.text("", 11, ui.c.onSurfaceVariant, false);
        r.setPadding(ui.dp(4), ui.dp(6), 0, 0);
        resolved[which] = r;
        box.addView(r);
        return box;
    }

    private static List<String[]> parseRules(String text) {
        List<String[]> out = new ArrayList<String[]>();
        for (String l : text.split("\n")) {
            String t = l.trim();
            if (t.length() == 0 || t.startsWith("#")) continue;
            int i = t.indexOf("=>");
            if (i < 0) i = t.indexOf("->");
            if (i < 0) out.add(new String[]{t, ""});
            else out.add(new String[]{t.substring(0, i).trim(), t.substring(i + 2).trim()});
        }
        return out;
    }

    private void saveRules() {
        StringBuilder sb = new StringBuilder();
        for (String[] pr : rulePairs) {
            String f = pr[0].trim(), t = pr[1].trim();
            if (f.length() == 0 || t.length() == 0) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(f).append(" => ").append(t);
        }
        cfg.pathRules = sb.toString();
        persist();
    }

    private void ruleWarn(String[] pr, TextView warn) {
        String f = pr[0].trim(), t = pr[1].trim();
        if (f.isEmpty() || t.isEmpty()) {
            warn.setVisibility(View.GONE);
            return;
        }
        boolean relFrom = !f.startsWith("/");
        boolean relTo = !t.startsWith("/");
        if (relFrom || relTo) {
            String pf = relFrom ? (sd + "/" + f) : f;
            String pt = relTo ? (sd + "/" + t) : t;
            warn.setText("Resolves: " + pf + " \u2192 " + pt);
            warn.setTextColor(ui.c.onSurfaceVariant);
            warn.setVisibility(View.VISIBLE);
        } else {
            warn.setVisibility(View.GONE);
        }
    }

    private TextView smallLabel(String t, int topDp) {
        TextView v = ui.text(t, 12, ui.c.onSurfaceVariant, true);
        v.setPadding(0, ui.dp(topDp), 0, ui.dp(6));
        return v;
    }

    private void moveRule(String[] pr, int dir) {
        if (!editable()) return;
        int i = rulePairs.indexOf(pr), j = i + dir;
        if (i < 0 || j < 0 || j >= rulePairs.size()) return;
        rulePairs.set(i, rulePairs.get(j));
        rulePairs.set(j, pr);
        saveRules();
        fillRules(false);
    }

    private void fillRules(boolean focusLast) {
        controls.removeAll(ruleControls);
        ruleControls.clear();
        rulesHolder.removeAllViews();
        if (rulePairs.isEmpty()) {
            TextView none = ui.text("No custom redirects configured.", 13, ui.c.onSurfaceVariant, false);
            none.setPadding(ui.dp(4), ui.dp(4), 0, ui.dp(12));
            rulesHolder.addView(none);
        }
        EditText last = null;
        for (final String[] pr : rulePairs) {
            LinearLayout card = ui.card(ui.c.surfaceContainerLow, 18f);
            card.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));

            card.addView(smallLabel("Original path", 0));
            final EditText from = ui.field(pr[0], "Download/{pkg} or /full/path");
            card.addView(from);

            card.addView(smallLabel("Redirect to path", 10));
            final EditText to = ui.field(pr[1], "CustomData/{pkg}/downloads or /full/path");
            card.addView(to);

            final TextView warn = ui.text("", 11, ui.c.onSurfaceVariant, false);
            warn.setPadding(ui.dp(4), ui.dp(4), 0, 0);
            warn.setVisibility(View.GONE);
            card.addView(warn);
            ruleWarn(pr, warn);

            TextWatcher w = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
                @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
                @Override public void afterTextChanged(Editable ed) {
                    if (building || !editable()) return;
                    pr[0] = from.getText().toString();
                    pr[1] = to.getText().toString();
                    ruleWarn(pr, warn);
                    saveRules();
                }
            };
            from.addTextChangedListener(w);
            to.addTextChangedListener(w);

            LinearLayout btns = ui.hbox();
            btns.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            btns.setPadding(0, ui.dp(8), 0, 0);

            TextView up = ui.buttonWithIcon("Up", Phosphor.ARROW_UP, NeoUi.TEXT, new View.OnClickListener() {
                @Override public void onClick(View v) { moveRule(pr, -1); }
            });
            up.setMinHeight(ui.dp(36));
            up.setTextSize(12);

            TextView down = ui.buttonWithIcon("Down", Phosphor.ARROW_DOWN, NeoUi.TEXT, new View.OnClickListener() {
                @Override public void onClick(View v) { moveRule(pr, 1); }
            });
            down.setMinHeight(ui.dp(36));
            down.setTextSize(12);

            TextView rm = ui.buttonWithIcon("Remove", Phosphor.TRASH, NeoUi.TEXT_ERROR, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (!editable()) return;
                    rulePairs.remove(pr);
                    saveRules();
                    fillRules(false);
                }
            });
            rm.setMinHeight(ui.dp(36));
            rm.setTextSize(12);

            btns.addView(up);
            btns.addView(down);
            btns.addView(rm);
            card.addView(btns);

            ruleControls.add(from);
            ruleControls.add(to);
            ruleControls.add(up);
            ruleControls.add(down);
            ruleControls.add(rm);
            rulesHolder.addView(card);
            last = from;
        }
        controls.addAll(ruleControls);
        if (!building) applyEnabled();
        if (focusLast && last != null) last.requestFocus();
    }

    private void setOverride(boolean on) {
        if (on) {
            overrideOn = true;
            if (!cfg.save(sd, pkg)) {
                overrideOn = false;
                toast("Can\u2019t write settings \u2014 grant all-files access first");
            }
        } else {
            if (!RedirectConfig.deleteOverride(sd, pkg)) {
                toast("Can\u2019t remove override \u2014 grant all-files access first");
            } else {
                overrideOn = false;
                cfg = RedirectConfig.load(sd, pkg);
            }
        }
        rebuild();
    }

    private void applyEnabled() {
        boolean ed = editable();
        for (View v : controls) {
            v.setEnabled(ed);
            v.setAlpha(ed ? 1f : 0.4f);
        }
        boolean[] on = {cfg.internal, cfg.external, cfg.storage};
        for (int i = 0; i < 3; i++) {
            if (pathEdits[i] == null) continue;
            boolean en = ed && on[i];
            pathEdits[i].setEnabled(en);
            pathEdits[i].setAlpha(en ? 1f : 0.4f);
        }
        if (prefsSwitch != null && ed) {
            prefsSwitch.setEnabled(cfg.internal);
            prefsSwitch.setAlpha(cfg.internal ? 1f : 0.4f);
        }
        if (rawSwitch != null && ed) {
            rawSwitch.setEnabled(!cfg.fakePaths);
            rawSwitch.setAlpha(cfg.fakePaths ? 0.4f : 1f);
        }
    }

    private void updatePathInfo() {
        if (resolved[0] == null || warnCard == null) return;
        String[] paths = {cfg.internalPath, cfg.externalPath, cfg.storagePath};
        String[] abs = new String[3];
        for (int i = 0; i < 3; i++) {
            abs[i] = RedirectConfig.resolve(sd, pkg, paths[i]).getAbsolutePath();
            resolved[i].setText((cfg.fakePaths ? "real folder \u2192 " : "\u2192 ") + abs[i]);
        }
        boolean clash = cfg.internal && cfg.external && abs[0].equals(abs[1]);
        warnText.setText("Internal and external use the same folder \u2014 their files and cache would be mixed.");
        warnCard.setVisibility(clash ? View.VISIBLE : View.GONE);
    }

    private void persist() {
        if (!editable()) return;
        if (!cfg.save(sd, pkg)) toast("Can\u2019t write settings \u2014 grant all-files access first");
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
