package io.github.expirt.storageredirect;

import android.app.Activity;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Settings for one app (EXTRA_PKG set) or the defaults (no extra). */
public class SettingsActivity extends Activity {

    static final String EXTRA_PKG = "pkg";

    private static final int K_INTERNAL = 1, K_PREFS = 2, K_EXTERNAL = 3, K_STORAGE = 4, K_RAW = 5,
            K_OVERRIDE = 6, K_FAKE = 7;

    private Ui ui;
    private String sd;
    private String pkg;                 // null = defaults
    private RedirectConfig cfg;
    private boolean overrideOn;         // only meaningful when pkg != null
    private boolean building = true;
    private Switch prefsSwitch;
    private Switch rawSwitch;
    private final EditText[] pathEdits = new EditText[3];
    private final TextView[] resolved = new TextView[3];
    private EditText blockEdit;
    private EditText excludeEdit;
    private List<String[]> rulePairs = new ArrayList<>();   // [original path, redirect path]
    private LinearLayout rulesHolder;
    private final List<View> ruleControls = new ArrayList<>();
    private LinearLayout warnCard;
    private TextView warnText;
    private BackupCard backupCard;
    private final List<View> controls = new ArrayList<>();

    /** A toggle row and its switch. */
    private static final class Row {
        LinearLayout view;
        Switch sw;
    }

