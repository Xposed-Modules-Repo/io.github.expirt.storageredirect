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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

/**
 * Neo Backup styled backup interface for Storage Redirect.
 * Provides backup archive creation, compression level control, backup listing,
 * restore, rename, and deletion with Neo Backup component styling.
 */
final class BackupCard {

    private static final int[] LEVELS = {6, 9, 1, 0};
    private static final String[] LEVEL_NAMES = {"Normal", "Maximum", "Fast", "None (store only)"};
    private static final int NEUTRAL = 0, WARN = 1, OK = 2;

    private static BackupCard sCurrent;
    private static volatile boolean sRunning;
    private static volatile boolean sCancel;
    private static volatile String sStatus = "";
    private static volatile int sStatusKind = NEUTRAL;

    private final Activity act;
    private final NeoUi ui;
    private final String sd;
    private final String pkg;
    private final BackupConfig bc;
    private final Runnable onChanged;

    private final LinearLayout root;
    private final LinearLayout listBox;
    private final TextView status;
    private final TextView action;
    private final TextView levelBtn;
    private final ProgressBar progress;
    private boolean building = true;

    BackupCard(Activity act, String sd, String pkg, Runnable onChanged) {
        this.onChanged = onChanged;
        this.act = act;
        this.ui = new NeoUi(act);
        this.sd = sd;
        this.pkg = pkg;
        this.bc = BackupConfig.load(sd, pkg);
        sCurrent = this;

        root = ui.vbox();

        // Intro card
        LinearLayout introCard = ui.card(ui.c.surfaceContainerLow, 18f);
        LinearLayout introRow = ui.hbox();
        ImageView zipIcon = new ImageView(act);
        zipIcon.setImageDrawable(Phosphor.get(Phosphor.FILE_ZIP, ui.c.primary, ui.dp(28)));
        introRow.addView(zipIcon, new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));

        TextView introText = ui.text("Backups create .zip archives of this app\u2019s redirected folders. "
                + "Restoring extracts the files and syncs redirection folders automatically.",
                13, ui.c.onSurfaceVariant, false);
        introText.setLineSpacing(0, 1.15f);
        introText.setPadding(ui.dp(12), 0, 0, 0);
        introRow.addView(introText, new LinearLayout.LayoutParams(0, -2, 1f));
        introCard.addView(introRow);
        root.addView(introCard);

        // Include switches
        root.addView(ui.sectionLabel("Folders to include"));
        List<View> inc = new ArrayList<View>();
        inc.add(toggleRow("Internal data folder", bc.internal, 0));
        inc.add(toggleRow("App external data folder", bc.external, 1));
        inc.add(toggleRow("Main storage folder", bc.storage, 2));
        root.addView(ui.group(inc));

        // Location & Template
        root.addView(ui.sectionLabel("Backup destination & name"));
        List<View> where = new ArrayList<View>();
        where.add(fieldRow("Backup directory", bc.dir, BackupConfig.DEF_DIR, 0));
        where.add(fieldRow("File name template  ({pkg} {app} {ver} {date} {time})", bc.name, BackupConfig.DEF_NAME, 1));
        root.addView(ui.group(where));

        // Execution & Settings card
        LinearLayout actions = ui.card(ui.c.surfaceContainer, 20f);

