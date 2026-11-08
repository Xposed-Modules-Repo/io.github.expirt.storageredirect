package io.github.expirt.storageredirect;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** App picker: choose the defaults or an individual app to configure. */
public class MainActivity extends Activity {

    private static final class Entry {
        String label, pkg;
        Drawable icon;
    }

    private Ui ui;
    private String sd;
    private LinearLayout accessHolder;
    private EditText cfgField;
    private TextView cfgInUse;
    private TextView cfgStatus;
    private TextView empty;
    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> shown = new ArrayList<>();
    private Set<String> overridden = new HashSet<>();
    private String query = "";
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        Ui.applyTheme(this);
        super.onCreate(b);
        ui = new Ui(this);
        ui.window(this);
        sd = Environment.getExternalStorageDirectory().getAbsolutePath();

        ListView lv = new ListView(this);
        lv.setBackgroundColor(ui.c.surface);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        lv.setSelector(new android.graphics.drawable.ColorDrawable(0));
        lv.setClipToPadding(false);
        lv.setVerticalScrollBarEnabled(false);
        lv.setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(32));
        lv.addHeaderView(buildHeader(), null, false);
        adapter = new Adapter();
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                int i = pos - 1;   // one header
                if (i >= 0 && i < shown.size()) open(shown.get(i).pkg);
            }
        });
        setContentView(lv);
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ---------------------------------------------------------------- header
    private View buildHeader() {
        LinearLayout col = ui.vbox();
        col.setPadding(0, ui.dp(36), 0, 0);

        LinearLayout top = ui.hbox();
        top.addView(ui.iconTile(Ui.Icon.FOLDER, ui.c.primaryContainer, ui.c.onPrimaryContainer, 56));
        LinearLayout names = ui.vbox();
        names.setPadding(ui.dp(16), 0, 0, 0);
        names.addView(ui.text("Storage Redirect", 28, ui.c.onSurface, true));
        names.addView(ui.text("Redirect any app\u2019s storage", 14, ui.c.onSurfaceVariant, false));
        top.addView(names);
        top.setPadding(ui.dp(4), 0, 0, ui.dp(22));
        col.addView(top);

        accessHolder = ui.vbox();
        col.addView(accessHolder);

        col.addView(ui.label("Configuration folder"));
        LinearLayout cc = ui.card(ui.c.surfaceContainer);
        cc.addView(ui.text("Where the module keeps its settings (defaults, per-app settings, backup settings). "
                + "When you change it, the existing settings are moved there.", 12, ui.c.onSurfaceVariant, false));
        cfgField = ui.field(RedirectConfig.configSetting(sd), RedirectConfig.DEF_CONFIG_DIR);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = ui.dp(12);
        cc.addView(cfgField, flp);
        cfgInUse = ui.text("", 11, ui.c.onSurfaceVariant, false);
        cfgInUse.setPadding(ui.dp(4), ui.dp(6), 0, 0);
        cc.addView(cfgInUse);
        LinearLayout cbtn = ui.hbox();
        cbtn.addView(ui.button("Apply", Ui.FILLED, new View.OnClickListener() {
            @Override public void onClick(View v) { applyConfigFolder(cfgField.getText().toString()); }
        }));
        cbtn.addView(ui.button("Reset to default", Ui.TEXT, new View.OnClickListener() {
            @Override public void onClick(View v) {
                cfgField.setText(RedirectConfig.DEF_CONFIG_DIR);
                applyConfigFolder(RedirectConfig.DEF_CONFIG_DIR);
            }
        }));
        LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(-1, -2);
        cblp.topMargin = ui.dp(10);
        cc.addView(cbtn, cblp);
        cfgStatus = ui.text("", 12, ui.c.onSurfaceVariant, false);
        cfgStatus.setPadding(ui.dp(4), ui.dp(8), 0, 0);
        cfgStatus.setVisibility(View.GONE);
        cc.addView(cfgStatus);
        col.addView(cc);

        col.addView(ui.label("Defaults"));
        LinearLayout def = ui.hbox();
        def.setBackground(ui.tile(ui.c.secondaryContainer, 28, ui.c.onSecondaryContainer));
        def.setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16));
        def.setClickable(true);
        def.addView(ui.iconTile(Ui.Icon.SLIDERS, ui.c.tertiaryContainer, ui.c.onTertiaryContainer, 48));
        LinearLayout dt = ui.vbox();
        dt.setPadding(ui.dp(16), 0, ui.dp(8), 0);
        dt.addView(ui.text("Default settings", 17, ui.c.onSecondaryContainer, true));
        dt.addView(ui.text("Used by every app without its own settings", 13, ui.c.onSecondaryContainer, false));
        def.addView(dt, new LinearLayout.LayoutParams(0, -2, 1f));
        def.addView(ui.text("\u203A", 28, ui.c.onSecondaryContainer, false));
        def.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { open(null); }
        });
        col.addView(def);

        col.addView(ui.label("Apps"));
        EditText search = ui.field("", "Search apps");
        search.setOnFocusChangeListener(null);
        search.setBackground(ui.shape(ui.c.surfaceContainerHigh, 28));
        search.setPadding(ui.dp(22), ui.dp(14), ui.dp(22), ui.dp(14));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable e) {
                query = e.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.bottomMargin = ui.dp(10);
        col.addView(search, slp);

        empty = ui.text("Loading apps\u2026", 13, ui.c.onSurfaceVariant, false);
        empty.setPadding(ui.dp(8), ui.dp(8), 0, ui.dp(8));
        col.addView(empty);
        return col;
    }

    private void renderAccess(boolean ok) {
        accessHolder.removeAllViews();
        LinearLayout card = ui.card(ok ? ui.c.primaryContainer : ui.c.errorContainer);
        int fg = ok ? ui.c.onPrimaryContainer : ui.c.onErrorContainer;
        LinearLayout row = ui.hbox();
        row.addView(ui.iconTile(ok ? Ui.Icon.CHECK : Ui.Icon.WARNING,
                ok ? ui.c.primary : ui.c.error, ok ? ui.c.onPrimary : ui.c.onError, 44));
        LinearLayout tx = ui.vbox();
        tx.setPadding(ui.dp(16), 0, 0, 0);
        tx.addView(ui.text(ok ? "All-files access granted" : "All-files access missing", 16, fg, true));
        tx.addView(ui.text(ok ? "Settings and backups can be saved."
                : "Settings can\u2019t be saved until you allow it.", 13, fg, false));
        row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(row);
        if (!ok) {
            TextView grant = ui.button("Grant access", Ui.FILLED, new View.OnClickListener() {
                @Override public void onClick(View v) { requestAccess(); }
            });
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-2, -2);
            glp.topMargin = ui.dp(14);
            card.addView(grant, glp);
        }
        accessHolder.addView(card);
    }

    // ------------------------------------------------------------------ data
    private void loadApps() {
        new Thread(new Runnable() {
            @Override public void run() {
                PackageManager pm = getPackageManager();
                final List<Entry> list = new ArrayList<>();
                for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                    if (ai.packageName.equals(getPackageName())) continue;
                    boolean sys = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                            && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                    if (sys || pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
                    Entry e = new Entry();
                    e.pkg = ai.packageName;
                    e.label = String.valueOf(pm.getApplicationLabel(ai));
                    try { e.icon = pm.getApplicationIcon(ai); } catch (Throwable ignored) { }
                    list.add(e);
                }
                Collections.sort(list, new Comparator<Entry>() {
                    @Override public int compare(Entry a, Entry b) {
                        return a.label.compareToIgnoreCase(b.label);
                    }
                });
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        all.clear();
                        all.addAll(list);
                        applyFilter();
                    }
                });
            }
        }).start();
    }

    private void applyFilter() {
        shown.clear();
        for (Entry e : all)
            if (query.length() == 0 || e.label.toLowerCase(Locale.ROOT).contains(query)
                    || e.pkg.toLowerCase(Locale.ROOT).contains(query))
                shown.add(e);
        String msg = all.isEmpty() ? "Loading apps\u2026" : shown.isEmpty() ? "No matching apps" : "";
        empty.setText(msg);
        empty.setVisibility(msg.length() == 0 ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private void applyConfigFolder(String text) {
        String err = RedirectConfig.moveConfigDir(sd, text);
        cfgStatus.setVisibility(View.VISIBLE);
        if (err == null) {
            cfgStatus.setText("Settings are now in " + RedirectConfig.dir(sd).getAbsolutePath());
            cfgStatus.setTextColor(ui.c.primary);
        } else {
            cfgStatus.setText(err);
            cfgStatus.setTextColor(ui.c.error);
        }
        refresh();
    }

    private void refresh() {
        boolean ok = hasAccess();
        renderAccess(ok);
        if (cfgInUse != null) cfgInUse.setText("In use: " + RedirectConfig.dir(sd).getAbsolutePath());
        overridden = ok ? RedirectConfig.overriddenPackages(sd) : new HashSet<String>();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void open(String pkg) {
        Intent i = new Intent(this, SettingsActivity.class);
        if (pkg != null) i.putExtra(SettingsActivity.EXTRA_PKG, pkg);
        startActivity(i);
    }

    // --------------------------------------------------------------- adapter
    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View cv, ViewGroup parent) {
            Entry e = shown.get(i);
            int n = shown.size();
            float top = i == 0 ? 28 : 6, bottom = i == n - 1 ? 28 : 6;

            FrameLayout cell = new FrameLayout(MainActivity.this);
            cell.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
            cell.setPadding(0, 0, 0, ui.dp(2));

            LinearLayout row = ui.hbox();
            row.setBackground(ui.tile(ui.c.surfaceContainer, top, top, bottom, bottom, ui.c.onSurface));
            row.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));

            ImageView icon = new ImageView(MainActivity.this);
            if (e.icon != null) icon.setImageDrawable(e.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ui.squircle(icon, 15);                       // rounded-square app icon
            row.addView(icon, new LinearLayout.LayoutParams(ui.dp(46), ui.dp(46)));

            LinearLayout tx = ui.vbox();
            tx.setPadding(ui.dp(16), 0, ui.dp(8), 0);
            tx.addView(ui.text(e.label, 16, ui.c.onSurface, true));
            tx.addView(ui.text(e.pkg, 12, ui.c.onSurfaceVariant, false));
            row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));

            if (overridden.contains(e.pkg)) {
                TextView chip = ui.text("Custom", 12, ui.c.onPrimaryContainer, true);
                chip.setBackground(ui.shape(ui.c.primaryContainer, 10));
                chip.setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4));
                row.addView(chip);
            }
            cell.addView(row, new FrameLayout.LayoutParams(-1, -2));
            return cell;
        }
    }

    // ---------------------------------------------------------------- access
    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                return (Boolean) Environment.class.getMethod("isExternalStorageManager").invoke(null);
            } catch (Throwable t) {
                return false;
            }
        }
        if (Build.VERSION.SDK_INT >= 23)
            return checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE")
                    == PackageManager.PERMISSION_GRANTED;
        return true;
    }

    private void requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable t) {
                startActivity(new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"));
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE",
                    "android.permission.READ_EXTERNAL_STORAGE"}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        refresh();
    }
}