    @Override
    protected void onCreate(Bundle b) {
        Ui.applyTheme(this);
        super.onCreate(b);
        ui = new Ui(this);
        ui.window(this);
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

    /** A restore changed this app's redirect settings: reload them and redraw. */
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

    // -------------------------------------------------------------------- UI
    private View buildUi() {
        controls.clear();
        ruleControls.clear();
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(ui.c.surface);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);

        LinearLayout col = ui.vbox();
        col.setPadding(ui.dp(16), ui.dp(36), ui.dp(16), ui.dp(40));
        sv.addView(col, new ViewGroup.LayoutParams(-1, -2));

        // ---- header
        LinearLayout head = ui.hbox();
        head.setPadding(ui.dp(4), 0, 0, ui.dp(18));
        String title = "Default settings";
        String sub = "Used by every app without its own settings";
        if (pkg != null) {
            title = pkg;
            sub = pkg;
            try {
                PackageManager pm = getPackageManager();
                title = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                Drawable icon = pm.getApplicationIcon(pkg);
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(icon);
                ui.squircle(iv, 18);
                head.addView(iv, new LinearLayout.LayoutParams(ui.dp(56), ui.dp(56)));
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        } else {
            head.addView(ui.iconTile(Ui.Icon.SLIDERS, ui.c.tertiaryContainer, ui.c.onTertiaryContainer, 56));
        }
        LinearLayout names = ui.vbox();
        names.setPadding(ui.dp(16), 0, 0, 0);
        names.addView(ui.text(title, 26, ui.c.onSurface, true));
        names.addView(ui.text(sub, 13, ui.c.onSurfaceVariant, false));
        head.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(head);

        // ---- per-app override
        if (pkg != null) {
            Row ov = toggle("Custom settings for this app",
                    overrideOn ? "This app ignores the defaults."
                            : "Off \u2014 this app follows the default settings.",
                    overrideOn, K_OVERRIDE, false);
            LinearLayout one = ui.card(overrideOn ? ui.c.primaryContainer : ui.c.surfaceContainer);
            one.setPadding(ui.dp(20), ui.dp(14), ui.dp(20), ui.dp(14));
            one.addView(ov.view);
            col.addView(one);
        }

        // ---- what to redirect
        col.addView(ui.label("What to redirect"));
        List<View> rows = new ArrayList<>();
        rows.add(toggle("Internal data", "/data/data/<pkg>  \u2192  files, cache, databases, app_*",
                cfg.internal, K_INTERNAL, true).view);
        Row prefs = toggle("SharedPreferences", "Also move shared_prefs (needs Internal data)",
                cfg.prefs, K_PREFS, true);
        prefsSwitch = prefs.sw;
        rows.add(prefs.view);
        rows.add(toggle("App external data", "/Android/data/<pkg> and /Android/obb/<pkg>",
                cfg.external, K_EXTERNAL, true).view);
        rows.add(toggle("Main storage", "/storage/emulated/0  \u2192  private virtual sdcard per app",
                cfg.storage, K_STORAGE, true).view);
        Row raw = toggle("Hardcoded paths", "Also rewrite raw \u201C/data/data/\u2026\u201D and \u201C/sdcard/\u2026\u201D strings",
                cfg.rawPaths, K_RAW, true);
        rawSwitch = raw.sw;
        rows.add(raw.view);
        col.addView(ui.group(rows));

        // ---- fake paths
        col.addView(ui.label("Fake standard paths"));
        List<View> fake = new ArrayList<>();
        fake.add(toggle("Show standard paths to the app",
                "The app keeps seeing /data/user/0/<pkg>, /storage/emulated/0 and /Android/data/<pkg>; "
                        + "file operations are redirected underneath. Java I/O only \u2014 native code "
                        + "(Unity, C++ engines) can\u2019t follow, so leave it off for those apps.",
                cfg.fakePaths, K_FAKE, true).view);
        col.addView(ui.group(fake));

        // ---- path redirects
        col.addView(ui.label("Path redirects"));
        List<View> rul = new ArrayList<>();
        LinearLayout rulBox = ui.vbox();
        rulBox.addView(ui.text("Redirect a path to another path", 16, ui.c.onSurface, true));
        TextView rd = ui.text("Type or browse for the full original path, then the full path it should be redirected to. "
                + "Both must start with /  ({pkg} = the app\u2019s package name). Everything under the "
                + "original path goes to the same place under the redirect path; the app keeps using the "
                + "original path. Wildcards: * = any name, ** = anything (several folders). Use {1}, {2}\u2026 "
                + "in the redirect path for what each wildcard matched, e.g. /sdcard/Android/data/*/cache "
                + "\u2192 /sdcard/Onedroid/cache/{1}. Rules are checked from the top; the first match wins. "
                + "Java file I/O only.", 12, ui.c.onSurfaceVariant, false);
        rd.setPadding(0, ui.dp(2), 0, ui.dp(12));
        rulBox.addView(rd);
        rulePairs = parseRules(cfg.pathRules);
        rulesHolder = ui.vbox();
        rulBox.addView(rulesHolder);
        TextView addRule = ui.button("Add redirect", Ui.TONAL, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!editable()) return;
                rulePairs.add(new String[]{"", ""});
                fillRules(true);
            }
        });
        controls.add(addRule);
        rulBox.addView(addRule);
        rul.add(rulBox);
        col.addView(ui.group(rul));
        fillRules(false);

        // ---- never redirect
        col.addView(ui.label("Never redirect"));
        List<View> exl = new ArrayList<>();
        LinearLayout exBox = ui.vbox();
        exBox.addView(ui.text("Paths to leave alone", 16, ui.c.onSurface, true));
        TextView ed2 = ui.text("These paths, and everything under them, are never redirected \u2014 not by the "
                + "rules above and not by the folder redirects. One per line; absolute, or relative to " + sd
                + ". {pkg} and wildcards (* and **) work.", 12, ui.c.onSurfaceVariant, false);
        ed2.setPadding(0, ui.dp(2), 0, ui.dp(10));
        exBox.addView(ed2);
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
        exBox.addView(excludeEdit);
        exBox.addView(addPathButton(excludeEdit, "Add path to leave alone"));
        exl.add(exBox);
        col.addView(ui.group(exl));

        // ---- block creation
        col.addView(ui.label("Block creation"));
        List<View> blk = new ArrayList<>();
        LinearLayout blkBox = ui.vbox();
        TextView bt = ui.text("Paths to block", 16, ui.c.onSurface, true);
        blkBox.addView(bt);
        TextView bd = ui.text("The app can\u2019t create a file or folder at exactly one of these paths "
                + "(one per line, case-sensitive). Absolute, or relative to " + sd
                + ".  {pkg} = the app\u2019s package name. * = any name, ** = anything: "
                + "/x/logs/** blocks everything inside logs, /x/logs/* only its direct children. "
                + "Java file I/O only.", 12, ui.c.onSurfaceVariant, false);
        bd.setPadding(0, ui.dp(2), 0, ui.dp(10));
        blkBox.addView(bd);
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
        blkBox.addView(blockEdit);
        blkBox.addView(addPathButton(blockEdit, "Add path to block"));
        blk.add(blkBox);
        col.addView(ui.group(blk));

        // ---- destination folders
        col.addView(ui.label("Destination folders"));
        col.addView(ui.text("Absolute path, or relative to " + sd + ".  {pkg} = the app\u2019s package name.",
                12, ui.c.onSurfaceVariant, false));
        List<View> paths = new ArrayList<>();
        paths.add(pathRow("Internal data folder", 0, cfg.internalPath, RedirectConfig.DEF_INTERNAL));
        paths.add(pathRow("App external data folder", 1, cfg.externalPath, RedirectConfig.DEF_EXTERNAL));
        paths.add(pathRow("Main storage folder", 2, cfg.storagePath, RedirectConfig.DEF_STORAGE));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, -2);
        glp.topMargin = ui.dp(8);
        col.addView(ui.group(paths), glp);

        warnCard = ui.card(ui.c.errorContainer);
        warnText = ui.text("", 13, ui.c.onErrorContainer, false);
        warnCard.addView(warnText);
        ((LinearLayout.LayoutParams) warnCard.getLayoutParams()).topMargin = ui.dp(6);
        col.addView(warnCard);
        updatePathInfo();

        // ---- backups (per app)
        backupCard = null;
        if (pkg != null) {
            col.addView(ui.label("Backups"));
            backupCard = new BackupCard(this, sd, pkg, new Runnable() {
                @Override public void run() { reloadAfterRestore(); }
            });
            col.addView(backupCard.view());
        }

        TextView note = ui.text("Changes apply the next time the app starts \u2014 force stop it first.",
                12, ui.c.onSurfaceVariant, false);
        note.setPadding(ui.dp(8), ui.dp(18), 0, 0);
        col.addView(note);
        return sv;
    }

    private Row toggle(String title, String desc, boolean init, final int key, boolean isSetting) {
        Row r = new Row();
        LinearLayout row = ui.hbox();
        LinearLayout tx = ui.vbox();
        tx.addView(ui.text(title, 16, key == K_OVERRIDE && overrideOn ? ui.c.onPrimaryContainer : ui.c.onSurface, true));
        TextView d = ui.text(desc, 12, key == K_OVERRIDE && overrideOn ? ui.c.onPrimaryContainer : ui.c.onSurfaceVariant, false);
        d.setPadding(0, ui.dp(2), ui.dp(12), 0);
        tx.addView(d);
        row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));

        Switch s = ui.toggle(init);
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
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
        t.setPadding(0, 0, 0, ui.dp(8));
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
        box.addView(browseRow(e, title, false, controls));
        TextView r = ui.text("", 11, ui.c.onSurfaceVariant, false);
        r.setPadding(ui.dp(4), ui.dp(6), 0, 0);
        resolved[which] = r;
        box.addView(r);
        return box;
    }

    // ------------------------------------------------------------ folder picker
    /** A text box with a Browse button beside it that fills the box from the folder picker. */
    private LinearLayout browseRow(final EditText field, final String title, final boolean files,
                                   List<View> bucket) {
        LinearLayout row = ui.hbox();
        row.addView(field, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView b = ui.button("Browse", Ui.TONAL, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!editable()) return;
                FolderPicker.show(SettingsActivity.this, ui, title, field.getText().toString(), sd, files,
                        new FolderPicker.Callback() {
                            @Override public void onPicked(String path) {
                                field.setText(path);
                                field.setSelection(path.length());
                            }
                        });
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = ui.dp(8);
        row.addView(b, lp);
        bucket.add(b);
        return row;
    }

    /** Button under a multi-line path list: picks a folder or file and adds it as a new line. */
    private TextView addPathButton(final EditText area, final String title) {
        TextView b = ui.button("Browse and add\u2026", Ui.TONAL, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!editable()) return;
                FolderPicker.show(SettingsActivity.this, ui, title, "", sd, true,
                        new FolderPicker.Callback() {
                            @Override public void onPicked(String path) {
                                String t = area.getText().toString();
                                if (t.length() > 0 && !t.endsWith("\n")) t += "\n";
                                area.setText(t + path);
                            }
                        });
            }
        });
        controls.add(b);
        return b;
    }

    // ------------------------------------------------------ path redirect editor
    private static List<String[]> parseRules(String text) {
        List<String[]> out = new ArrayList<>();
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

    /** Saves every rule that has both boxes filled in. */
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
        boolean bad = (pr[0].trim().length() > 0 && !pr[0].trim().startsWith("/"))
                || (pr[1].trim().length() > 0 && !pr[1].trim().startsWith("/"));
        warn.setVisibility(bad ? View.VISIBLE : View.GONE);
    }

    private TextView smallLabel(String t, int topDp) {
        TextView v = ui.text(t, 13, ui.c.onSurfaceVariant, true);
        v.setPadding(0, ui.dp(topDp), 0, ui.dp(8));
        return v;
    }

    /** Moves a rule up (-1) or down (+1): rules higher in the list win. */
    private void moveRule(String[] pr, int dir) {
        if (!editable()) return;
        int i = rulePairs.indexOf(pr), j = i + dir;
        if (i < 0 || j < 0 || j >= rulePairs.size()) return;
        rulePairs.set(i, rulePairs.get(j));
        rulePairs.set(j, pr);
        saveRules();
        fillRules(false);
    }

    /** (Re)draws one card per rule: the original path box with the redirect path box under it. */
    private void fillRules(boolean focusLast) {
        controls.removeAll(ruleControls);
        ruleControls.clear();
        rulesHolder.removeAllViews();
        if (rulePairs.isEmpty()) {
            TextView none = ui.text("No redirects yet.", 13, ui.c.onSurfaceVariant, false);
            none.setPadding(ui.dp(4), 0, 0, ui.dp(12));
            rulesHolder.addView(none);
        }
        EditText last = null;
        for (final String[] pr : rulePairs) {
            LinearLayout card = ui.vbox();
            card.setPadding(0, 0, 0, ui.dp(14));
            card.addView(smallLabel("Original path", 0));
            final EditText from = ui.field(pr[0], "/storage/emulated/0/Download/{pkg}");
            card.addView(browseRow(from, "Original path", true, ruleControls));
            card.addView(smallLabel("Redirect to", 12));
            final EditText to = ui.field(pr[1], "/storage/emulated/0/Games/{pkg}");
            card.addView(browseRow(to, "Redirect to", true, ruleControls));
            final TextView warn = ui.text("Use full paths that start with /", 11, ui.c.error, false);
            warn.setPadding(ui.dp(4), ui.dp(6), 0, 0);
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
            btns.setPadding(0, ui.dp(4), 0, 0);
            TextView up = ui.button("Up", Ui.TEXT, new View.OnClickListener() {
                @Override public void onClick(View v) { moveRule(pr, -1); }
            });
            TextView down = ui.button("Down", Ui.TEXT, new View.OnClickListener() {
                @Override public void onClick(View v) { moveRule(pr, 1); }
            });
            TextView rm = ui.button("Remove", Ui.TEXT_ERROR, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (!editable()) return;
                    rulePairs.remove(pr);
                    saveRules();
                    fillRules(false);
                }
            });
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
            // Start from whatever the app currently inherits.
            overrideOn = true;
            if (!cfg.save(sd, pkg)) {
                overrideOn = false;
                toast("Can\u2019t write settings \u2014 grant all-files access first");
            }
        } else {
            if (!RedirectConfig.deleteOverride(sd, pkg)) {
                toast("Can\u2019t remove the override \u2014 grant all-files access first");
            } else {
                overrideOn = false;
                cfg = RedirectConfig.load(sd, pkg);     // back to the defaults
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
        if (rawSwitch != null && ed) {      // fake mode already covers hardcoded paths
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
}