        levelBtn = ui.button(levelLabel(), NeoUi.TONAL, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bc.level = LEVELS[(levelIndex() + 1) % LEVELS.length];
                bc.save(BackupCard.this.sd, BackupCard.this.pkg);
                levelBtn.setText(levelLabel());
            }
        });
        actions.addView(levelBtn, new LinearLayout.LayoutParams(-1, -2));

        action = ui.buttonWithIcon("Back up now", Phosphor.FILE_ZIP, NeoUi.FILLED, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sRunning) {
                    sCancel = true;
                    sStatus = "Cancelling\u2026";
                    applyStatus();
                } else {
                    askNameAndStart();
                }
            }
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = ui.dp(8);
        actions.addView(action, alp);

        progress = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, ui.dp(4));
        plp.topMargin = ui.dp(8);
        actions.addView(progress, plp);

        status = ui.text("", 12, ui.c.onSurfaceVariant, false);
        status.setPadding(ui.dp(4), ui.dp(8), 0, 0);
        actions.addView(status);

        LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) actions.getLayoutParams();
        clp.topMargin = ui.dp(10);
        root.addView(actions);

        // Saved backups section
        root.addView(ui.sectionLabel("Saved backups"));
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
            LinearLayout none = ui.card(ui.c.surfaceContainerLow, 18f);
            none.setGravity(Gravity.CENTER);
            none.setPadding(ui.dp(16), ui.dp(24), ui.dp(16), ui.dp(24));

            ImageView emptyIcon = new ImageView(act);
            emptyIcon.setImageDrawable(Phosphor.get(Phosphor.FILE_ZIP, ui.c.outlineVariant, ui.dp(36)));
            none.addView(emptyIcon, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)));

            TextView emptyText = ui.text("No backups found for this application", 14, ui.c.onSurfaceVariant, false);
            emptyText.setPadding(0, ui.dp(8), 0, 0);
            none.addView(emptyText);
            listBox.addView(none);
            return;
        }

        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        List<View> rows = new ArrayList<View>();
        for (final Backups.Item it : items) {
            LinearLayout row = ui.vbox();

            LinearLayout topRow = ui.hbox();
            ImageView itemIcon = new ImageView(act);
            itemIcon.setImageDrawable(Phosphor.get(Phosphor.FILE_ZIP, ui.c.primary, ui.dp(22)));
            topRow.addView(itemIcon, new LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)));

            LinearLayout titleBox = ui.vbox();
            titleBox.setPadding(ui.dp(10), 0, 0, 0);
            titleBox.addView(ui.text(it.name, 15, ui.c.onSurface, true));
            titleBox.addView(ui.text(Formatter.formatShortFileSize(act, it.size) + "  \u00B7  "
                    + df.format(new Date(it.time)), 12, ui.c.onSurfaceVariant, false));
            topRow.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(topRow);

            LinearLayout btns = ui.hbox();
            btns.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

            TextView restoreBtn = ui.buttonWithIcon("Restore", Phosphor.RESTORE, NeoUi.TONAL, new View.OnClickListener() {
                @Override public void onClick(View v) { restoreDialog(it); }
            });
            restoreBtn.setMinHeight(ui.dp(36));
            restoreBtn.setTextSize(12);

            TextView renameBtn = ui.buttonWithIcon("Rename", Phosphor.PENCIL, NeoUi.TEXT, new View.OnClickListener() {
                @Override public void onClick(View v) { renameDialog(it); }
            });
            renameBtn.setMinHeight(ui.dp(36));
            renameBtn.setTextSize(12);

            TextView deleteBtn = ui.buttonWithIcon("Delete", Phosphor.TRASH, NeoUi.TEXT_ERROR, new View.OnClickListener() {
                @Override public void onClick(View v) { deleteDialog(it); }
            });
            deleteBtn.setMinHeight(ui.dp(36));
            deleteBtn.setTextSize(12);

            btns.addView(renameBtn);
            btns.addView(deleteBtn);
            btns.addView(restoreBtn);

            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
            blp.topMargin = ui.dp(8);
            row.addView(btns, blp);
            rows.add(row);
        }
        listBox.addView(ui.group(rows));
    }

    private void askNameAndStart() {
        List<Backups.Source> src = sources();
        if (src.isEmpty()) {
            sStatus = "Nothing to back up \u2014 ensure redirected folders exist and are included.";
            sStatusKind = WARN;
            applyStatus();
            return;
        }
        String[] av = appInfo();
        final EditText e = ui.field(Backups.expand(bc.name, pkg, av[0], av[1], new Date()), "Backup name");
        e.setSelectAllOnFocus(true);
        ui.dialog(act, Phosphor.FILE_ZIP, "Backup name", "Enter a name for the new backup archive:", e,
                "Back up", new Runnable() {
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
        final Map<String, String> info = new LinkedHashMap<String, String>();
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
        if (sStatus.length() == 0) {
            status.setVisibility(View.GONE);
        } else if (status.getVisibility() != View.VISIBLE) {
            status.setAlpha(0f);
            status.setVisibility(View.VISIBLE);
            status.animate().alpha(1f).setDuration(200).start();
        }

        if (progress != null) {
            boolean wasVisible = progress.getVisibility() == View.VISIBLE;
            if (sRunning && !wasVisible) {
                progress.setAlpha(0f);
                progress.setVisibility(View.VISIBLE);
                progress.animate().alpha(1f).setDuration(200).start();
            } else if (!sRunning && wasVisible) {
                progress.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
                    @Override public void run() { progress.setVisibility(View.GONE); }
                }).start();
            }
        }
        action.setText(sRunning ? "Cancel backup" : "Back up now");
    }

    private List<Backups.Source> sources() {
        RedirectConfig rc = RedirectConfig.load(sd, pkg);
        List<Backups.Source> out = new ArrayList<Backups.Source>();
        if (bc.internal && rc.internal) add(out, "internal", rc.internalPath);
        if (bc.external && rc.external) add(out, "external", rc.externalPath);
        if (bc.storage && rc.storage) add(out, "storage", rc.storagePath);
        return out;
    }

    private void add(List<Backups.Source> out, String kind, String path) {
        File d = RedirectConfig.resolve(sd, pkg, path);
        if (d.isDirectory()) out.add(new Backups.Source(kind, d));
    }

    private void restoreDialog(final Backups.Item it) {
        if (sRunning) { toast("Another backup or restore operation is running"); return; }
        final Backups.Info info;
        try {
            info = Backups.readInfo(it.file);
        } catch (Throwable t) {
            info(it.name, "This backup file cannot be restored: " + t.getMessage());
            return;
        }
        if (info.pkg != null && !info.pkg.equals(pkg)) {
            info(it.name, "This backup belongs to " + info.pkg + ", not " + pkg + ".");
            return;
        }
        if (info.sources.isEmpty()) {
            info(it.name, "This backup contains no folder metadata, so it cannot be restored automatically.");
            return;
        }

        RedirectConfig rc = RedirectConfig.load(sd, pkg);
        StringBuilder sb = new StringBuilder("Restore will extract files back into the original folders:\n");
        for (Map.Entry<String, String> e : info.sources.entrySet()) {
            String now = currentPath(rc, e.getKey());
            sb.append("\n\u2022 ").append(e.getKey()).append(" \u2192 ").append(e.getValue());
            if (now != null && !now.equals(e.getValue())) sb.append("\n   (currently set to ").append(now).append(")");
        }
        sb.append("\n\nThis app\u2019s redirection paths will also be updated to match. Force stop the app before restoring.");

        final CheckBox clean = new CheckBox(act);
        clean.setText("Delete existing files in those folders first");
        clean.setTextColor(ui.c.onSurface);
        clean.setButtonTintList(ColorStateList.valueOf(ui.c.primary));
        clean.setPadding(ui.dp(4), ui.dp(12), 0, 0);

        ui.dialog(act, Phosphor.RESTORE, "Restore " + it.name + "?", sb.toString(), clean, "Restore", new Runnable() {
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
                        msg = "Restore cancelled \u2014 folders may be only partially restored.";
                        kind = WARN;
                    } else {
                        changed = applyRedirect(info);
                        msg = "Restored " + r.files + " files ("
                                + Formatter.formatShortFileSize(actOrNull(), r.bytes) + ")"
                                + (r.failed > 0 ? ", " + r.failed + " failed" : "")
                                + (changed ? ". Redirection folders updated."
                                : ". Could not update redirection settings (grant all-files access).");
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

    private boolean applyRedirect(Backups.Info info) {
        RedirectConfig cfg = RedirectConfig.load(sd, pkg);
        for (Map.Entry<String, String> e : info.sources.entrySet()) {
            String kind = e.getKey(), path = e.getValue();
            if ("internal".equals(kind)) { cfg.internalPath = path; cfg.internal = true; }
            else if ("external".equals(kind)) { cfg.externalPath = path; cfg.external = true; }
            else if ("storage".equals(kind)) { cfg.storagePath = path; cfg.storage = true; }
        }
        return cfg.save(sd, pkg);
    }

    private void info(String title, String message) {
        ui.dialog(act, Phosphor.INFO, title, message, null, "OK", null, null);
    }

    private void renameDialog(final Backups.Item it) {
        final EditText e = ui.field(it.name, "Name");
        e.setSelectAllOnFocus(true);
        ui.dialog(act, Phosphor.PENCIL, "Rename backup", "Enter a new name for this backup:", e, "Rename", new Runnable() {
            @Override public void run() {
                String err = Backups.rename(it, e.getText().toString());
                if (err != null) toast(err);
                refresh();
            }
        }, "Cancel");
    }

    private void deleteDialog(final Backups.Item it) {
        ui.dialog(act, Phosphor.TRASH, "Delete backup?",
                "Are you sure you want to delete \u201C" + it.name + ".zip\u201D? This action cannot be undone.",
                null, "Delete", new Runnable() {
            @Override public void run() {
                if (!Backups.delete(it)) toast("Delete failed");
                refresh();
            }
        }, "Cancel");
    }

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
        return "Compression: " + LEVEL_NAMES[levelIndex()] + "  \u00B7  Tap to change";
    }

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
