package io.github.expirt.storageredirect;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** "Backups" section of an app's settings: set up, run, list, restore, rename and delete zip backups. */
final class BackupCard {

    private static final int[] LEVELS = {6, 9, 1, 0};
    private static final String[] LEVEL_NAMES = {"Normal", "Maximum", "Fast", "None (store only)"};
    private static final int NEUTRAL = 0, WARN = 1, OK = 2;

    // A run keeps going while the screen is rebuilt, so its state is static.
    private static BackupCard sCurrent;
    private static volatile boolean sRunning;
    private static volatile boolean sCancel;
    private static volatile String sStatus = "";
    private static volatile int sStatusKind = NEUTRAL;

    private final Activity act;
    private final Ui ui;
    private final String sd;
    private final String pkg;
    private final BackupConfig bc;
    private final Runnable onChanged;     // redraw the settings screen after a restore
    private final LinearLayout root;
    private final LinearLayout listBox;
    private final TextView status;
    private final TextView action;
    private final TextView levelBtn;
    private boolean building = true;

    BackupCard(Activity act, String sd, String pkg, Runnable onChanged) {
        this.onChanged = onChanged;
        this.act = act;
        this.ui = new Ui(act);
        this.sd = sd;
        this.pkg = pkg;
        this.bc = BackupConfig.load(sd, pkg);
        sCurrent = this;

        root = ui.vbox();

        TextView intro = ui.text("Zip backups of this app\u2019s redirected folders. Restore puts the files back "
                + "into the folders they came from and points the app\u2019s redirect settings at them again.",
                13, ui.c.onSurfaceVariant, false);
        intro.setPadding(ui.dp(8), 0, ui.dp(8), ui.dp(10));
        root.addView(intro);

        // ---- include
        List<View> inc = new ArrayList<>();
        inc.add(toggleRow("Internal data", bc.internal, 0));
        inc.add(toggleRow("App external data", bc.external, 1));
        inc.add(toggleRow("Main storage", bc.storage, 2));
        root.addView(ui.group(inc));

        // ---- where and how it is named
        List<View> where = new ArrayList<>();
        where.add(fieldRow("Backup folder", bc.dir, BackupConfig.DEF_DIR, 0));
        where.add(fieldRow("File name   {pkg} {app} {ver} {date} {time}", bc.name, BackupConfig.DEF_NAME, 1));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, -2);
        wlp.topMargin = ui.dp(10);
        root.addView(ui.group(where), wlp);

