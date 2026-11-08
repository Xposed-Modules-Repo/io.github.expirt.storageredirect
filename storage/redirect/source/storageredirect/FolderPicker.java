package io.github.expirt.storageredirect;

import android.app.Activity;
import android.app.Dialog;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * A small in-app folder browser, so a path can be chosen instead of typed. It lists the real
 * storage (the module app has all-files access), opens folders on tap, and returns either the
 * folder that is open ("Select this folder") or, when files are allowed, a tapped file.
 */
final class FolderPicker {

    interface Callback {
        void onPicked(String path);
    }

    private static final int MAX_ROWS = 400;

    private final Activity act;
    private final Ui ui;
    private final String root;
    private final boolean allowFiles;
    private final Callback cb;
    private File cur;
    private TextView pathView;
    private LinearLayout list;
    private ScrollView listScroll;
    private Dialog dialog;

    private FolderPicker(Activity act, Ui ui, String root, boolean allowFiles, Callback cb) {
        this.act = act;
        this.ui = ui;
        this.root = root;
        this.allowFiles = allowFiles;
        this.cb = cb;
    }

    /**
     * @param startText what is typed in the box now; the picker opens at the closest folder that
     *                  exists (a {placeholder} and everything after it is ignored)
     * @param root      the storage root, used when nothing usable is typed and by the Storage button
     * @param allowFiles also list files; tapping one picks it
     */
    static void show(Activity act, Ui ui, String title, String startText, String root,
                     boolean allowFiles, Callback cb) {
        new FolderPicker(act, ui, root, allowFiles, cb).open(title, startText);
    }

    /** The folder to open first: the typed path, or its nearest existing parent, or the root. */
    static File startDir(String root, String text) {
        String t = text == null ? "" : text.trim();
        int i = t.indexOf('{');
        if (i >= 0) t = t.substring(0, i);
        File f = t.length() == 0 ? new File(root) : (t.startsWith("/") ? new File(t) : new File(root, t));
        while (f != null && !f.isDirectory()) f = f.getParentFile();
        return f == null ? new File(root) : f;
    }

    private void open(String title, String startText) {
        cur = startDir(root, startText);

        LinearLayout body = ui.vbox();
        pathView = ui.text("", 12, ui.c.onSurfaceVariant, true);
        pathView.setPadding(0, 0, 0, ui.dp(6));
        body.addView(pathView);

        LinearLayout bar = ui.hbox();
        bar.addView(ui.button("Up", Ui.TEXT, new View.OnClickListener() {
            @Override public void onClick(View v) {
                File p = cur.getParentFile();
                if (p != null) {
                    cur = p;
                    refresh();
                }
            }
        }));
        bar.addView(ui.button("Storage", Ui.TEXT, new View.OnClickListener() {
            @Override public void onClick(View v) {
                cur = new File(root);
                refresh();
            }
        }));
        bar.addView(ui.button("New folder", Ui.TEXT, new View.OnClickListener() {
            @Override public void onClick(View v) { newFolder(); }
        }));
        body.addView(bar);

        listScroll = new ScrollView(act);
        list = ui.vbox();
        listScroll.addView(list);
        listScroll.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                v.getParent().requestDisallowInterceptTouchEvent(true);   // scroll the list, not the dialog
                return false;
            }
        });
        int h = Math.min(ui.dp(320), act.getResources().getDisplayMetrics().heightPixels * 45 / 100);
        body.addView(listScroll, new LinearLayout.LayoutParams(-1, h));
        refresh();

        dialog = ui.dialog(act, title, null, body, "Select this folder", new Runnable() {
            @Override public void run() { cb.onPicked(cur.getAbsolutePath()); }
        }, "Cancel");
    }

    private void refresh() {
        pathView.setText(cur.getAbsolutePath());
        list.removeAllViews();
        listScroll.scrollTo(0, 0);
        File[] kids = cur.listFiles();
        if (kids == null) {
            list.addView(note("Can\u2019t read this folder (no storage access?)."));
            return;
        }
        Arrays.sort(kids, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                boolean da = a.isDirectory(), db = b.isDirectory();
                if (da != db) return da ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        int shown = 0;
        for (File k : kids) {
            boolean dir = k.isDirectory();
            if (!dir && !allowFiles) continue;
            if (shown++ >= MAX_ROWS) {
                list.addView(note("\u2026 more entries not shown"));
                break;
            }
            list.addView(row(k, dir));
        }
        if (shown == 0) list.addView(note(allowFiles ? "Empty folder." : "No subfolders here."));
    }

    private TextView note(String s) {
        TextView t = ui.text(s, 13, ui.c.onSurfaceVariant, false);
        t.setPadding(ui.dp(4), ui.dp(12), ui.dp(4), ui.dp(12));
        return t;
    }

    private TextView row(final File k, final boolean dir) {
        TextView t = ui.text((dir ? "\uD83D\uDCC1  " : "\uD83D\uDCC4  ") + k.getName(), 15, ui.c.onSurface, false);
        t.setPadding(ui.dp(4), ui.dp(12), ui.dp(4), ui.dp(12));
        TypedValue tv = new TypedValue();
        if (act.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true))
            t.setBackgroundResource(tv.resourceId);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (dir) {
                    cur = k;
                    refresh();
                } else {
                    dialog.dismiss();
                    cb.onPicked(k.getAbsolutePath());
                }
            }
        });
        return t;
    }

    private void newFolder() {
        final EditText e = ui.field("", "Folder name");
        ui.dialog(act, "New folder", null, e, "Create", new Runnable() {
            @Override public void run() {
                String n = e.getText().toString().trim();
                if (n.length() == 0 || n.indexOf('/') >= 0) {
                    Toast.makeText(act, "Invalid name", Toast.LENGTH_SHORT).show();
                    return;
                }
                File d = new File(cur, n);
                if (d.isDirectory() || d.mkdirs()) {
                    cur = d;
                    refresh();
                } else {
                    Toast.makeText(act, "Couldn\u2019t create the folder", Toast.LENGTH_SHORT).show();
                }
            }
        }, "Cancel");
    }
}
