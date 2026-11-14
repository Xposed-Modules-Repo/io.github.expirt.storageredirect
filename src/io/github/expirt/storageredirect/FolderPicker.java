package io.github.expirt.storageredirect;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * FolderPicker: Interactive Material 3 directory browser and selector.
 * Allows users to browse directories on shared storage, create new subfolders,
 * navigate up/down, and select target container directories.
 */
public class FolderPicker {

    public interface OnFolderSelectedListener {
        void onFolderSelected(String path);
    }

    public static void show(final Activity act, final NeoUi ui, final String initialVal,
                            final OnFolderSelectedListener listener) {
        final Dialog d = new Dialog(act);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final String sd = Environment.getExternalStorageDirectory().getAbsolutePath();
        File startDir = null;
        if (initialVal != null && !initialVal.trim().isEmpty()) {
            startDir = RedirectConfig.resolve(sd, null, initialVal.trim());
        }
        if (startDir == null || !startDir.isDirectory()) {
            startDir = new File(sd);
        }

        final File[] currentDir = new File[]{startDir};

        final LinearLayout box = ui.vbox();
        box.setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(16));
        box.setBackground(ui.shape(ui.c.surfaceContainerHigh, 28));

        // Header icon & title
        LinearLayout head = ui.hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(act);
        iv.setImageDrawable(Phosphor.get(Phosphor.FOLDER, ui.c.primary, ui.dp(28)));
        head.addView(iv, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)));

        TextView title = ui.text("Choose Folder", 20, ui.c.onSurface, true);
        title.setPadding(ui.dp(12), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(head);

        // Current Path Display
        final TextView pathView = ui.text("", 12, ui.c.primary, true);
        pathView.setPadding(0, ui.dp(10), 0, ui.dp(6));
        box.addView(pathView);

        // Folder List container inside ScrollView
        final ScrollView sv = new ScrollView(act);
        sv.setFillViewport(true);
        final LinearLayout listContainer = ui.vbox();
        sv.addView(listContainer, new ViewGroup.LayoutParams(-1, -2));
        box.addView(sv, new LinearLayout.LayoutParams(-1, ui.dp(240)));

        // Navigation bar: Up, New Folder
        LinearLayout navBar = ui.hbox();
        navBar.setPadding(0, ui.dp(10), 0, 0);

        final TextView btnUp = ui.buttonWithIcon("Up", Phosphor.ARROW_UP, NeoUi.TONAL, null);
        final TextView btnNew = ui.buttonWithIcon("New Folder", Phosphor.PLUS_CIRCLE, NeoUi.TONAL, null);
        navBar.addView(btnUp);
        navBar.addView(btnNew);
        box.addView(navBar);

        // Actions: Cancel, Select This Folder
        LinearLayout actions = ui.hbox();
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, ui.dp(12), 0, 0);

        TextView btnCancel = ui.button("Cancel", NeoUi.TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        TextView btnSelect = ui.buttonWithIcon("Select This Folder", Phosphor.CHECK, NeoUi.FILLED, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                if (listener != null) {
                    String sel = currentDir[0].getAbsolutePath();
                    if (sel.equals(sd)) {
                        listener.onFolderSelected(sel);
                    } else if (sel.startsWith(sd + "/")) {
                        listener.onFolderSelected(sel.substring(sd.length() + 1));
                    } else {
                        listener.onFolderSelected(sel);
                    }
                }
            }
        });
        actions.addView(btnCancel);
        actions.addView(btnSelect);
        box.addView(actions);

        // Loader function
        final Runnable loadList = new Runnable() {
            @Override
            public void run() {
                File dir = currentDir[0];
                pathView.setText(dir.getAbsolutePath());

                boolean isSdRoot = dir.getAbsolutePath().equals(sd) || dir.getParentFile() == null;
                btnUp.setEnabled(!isSdRoot);
                btnUp.setAlpha(!isSdRoot ? 1f : 0.4f);

                listContainer.removeAllViews();

                File[] subdirs = dir.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return pathname.isDirectory() && !pathname.getName().startsWith(".");
                    }
                });

                if (subdirs == null || subdirs.length == 0) {
                    TextView empty = ui.text("No subfolders", 13, ui.c.onSurfaceVariant, false);
                    empty.setPadding(ui.dp(12), ui.dp(16), ui.dp(12), ui.dp(16));
                    listContainer.addView(empty);
                } else {
                    List<File> list = new ArrayList<File>(Arrays.asList(subdirs));
                    Collections.sort(list, new Comparator<File>() {
                        @Override
                        public int compare(File o1, File o2) {
                            return o1.getName().compareToIgnoreCase(o2.getName());
                        }
                    });

                    List<View> itemViews = new ArrayList<View>();
                    for (final File sub : list) {
                        LinearLayout row = ui.hbox();
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        ImageView fi = new ImageView(act);
                        fi.setImageDrawable(Phosphor.get(Phosphor.FOLDER, ui.c.primary, ui.dp(20)));
                        row.addView(fi, new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)));

                        TextView name = ui.text(sub.getName(), 14, ui.c.onSurface, false);
                        name.setPadding(ui.dp(10), 0, 0, 0);
                        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

                        row.setClickable(true);
                        row.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                currentDir[0] = sub;
                                run();
                            }
                        });
                        itemViews.add(row);
                    }
                    listContainer.addView(ui.group(itemViews));
                }
            }
        };

        btnUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File parent = currentDir[0].getParentFile();
                if (parent != null && (currentDir[0].getAbsolutePath().startsWith(sd) || parent.exists())) {
                    currentDir[0] = parent;
                    loadList.run();
                }
            }
        });

        btnNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText input = ui.field("", "Folder name");
                ui.dialog(act, Phosphor.PLUS_CIRCLE, "Create Subfolder",
                        "Create new folder inside " + currentDir[0].getName() + ":", input, "Create",
                        new Runnable() {
                            @Override
                            public void run() {
                                String n = input.getText().toString().trim();
                                if (!n.isEmpty()) {
                                    File nf = new File(currentDir[0], n);
                                    if (!nf.exists()) nf.mkdirs();
                                    currentDir[0] = nf;
                                    loadList.run();
                                }
                            }
                        }, "Cancel");
            }
        });

        loadList.run();
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
    }
}