        // ---- actions
        LinearLayout actions = ui.card(ui.c.surfaceContainer);
        levelBtn = ui.button(levelLabel(), Ui.TONAL, new View.OnClickListener() {
            @Override public void onClick(View v) {
                bc.level = LEVELS[(levelIndex() + 1) % LEVELS.length];
                bc.save(BackupCard.this.sd, BackupCard.this.pkg);
                levelBtn.setText(levelLabel());
            }
        });
        actions.addView(levelBtn, new LinearLayout.LayoutParams(-1, -2));
        action = ui.button("Back up now", Ui.FILLED, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (sRunning) { sCancel = true; sStatus = "Cancelling\u2026"; applyStatus(); }
                else askNameAndStart();
            }
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = ui.dp(8);
        actions.addView(action, alp);
        status = ui.text("", 12, ui.c.onSurfaceVariant, false);
        status.setPadding(ui.dp(4), ui.dp(10), 0, 0);
        actions.addView(status);
        LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) actions.getLayoutParams();
        clp.topMargin = ui.dp(10);
        root.addView(actions);

        // ---- saved backups
        root.addView(ui.label("Saved backups"));
        listBox = ui.vbox();
        root.addView(listBox);

        building = false;
        refresh();
    }

    View view() { return root; }

    void refresh() {
        applyStatus();
        listBox.removeAllViews();
        final List<Backups.Item> items = Backups.list(bc.backupDir(sd, pkg));
        if (items.isEmpty()) {
            LinearLayout none = ui.card(ui.c.surfaceContainer);
            none.addView(ui.text("No backups yet.", 14, ui.c.onSurfaceVariant, false));
            listBox.addView(none);
            return;
        }
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        List<View> rows = new ArrayList<>();
        for (final Backups.Item it : items) {
            LinearLayout row = ui.vbox();
            row.addView(ui.text(it.name, 15, ui.c.onSurface, true));
            row.addView(ui.text(Formatter.formatShortFileSize(act, it.size) + "  \u00B7  "
                    + df.format(new Date(it.time)), 12, ui.c.onSurfaceVariant, false));
            LinearLayout btns = ui.hbox();
            btns.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            btns.addView(ui.button("Restore", Ui.TONAL, new View.OnClickListener() {
                @Override public void onClick(View v) { restoreDialog(it); }
            }));
            btns.addView(ui.button("Rename", Ui.TEXT, new View.OnClickListener() {
                @Override public void onClick(View v) { renameDialog(it); }
            }));
            btns.addView(ui.button("Delete", Ui.TEXT_ERROR, new View.OnClickListener() {
                @Override public void onClick(View v) { deleteDialog(it); }
            }));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
            blp.topMargin = ui.dp(6);
            row.addView(btns, blp);
            rows.add(row);
        }
        listBox.addView(ui.group(rows));
    }

    // ------------------------------------------------------------------- run
    private void askNameAndStart() {
        List<Backups.Source> src = sources();
        if (src.isEmpty()) {
            sStatus = "Nothing to back up \u2014 turn on a redirect, run the app once, and include its folder above.";
            sStatusKind = WARN;
            applyStatus();
            return;
        }
        String[] av = appInfo();
        final EditText e = ui.field(Backups.expand(bc.name, pkg, av[0], av[1], new Date()), "Backup name");
        e.setSelectAllOnFocus(true);
        ui.dialog(act, "Backup name", null, e, "Back up", new Runnable() {
            @Override public void run() {
                String base = Backups.sanitize(e.getText().toString());
                if (base.length() == 0) { toast("Invalid name"); return; }
                start(base);
            }
        }, "Cancel");
    }

    private void start(final String base) {
        if (sRunning) return;
        final List<Backups.Source> src = sources();
        if (src.isEmpty()) return;
        final File dir = bc.backupDir(sd, pkg);
        final int level = bc.level;
        final Map<String, String> info = new LinkedHashMap<>();
        String[] av = appInfo();
        info.put("package", pkg);
        info.put("app", av[0] == null ? pkg : av[0]);
        info.put("version", av[1] == null ? "" : av[1]);
        info.put("format", "2");
        info.put("created", new Date().toString());
        info.put("compression.level", String.valueOf(level));

        sRunning = true;
        sCancel = false;
        sStatus = "Backing up\u2026";
        sStatusKind = NEUTRAL;
        applyStatus();

        new Thread(new Runnable() {
            @Override public void run() {
                final long[] last = {0};
                Backups.Progress p = new Backups.Progress() {
                    @Override public void onProgress(int files, long bytes) {
                        long now = System.currentTimeMillis();
                        if (now - last[0] < 250) return;
                        last[0] = now;
                        post("Backing up\u2026 " + files + " files, "
                                + Formatter.formatShortFileSize(actOrNull(), bytes), NEUTRAL);
                    }
                    @Override public boolean cancelled() { return sCancel; }
                };
                String msg;
                int kind;
                try {
                    File out = Backups.create(dir, base, src, level, info, p);
                    if (out == null) { msg = "Cancelled."; kind = WARN; }
                    else {
                        msg = "Saved " + out.getName() + " ("
                                + Formatter.formatShortFileSize(actOrNull(), out.length()) + ")";
                        kind = OK;
                    }
                } catch (Throwable t) {
                    msg = "Backup failed: " + t.getMessage()
                            + (t.getMessage() != null && t.getMessage().contains("denied")
                            ? " (grant all-files access)" : "");
                    kind = WARN;
                }
                sRunning = false;
                sCancel = false;
                post(msg, kind);
            }
        }).start();
    }

    private static android.content.Context actOrNull() {
        BackupCard c = sCurrent;
        return c == null ? null : c.act;
    }

    private static void post(String msg, int kind) {
        post(msg, kind, false);
    }

    private static void post(String msg, int kind, final boolean configChanged) {
        sStatus = msg;
        sStatusKind = kind;
        final BackupCard c = sCurrent;
        if (c == null) return;
        c.act.runOnUiThread(new Runnable() {
            @Override public void run() {
                c.applyStatus();
                if (!sRunning) c.refresh();
                if (configChanged && c.onChanged != null) c.onChanged.run();
            }
        });
    }

    private void applyStatus() {
        if (status == null) return;
        status.setText(sStatus);
        status.setTextColor(sStatusKind == WARN ? ui.c.error
                : sStatusKind == OK ? ui.c.primary : ui.c.onSurfaceVariant);
        status.setVisibility(sStatus.length() == 0 ? View.GONE : View.VISIBLE);
        action.setText(sRunning ? "Cancel backup" : "Back up now");
    }

    /** Only folders that are redirected, included, and exist. */
    private List<Backups.Source> sources() {
        RedirectConfig rc = RedirectConfig.load(sd, pkg);
        List<Backups.Source> out = new ArrayList<>();
        if (bc.internal && rc.internal) add(out, "internal", rc.internalPath);
        if (bc.external && rc.external) add(out, "external", rc.externalPath);
        if (bc.storage && rc.storage) add(out, "storage", rc.storagePath);
        return out;
    }

    private void add(List<Backups.Source> out, String kind, String path) {
        File d = RedirectConfig.resolve(sd, pkg, path);
        if (d.isDirectory()) out.add(new Backups.Source(kind, d));
    }

    // --------------------------------------------------------------- restore
    private void restoreDialog(final Backups.Item it) {
        if (sRunning) { toast("Another backup or restore is running"); return; }
        final Backups.Info info;
        try {
            info = Backups.readInfo(it.file);
        } catch (Throwable t) {
            info(it.name, "This file can\u2019t be restored: " + t.getMessage());
            return;
        }
        if (info.pkg != null && !info.pkg.equals(pkg)) {
            info(it.name, "This backup belongs to " + info.pkg + ", not " + pkg + ".");
            return;
        }
        if (info.sources.isEmpty()) {
            info(it.name, "This backup has no folder information, so it can\u2019t be restored automatically.");
            return;
        }

        RedirectConfig rc = RedirectConfig.load(sd, pkg);
        StringBuilder sb = new StringBuilder("Restores the backup into the folders it was made from:\n");
        for (Map.Entry<String, String> e : info.sources.entrySet()) {
            String now = currentPath(rc, e.getKey());
            sb.append("\n\u2022 ").append(e.getKey()).append(" \u2192 ").append(e.getValue());
            if (now != null && !now.equals(e.getValue())) sb.append("\n   (now set to ").append(now).append(")");
        }
        sb.append("\n\nThis app\u2019s redirect folders are set to these paths too, so it finds its data "
                + "on the next launch. Force stop the app before restoring.");

        final CheckBox clean = new CheckBox(act);
        clean.setText("Delete the existing files in those folders first");
        clean.setTextColor(ui.c.onSurface);
        clean.setButtonTintList(ColorStateList.valueOf(ui.c.primary));
        clean.setPadding(ui.dp(4), ui.dp(12), 0, 0);

        ui.dialog(act, "Restore " + it.name + "?", sb.toString(), clean, "Restore", new Runnable() {
            @Override public void run() { startRestore(it, info, clean.isChecked()); }
        }, "Cancel");
    }

    private String currentPath(RedirectConfig rc, String kind) {
        String p = "internal".equals(kind) ? rc.internalPath
                : "external".equals(kind) ? rc.externalPath
                : "storage".equals(kind) ? rc.storagePath : null;
        return p == null ? null : RedirectConfig.resolve(sd, pkg, p).getAbsolutePath();
    }

    private void startRestore(final Backups.Item it, final Backups.Info info, final boolean clean) {
        if (sRunning) return;
        sRunning = true;
        sCancel = false;
        sStatus = "Restoring\u2026";
        sStatusKind = NEUTRAL;
        applyStatus();

        new Thread(new Runnable() {
            @Override public void run() {
                final long[] last = {0};
                Backups.Progress p = new Backups.Progress() {
                    @Override public void onProgress(int files, long bytes) {
                        long now = System.currentTimeMillis();
                        if (now - last[0] < 250) return;
                        last[0] = now;
                        post("Restoring\u2026 " + files + " files, "
                                + Formatter.formatShortFileSize(actOrNull(), bytes), NEUTRAL);
                    }
                    @Override public boolean cancelled() { return sCancel; }
                };
                String msg;
                int kind;
                boolean changed = false;
                try {
                    Backups.Result r = Backups.restore(it.file, info, clean, sd, p);
                    if (r == null) {
                        msg = "Restore cancelled \u2014 the folders are only partly restored.";
                        kind = WARN;
                    } else {
                        changed = applyRedirect(info);
                        msg = "Restored " + r.files + " files ("
                                + Formatter.formatShortFileSize(actOrNull(), r.bytes) + ")"
                                + (r.failed > 0 ? ", " + r.failed + " failed" : "")
                                + (changed ? ". Redirect folders set to the backup\u2019s paths."
                                : ". Couldn\u2019t update the redirect settings (grant all-files access).");
                        kind = r.failed > 0 || !changed ? WARN : OK;
                    }
                } catch (Throwable t) {
                    msg = "Restore failed: " + t.getMessage();
                    kind = WARN;
                }
                sRunning = false;
                sCancel = false;
                post(msg, kind, changed);
            }
        }).start();
    }

    /** Points this app's redirect settings at the folders the backup came from. */
    private boolean applyRedirect(Backups.Info info) {
        RedirectConfig cfg = RedirectConfig.load(sd, pkg);      // effective config -> becomes the override
        for (Map.Entry<String, String> e : info.sources.entrySet()) {
            String kind = e.getKey(), path = e.getValue();
            if ("internal".equals(kind)) { cfg.internalPath = path; cfg.internal = true; }
            else if ("external".equals(kind)) { cfg.externalPath = path; cfg.external = true; }
            else if ("storage".equals(kind)) { cfg.storagePath = path; cfg.storage = true; }
        }
        return cfg.save(sd, pkg);
    }

    private void info(String title, String message) {
        ui.dialog(act, title, message, null, "OK", null, null);
    }

    // ------------------------------------------------------- rename / delete
    private void renameDialog(final Backups.Item it) {
        final EditText e = ui.field(it.name, "Name");
        e.setSelectAllOnFocus(true);
        ui.dialog(act, "Rename backup", null, e, "Rename", new Runnable() {
            @Override public void run() {
                String err = Backups.rename(it, e.getText().toString());
                if (err != null) toast(err);
                refresh();
            }
        }, "Cancel");
    }

    private void deleteDialog(final Backups.Item it) {
        ui.dialog(act, "Delete backup?", it.name + ".zip will be permanently deleted.", null,
                "Delete", new Runnable() {
                    @Override public void run() {
                        if (!Backups.delete(it)) toast("Delete failed");
                        refresh();
                    }
                }, "Cancel");
    }

    // --------------------------------------------------------------- widgets
    private View toggleRow(String title, boolean init, final int which) {
        LinearLayout row = ui.hbox();
        row.addView(ui.text(title, 15, ui.c.onSurface, true), new LinearLayout.LayoutParams(0, -2, 1f));
        Switch s = ui.toggle(init);
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                if (building) return;
                if (which == 0) bc.internal = on;
                else if (which == 1) bc.external = on;
                else bc.storage = on;
                bc.save(sd, pkg);
            }
        });
        row.addView(s);
        return row;
    }

    private View fieldRow(String title, String value, final String def, final int which) {
        LinearLayout box = ui.vbox();
        TextView t = ui.text(title, 13, ui.c.onSurfaceVariant, true);
        t.setPadding(0, 0, 0, ui.dp(8));
        box.addView(t);
        EditText e = ui.field(value, def);
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable ed) {
                if (building) return;
                String v = ed.toString().trim();
                if (v.length() == 0) v = def;
                if (which == 0) bc.dir = v;
                else bc.name = v;
                bc.save(sd, pkg);
                if (which == 0) refresh();
            }
        });
        box.addView(e);
        return box;
    }

    private int levelIndex() {
        for (int i = 0; i < LEVELS.length; i++) if (LEVELS[i] == bc.level) return i;
        return 0;
    }

    private String levelLabel() {
        return "Compression: " + LEVEL_NAMES[levelIndex()] + "  \u00B7  tap to change";
    }

    /** {label, versionName} of the target app, null entries if it can't be read. */
    private String[] appInfo() {
        String label = null, ver = null;
        try {
            PackageManager pm = act.getPackageManager();
            label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            PackageInfo pi = pm.getPackageInfo(pkg, 0);
            ver = pi.versionName;
        } catch (Throwable ignored) {
        }
        return new String[]{label, ver};
    }

    private void toast(String s) {
        Toast.makeText(act, s, Toast.LENGTH_SHORT).show();
    }
}
