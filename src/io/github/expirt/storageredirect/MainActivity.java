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
import android.text.format.Formatter;
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
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * MainActivity: Transformed to Neo Backup's navigation architecture and component design.
 * Features a 4-tab Navigation Suite (Apps, Redirection, Backups, Settings),
 * TopBar with live search and refresh, and 100% preservation of all Storage Redirect operations.
 */
public class MainActivity extends Activity {

    public static final class Entry {
        public String label, pkg;
        public Drawable icon;
    }

    private NeoUi ui;
    private String sd;

    // Navigation & Pages
    private NeoUi.TopBar topBar;
    private NeoUi.BottomNav bottomNav;
    private NeoUi.SwipePager pager;
    private int currentTab = 0;

    // Tab 0: Apps
    private LinearLayout appsPage;
    private LinearLayout accessHolder;
    private TextView appsCountView;
    private TextView emptyView;
    private ListView appsListView;
    private final List<Entry> allApps = new ArrayList<Entry>();
    private final List<Entry> filteredApps = new ArrayList<Entry>();
    private Set<String> overridden = new HashSet<String>();
    private String query = "";
    private AppAdapter appAdapter;

    // Tab 1: Redirection
    private ScrollView redirectionPage;

    // Tab 2: Backups
    private ScrollView backupsPage;
    private LinearLayout backupsListHolder;

    // Tab 3: Settings
    private ScrollView settingsPage;
    private EditText cfgField;
    private TextView cfgInUse;
    private TextView cfgStatus;

    @Override
    protected void onCreate(Bundle b) {
        NeoUi.applyTheme(this);
        super.onCreate(b);
        RedirectConfig.init(this);
        ui = new NeoUi(this);
        ui.setupWindow(this);
        sd = Environment.getExternalStorageDirectory().getAbsolutePath();

        LinearLayout root = ui.vbox();
        root.setBackgroundColor(ui.c.surface);

        // ---- Neo TopBar
        topBar = ui.topBar("Applications", new NeoUi.SearchListener() {
            @Override
            public void onQuery(String q) {
                query = q.trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        }, new Runnable() {
            @Override
            public void run() {
                refreshAll();
            }
        }, new Runnable() {
            @Override
            public void run() {
                switchToTab(3); // Navigate to Settings
            }
        });
        root.addView(topBar.root);

        // ---- Neo SwipePager (Horizontal Swipe Paging)
        pager = new NeoUi.SwipePager(this);
        root.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Build Pages
        buildAppsPage();
        buildRedirectionPage();
        buildBackupsPage();
        buildSettingsPage();

        // Add pages to swipe pager
        pager.addView(appsPage);
        pager.addView(redirectionPage);
        pager.addView(backupsPage);
        pager.addView(settingsPage);

        pager.setOnPageChangeListener(new NeoUi.SwipePager.OnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                onPageChanged(position);
            }
        });

        // ---- Neo Navigation Suite (Bottom Navigation)
        String[] tabLabels = new String[]{"Apps", "Rules", "Backups", "Settings"};
        int[] tabIcons = new int[]{Phosphor.HOUSE, Phosphor.SLIDERS, Phosphor.FILE_ZIP, Phosphor.GEAR};
        bottomNav = ui.bottomNav(tabLabels, tabIcons, new NeoUi.BottomNav.OnTabSelectedListener() {
            @Override
            public void onTabSelected(int index) {
                switchToTab(index);
            }
        });
        root.addView(bottomNav.root);

        setContentView(root);

        // Initial setup
        pager.setCurrentPage(0);
        onPageChanged(0);
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    private void switchToTab(int index) {
        if (pager != null) {
            pager.smoothScrollToPage(index);
        }
        onPageChanged(index);
    }

    private void onPageChanged(int index) {
        currentTab = index;
        bottomNav.select(index);

        final String newTitle;
        switch (index) {
            case 0:
                newTitle = "Applications";
                break;
            case 1:
                newTitle = "Redirection";
                break;
            case 2:
                newTitle = "Backups";
                loadGlobalBackups();
                break;
            case 3:
            default:
                newTitle = "Settings";
                break;
        }

        // Title smooth crossfade
        topBar.titleView.animate().alpha(0f).setDuration(120).withEndAction(new Runnable() {
            @Override
            public void run() {
                topBar.titleView.setText(newTitle);
                topBar.titleView.animate().alpha(1f).setDuration(160).start();
            }
        }).start();
    }

    private void refreshAll() {
        boolean ok = hasAccess();
        renderAccessBanner(ok);
        if (cfgInUse != null) {
            cfgInUse.setText("In use: " + RedirectConfig.dir(sd).getAbsolutePath());
        }
        overridden = ok ? RedirectConfig.overriddenPackages(sd) : new HashSet<String>();
        if (appAdapter != null) appAdapter.notifyDataSetChanged();
        if (currentTab == 2) loadGlobalBackups();
    }

    // =========================================================================
    // TAB 0: Applications
    // =========================================================================
    private void buildAppsPage() {
        appsPage = ui.vbox();
        appsPage.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

        // Header section inside a ListView header
        LinearLayout listHeader = ui.vbox();
        listHeader.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8));

        accessHolder = ui.vbox();
        listHeader.addView(accessHolder);

        LinearLayout statsRow = ui.hbox();
        appsCountView = ui.text("Loading applications\u2026", 13, ui.c.onSurfaceVariant, true);
        statsRow.addView(appsCountView, new LinearLayout.LayoutParams(0, -2, 1f));
        listHeader.addView(statsRow);

        appsListView = new ListView(this);
        appsListView.setBackgroundColor(ui.c.surface);
        appsListView.setDivider(null);
        appsListView.setDividerHeight(0);
        appsListView.setSelector(new android.graphics.drawable.ColorDrawable(0));
        appsListView.setClipToPadding(false);
        appsListView.setVerticalScrollBarEnabled(false);
        appsListView.setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(24));
        appsListView.addHeaderView(listHeader, null, false);

        appAdapter = new AppAdapter();
        appsListView.setAdapter(appAdapter);
        appsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int i = position - 1; // Subtract 1 for the header
                if (i >= 0 && i < filteredApps.size()) {
                    openAppSettings(filteredApps.get(i).pkg);
                }
            }
        });

        emptyView = ui.text("No matching applications", 14, ui.c.onSurfaceVariant, false);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, ui.dp(40), 0, 0);
        emptyView.setVisibility(View.GONE);

        appsPage.addView(appsListView, new LinearLayout.LayoutParams(-1, -1));
    }

    private void renderAccessBanner(boolean ok) {
        accessHolder.removeAllViews();
        if (ok) return;

        LinearLayout card = ui.card(ui.c.errorContainer, 18f);
        int fg = ui.c.onErrorContainer;

        LinearLayout row = ui.hbox();
        ImageView alertIcon = new ImageView(this);
        alertIcon.setImageDrawable(Phosphor.get(Phosphor.CIRCLE_WAVY_WARNING, ui.c.error, ui.dp(28)));
        row.addView(alertIcon, new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));

        LinearLayout tx = ui.vbox();
        tx.setPadding(ui.dp(12), 0, 0, 0);
        tx.addView(ui.text("All-files storage access missing", 15, fg, true));
        tx.addView(ui.text("Redirection settings and backups cannot be saved until permission is allowed.", 12, fg, false));
        row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(row);

        TextView grantBtn = ui.buttonWithIcon("Grant storage access", Phosphor.CHECK_CIRCLE, NeoUi.FILLED, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestAccess();
            }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-2, -2);
        glp.topMargin = ui.dp(12);
        card.addView(grantBtn, glp);

        LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) card.getLayoutParams();
        clp.bottomMargin = ui.dp(10);
        accessHolder.addView(card);
    }

    private void loadApps() {
        topBar.setProgress(true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = getPackageManager();
                final List<Entry> list = new ArrayList<Entry>();
                for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                    if (ai.packageName.equals(getPackageName())) continue;
                    boolean sys = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                            && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                    if (sys || pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
                    Entry e = new Entry();
                    e.pkg = ai.packageName;
                    e.label = String.valueOf(pm.getApplicationLabel(ai));
                    try {
                        e.icon = pm.getApplicationIcon(ai);
                    } catch (Throwable ignored) {}
                    list.add(e);
                }
                Collections.sort(list, new Comparator<Entry>() {
                    @Override
                    public int compare(Entry a, Entry b) {
                        return a.label.compareToIgnoreCase(b.label);
                    }
                });
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        topBar.setProgress(false);
                        allApps.clear();
                        allApps.addAll(list);
                        applyFilter();
                        appsListView.setAlpha(0f);
                        appsListView.animate().alpha(1f).setDuration(260)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                    }
                });
            }
        }).start();
    }

    private void applyFilter() {
        filteredApps.clear();
        for (Entry e : allApps) {
            if (query.isEmpty() || e.label.toLowerCase(Locale.ROOT).contains(query)
                    || e.pkg.toLowerCase(Locale.ROOT).contains(query)) {
                filteredApps.add(e);
            }
        }
        if (allApps.isEmpty()) {
            appsCountView.setText("Loading applications\u2026");
        } else if (filteredApps.isEmpty()) {
            appsCountView.setText("No matching applications");
        } else if (query.isEmpty()) {
            appsCountView.setText(allApps.size() + " installed applications");
        } else {
            appsCountView.setText(filteredApps.size() + " of " + allApps.size() + " applications");
        }
        appAdapter.notifyDataSetChanged();
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredApps.size(); }
        @Override public Object getItem(int i) { return filteredApps.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View cv, ViewGroup parent) {
            Entry e = filteredApps.get(i);
            int n = filteredApps.size();
            float top = (i == 0) ? 20f : 4f;
            float bottom = (i == n - 1) ? 20f : 4f;

            FrameLayout cell = new FrameLayout(MainActivity.this);
            cell.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
            cell.setPadding(0, 0, 0, ui.dp(2));

            LinearLayout row = ui.hbox();
            row.setBackground(ui.tile(ui.c.surfaceContainer, top, top, bottom, bottom, ui.c.onSurface));
            row.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));

            ImageView icon = new ImageView(MainActivity.this);
            if (e.icon != null) {
                icon.setImageDrawable(e.icon);
            } else {
                icon.setImageDrawable(Phosphor.get(Phosphor.HOUSE, ui.c.primary, ui.dp(28)));
            }
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ui.squircle(icon, 14);
            row.addView(icon, new LinearLayout.LayoutParams(ui.dp(46), ui.dp(46)));

            LinearLayout tx = ui.vbox();
            tx.setPadding(ui.dp(16), 0, ui.dp(8), 0);
            tx.addView(ui.text(e.label, 15, ui.c.onSurface, true));
            tx.addView(ui.text(e.pkg, 12, ui.c.onSurfaceVariant, false));
            row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));

            if (overridden.contains(e.pkg)) {
                TextView chip = ui.chip("Custom", ui.c.primaryContainer, ui.c.onPrimaryContainer);
                row.addView(chip);
            }

            cell.addView(row, new FrameLayout.LayoutParams(-1, -2));
            return cell;
        }
    }

    // =========================================================================
    // TAB 1: Redirection & Rules Overview
    // =========================================================================
    private void buildRedirectionPage() {
        redirectionPage = new ScrollView(this);
        redirectionPage.setFillViewport(true);
        redirectionPage.setVerticalScrollBarEnabled(false);

        LinearLayout col = ui.vbox();
        col.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(40));

        // Default settings shortcut card
        col.addView(ui.sectionLabel("Global redirection"));
        LinearLayout defCard = ui.card(ui.c.primaryContainer, 20f);
        defCard.setClickable(true);
        LinearLayout defRow = ui.hbox();

        FrameLayout iconFrame = new FrameLayout(this);
        iconFrame.setBackground(ui.shape(ui.c.onPrimaryContainer, 14f));
        ImageView defIcon = new ImageView(this);
        defIcon.setImageDrawable(Phosphor.get(Phosphor.SLIDERS, ui.c.primaryContainer, ui.dp(24)));
        iconFrame.addView(defIcon, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        defRow.addView(iconFrame, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));

        LinearLayout dt = ui.vbox();
        dt.setPadding(ui.dp(14), 0, ui.dp(8), 0);
        dt.addView(ui.text("Default configuration", 17, ui.c.onPrimaryContainer, true));
        dt.addView(ui.text("Configure global folder paths, standard path faking, and rules", 12, ui.c.onPrimaryContainer, false));
        defRow.addView(dt, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView arrow = new ImageView(this);
        arrow.setImageDrawable(Phosphor.get(Phosphor.ARROW_RIGHT, ui.c.onPrimaryContainer, ui.dp(20)));
        defRow.addView(arrow, new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)));

        defCard.addView(defRow);
        defCard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openAppSettings(null); }
        });
        col.addView(defCard);

        // Active Default Targets Summary
        col.addView(ui.sectionLabel("Default destinations"));
        RedirectConfig defCfg = RedirectConfig.load(sd, null);
        List<View> destRows = new ArrayList<View>();
        destRows.add(infoRow("Internal data", defCfg.internal ? "Active \u2192 " + defCfg.internalPath : "Disabled"));
        destRows.add(infoRow("App external data", defCfg.external ? "Active \u2192 " + defCfg.externalPath : "Disabled"));
        destRows.add(infoRow("Main storage", defCfg.storage ? "Active \u2192 " + defCfg.storagePath : "Disabled"));
        destRows.add(infoRow("Fake standard paths", defCfg.fakePaths ? "Enabled (Virtual filesystem)" : "Off"));
        col.addView(ui.group(destRows));

        // Path-to-Path Quick Summary
        col.addView(ui.sectionLabel("Custom path rules"));
        LinearLayout rulesCard = ui.card(ui.c.surfaceContainer, 20f);
        rulesCard.addView(ui.text("Global Path-to-Path Rules", 15, ui.c.onSurface, true));

        String rulesText = defCfg.pathRules.trim();
        int ruleCount = rulesText.isEmpty() ? 0 : rulesText.split("\n").length;
        TextView ruleSummary = ui.text(ruleCount + " active global redirection rules defined.",
                13, ui.c.onSurfaceVariant, false);
        ruleSummary.setPadding(0, ui.dp(4), 0, ui.dp(12));
        rulesCard.addView(ruleSummary);

        TextView manageRulesBtn = ui.buttonWithIcon("Edit rules in defaults", Phosphor.PENCIL, NeoUi.TONAL, new View.OnClickListener() {
            @Override public void onClick(View v) { openAppSettings(null); }
        });
        rulesCard.addView(manageRulesBtn);
        col.addView(rulesCard);

        redirectionPage.addView(col);
    }

    private View infoRow(String title, String value) {
        LinearLayout row = ui.hbox();
        TextView t = ui.text(title, 14, ui.c.onSurface, true);
        TextView v = ui.text(value, 13, ui.c.onSurfaceVariant, false);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(v);
        return row;
    }

    // =========================================================================
    // TAB 2: Backups Overview
    // =========================================================================
    private void buildBackupsPage() {
        backupsPage = new ScrollView(this);
        backupsPage.setFillViewport(true);
        backupsPage.setVerticalScrollBarEnabled(false);

        LinearLayout col = ui.vbox();
        col.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(40));

        col.addView(ui.sectionLabel("All application backups"));
        backupsListHolder = ui.vbox();
        col.addView(backupsListHolder);

        backupsPage.addView(col);
    }

    private void loadGlobalBackups() {
        if (backupsListHolder == null) return;
        backupsListHolder.removeAllViews();

        File rootBackupDir = new File(sd, "CustomData/Backups");
        List<Backups.Item> allFound = new ArrayList<Backups.Item>();

        if (rootBackupDir.isDirectory()) {
            File[] pkgDirs = rootBackupDir.listFiles();
            if (pkgDirs != null) {
                for (File d : pkgDirs) {
                    if (d.isDirectory()) {
                        allFound.addAll(Backups.list(d));
                    }
                }
            }
        }

        if (allFound.isEmpty()) {
            LinearLayout none = ui.card(ui.c.surfaceContainerLow, 20f);
            none.setGravity(Gravity.CENTER);
            none.setPadding(ui.dp(20), ui.dp(36), ui.dp(20), ui.dp(36));

            ImageView emptyIcon = new ImageView(this);
            emptyIcon.setImageDrawable(Phosphor.get(Phosphor.FILE_ZIP, ui.c.outlineVariant, ui.dp(44)));
            none.addView(emptyIcon, new LinearLayout.LayoutParams(ui.dp(54), ui.dp(54)));

            TextView emptyText = ui.text("No backups found yet", 16, ui.c.onSurface, true);
            emptyText.setPadding(0, ui.dp(12), 0, 0);
            none.addView(emptyText);

            TextView sub = ui.text("Open an application's configuration and tap 'Back up now' to create zip archives.",
                    13, ui.c.onSurfaceVariant, false);
            sub.setLineSpacing(0, 1.15f);
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(ui.dp(16), ui.dp(4), ui.dp(16), 0);
            none.addView(sub);

            backupsListHolder.addView(none);
            return;
        }

        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        List<View> rows = new ArrayList<View>();
        for (final Backups.Item it : allFound) {
            LinearLayout itemRow = ui.vbox();

            LinearLayout top = ui.hbox();
            ImageView zipIcon = new ImageView(this);
            zipIcon.setImageDrawable(Phosphor.get(Phosphor.FILE_ZIP, ui.c.primary, ui.dp(24)));
            top.addView(zipIcon, new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)));

            LinearLayout nameBox = ui.vbox();
            nameBox.setPadding(ui.dp(12), 0, 0, 0);
            nameBox.addView(ui.text(it.name, 15, ui.c.onSurface, true));
            nameBox.addView(ui.text(Formatter.formatShortFileSize(this, it.size) + "  \u00B7  "
                    + df.format(new Date(it.time)), 12, ui.c.onSurfaceVariant, false));
            top.addView(nameBox, new LinearLayout.LayoutParams(0, -2, 1f));
            itemRow.addView(top);

            LinearLayout actions = ui.hbox();
            actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            actions.setPadding(0, ui.dp(8), 0, 0);

            TextView delBtn = ui.buttonWithIcon("Delete", Phosphor.TRASH, NeoUi.TEXT_ERROR, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ui.dialog(MainActivity.this, Phosphor.TRASH, "Delete backup?",
                            "Delete " + it.name + ".zip permanently?", null, "Delete", new Runnable() {
                        @Override public void run() {
                            Backups.delete(it);
                            loadGlobalBackups();
                        }
                    }, "Cancel");
                }
            });
            delBtn.setMinHeight(ui.dp(36));
            delBtn.setTextSize(12);
            actions.addView(delBtn);

            itemRow.addView(actions);
            rows.add(itemRow);
        }
        backupsListHolder.addView(ui.group(rows));
    }

    // =========================================================================
    // TAB 3: Settings & Preferences
    // =========================================================================
    private void buildSettingsPage() {
        settingsPage = new ScrollView(this);
        settingsPage.setFillViewport(true);
        settingsPage.setVerticalScrollBarEnabled(false);

        LinearLayout col = ui.vbox();
        col.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(40));

        // Module status card
        col.addView(ui.sectionLabel("Framework status"));
        LinearLayout statusCard = ui.card(ui.c.surfaceContainerLow, 20f);
        LinearLayout statusRow = ui.hbox();
        ImageView shield = new ImageView(this);
        shield.setImageDrawable(Phosphor.get(Phosphor.SHIELD_CHECKERED, ui.c.success, ui.dp(32)));
        statusRow.addView(shield, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));

        LinearLayout stx = ui.vbox();
        stx.setPadding(ui.dp(14), 0, 0, 0);
        stx.addView(ui.text("Storage Redirect Active", 16, ui.c.onSurface, true));
        stx.addView(ui.text("LSPatch & LSPosed compatible hook engine loaded", 12, ui.c.onSurfaceVariant, false));
        statusRow.addView(stx, new LinearLayout.LayoutParams(0, -2, 1f));
        statusCard.addView(statusRow);
        col.addView(statusCard);

        // Configuration folder card
        col.addView(ui.sectionLabel("Configuration directory"));
        LinearLayout cfgCard = ui.card(ui.c.surfaceContainer, 20f);
        cfgCard.addView(ui.text("Shared directory where all redirection and backup settings are stored for all hooked apps.",
                13, ui.c.onSurfaceVariant, false));

        File currentDir = RedirectConfig.dir(sd);
        cfgField = ui.field(currentDir.getAbsolutePath(), RedirectConfig.DEF_CONFIG_DIR);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = ui.dp(10);
        cfgCard.addView(cfgField, flp);

        cfgInUse = ui.text("In use: " + currentDir.getAbsolutePath(), 11, ui.c.onSurfaceVariant, false);
        cfgInUse.setPadding(ui.dp(4), ui.dp(6), 0, 0);
        cfgCard.addView(cfgInUse);

        LinearLayout cbtn = ui.hbox();
        cbtn.addView(ui.buttonWithIcon("Apply", Phosphor.CHECK, NeoUi.FILLED, new View.OnClickListener() {
            @Override public void onClick(View v) { applyConfigFolder(cfgField.getText().toString()); }
        }));
        cbtn.addView(ui.button("Reset to default", NeoUi.TEXT, new View.OnClickListener() {
            @Override public void onClick(View v) {
                cfgField.setText(RedirectConfig.DEF_CONFIG_DIR);
                applyConfigFolder(RedirectConfig.DEF_CONFIG_DIR);
            }
        }));
        LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(-1, -2);
        cblp.topMargin = ui.dp(10);
        cfgCard.addView(cbtn, cblp);

        cfgStatus = ui.text("", 12, ui.c.primary, false);
        cfgStatus.setPadding(ui.dp(4), ui.dp(8), 0, 0);
        cfgStatus.setVisibility(View.GONE);
        cfgCard.addView(cfgStatus);
        col.addView(cfgCard);

        // Module Settings Backup & Restore Card
        col.addView(ui.sectionLabel("Module configuration backup"));
        LinearLayout mbCard = ui.card(ui.c.surfaceContainer, 20f);
        mbCard.addView(ui.text("Backup and restore the module\u2019s own settings (global defaults, per-app rules, and backup configs) to a portable zip archive.",
                13, ui.c.onSurfaceVariant, false));

        final String defZipPath = sd + "/" + RedirectConfig.DEF_CONFIG_DIR + "/storageredirect-module-config.zip";
        final EditText mbField = ui.field(defZipPath, "Backup archive path (.zip)");
        LinearLayout.LayoutParams mblp = new LinearLayout.LayoutParams(-1, -2);
        mblp.topMargin = ui.dp(10);
        mbCard.addView(mbField, mblp);

        final TextView mbStatus = ui.text("", 12, ui.c.primary, false);
        mbStatus.setPadding(ui.dp(4), ui.dp(8), 0, 0);
        mbStatus.setVisibility(View.GONE);

        LinearLayout mbBtns = ui.hbox();
        mbBtns.addView(ui.buttonWithIcon("Backup", Phosphor.FILE_ZIP, NeoUi.FILLED, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String path = mbField.getText().toString().trim();
                if (path.isEmpty()) path = defZipPath;
                File target = path.startsWith("/") ? new File(path) : new File(sd, path);
                String err = RedirectConfig.backupModuleConfig(MainActivity.this, sd, target);
                mbStatus.setVisibility(View.VISIBLE);
                if (err == null) {
                    mbStatus.setText("Backup saved successfully (" + Formatter.formatFileSize(MainActivity.this, target.length()) + ")");
                    mbStatus.setTextColor(ui.c.primary);
                    Toast.makeText(MainActivity.this, "Module settings backed up", Toast.LENGTH_SHORT).show();
                } else {
                    mbStatus.setText("Backup error: " + err);
                    mbStatus.setTextColor(ui.c.error);
                }
            }
        }));
        mbBtns.addView(ui.buttonWithIcon("Restore", Phosphor.RESTORE, NeoUi.TONAL, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String path = mbField.getText().toString().trim();
                final File target = path.startsWith("/") ? new File(path) : new File(sd, path);
                if (!target.isFile()) {
                    mbStatus.setVisibility(View.VISIBLE);
                    mbStatus.setText("Backup file does not exist: " + target.getAbsolutePath());
                    mbStatus.setTextColor(ui.c.error);
                    return;
                }
                ui.dialog(MainActivity.this, Phosphor.WARNING, "Restore Module Settings?",
                        "This will overwrite the current module configuration with settings from:\n" + target.getAbsolutePath(),
                        null, "Restore", new Runnable() {
                            @Override
                            public void run() {
                                String err = RedirectConfig.restoreModuleConfig(MainActivity.this, sd, target);
                                mbStatus.setVisibility(View.VISIBLE);
                                if (err == null) {
                                    mbStatus.setText("Settings restored successfully!");
                                    mbStatus.setTextColor(ui.c.primary);
                                    Toast.makeText(MainActivity.this, "Settings restored", Toast.LENGTH_SHORT).show();
                                    refreshAll();
                                } else {
                                    mbStatus.setText("Restore error: " + err);
                                    mbStatus.setTextColor(ui.c.error);
                                }
                            }
                        }, "Cancel");
            }
        }));
        LinearLayout.LayoutParams mbBtnLp = new LinearLayout.LayoutParams(-1, -2);
        mbBtnLp.topMargin = ui.dp(10);
        mbCard.addView(mbBtns, mbBtnLp);
        mbCard.addView(mbStatus);
        col.addView(mbCard);

        // Look & Feel (Theme selection)
        col.addView(ui.sectionLabel("Appearance & Theme"));
        List<View> themeRows = new ArrayList<View>();

        final String[] themeOptions = {"Follow system", "Light theme", "Dark theme", "AMOLED Black"};
        int currentMode = NeoTheme.getSavedMode(this);
        themeRows.add(actionPreference("Theme mode", themeOptions[currentMode], Phosphor.GEAR, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showThemeDialog();
            }
        }));

        final String[] accentOptions = {"Red Comet (Neo default)", "Blue", "Teal", "Purple", "Dynamic Monet"};
        int currentAccent = NeoTheme.getSavedAccent(this);
        themeRows.add(actionPreference("Color palette", accentOptions[currentAccent], Phosphor.SLIDERS, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAccentDialog();
            }
        }));
        col.addView(ui.group(themeRows));

        // About section
        col.addView(ui.sectionLabel("About"));
        List<View> aboutRows = new ArrayList<View>();
        aboutRows.add(infoRow("Storage Redirect", "v2.15 (Neo Backup Redesign)"));
        aboutRows.add(infoRow("Design Reference", "Neo Backup by Machiav3lli"));
        aboutRows.add(infoRow("Iconography", "Phosphor Icons (MIT License)"));
        col.addView(ui.group(aboutRows));

        settingsPage.addView(col);
    }

    private View actionPreference(String title, String summary, int iconType, View.OnClickListener l) {
        LinearLayout row = ui.hbox();
        row.setClickable(true);
        row.setOnClickListener(l);

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(Phosphor.get(iconType, ui.c.primary, ui.dp(22)));
        row.addView(iv, new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)));

        LinearLayout tx = ui.vbox();
        tx.setPadding(ui.dp(12), 0, 0, 0);
        tx.addView(ui.text(title, 15, ui.c.onSurface, true));
        tx.addView(ui.text(summary, 12, ui.c.primary, false));
        row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView arrow = new ImageView(this);
        arrow.setImageDrawable(Phosphor.get(Phosphor.ARROW_RIGHT, ui.c.onSurfaceVariant, ui.dp(18)));
        row.addView(arrow, new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
        return row;
    }

    private void showThemeDialog() {
        final String[] options = {"Follow system", "Light theme", "Dark theme", "AMOLED Black"};
        LinearLayout body = ui.vbox();
        for (int i = 0; i < options.length; i++) {
            final int mode = i;
            TextView item = ui.text(options[i], 16, ui.c.onSurface, true);
            item.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
            item.setBackground(ui.tile(0, 16f, ui.c.primary));
            item.setClickable(true);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NeoTheme.setSavedMode(MainActivity.this, mode);
                    recreate();
                }
            });
            body.addView(item);
        }
        ui.dialog(this, Phosphor.GEAR, "Theme Mode", null, body, null, null, "Close");
    }

    private void showAccentDialog() {
        final String[] options = {"Red Comet (Neo default)", "Blue", "Teal", "Purple", "Dynamic Monet"};
        LinearLayout body = ui.vbox();
        for (int i = 0; i < options.length; i++) {
            final int accent = i;
            TextView item = ui.text(options[i], 16, ui.c.onSurface, true);
            item.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
            item.setBackground(ui.tile(0, 16f, ui.c.primary));
            item.setClickable(true);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NeoTheme.setSavedAccent(MainActivity.this, accent);
                    recreate();
                }
            });
            body.addView(item);
        }
        ui.dialog(this, Phosphor.SLIDERS, "Color Palette", null, body, null, null, "Close");
    }

    private void applyConfigFolder(String text) {
        String err = RedirectConfig.moveConfigDir(sd, text);
        cfgStatus.setVisibility(View.VISIBLE);
        if (err == null) {
            File d = RedirectConfig.dir(sd);
            cfgStatus.setText("Settings are now in " + d.getAbsolutePath());
            cfgStatus.setTextColor(ui.c.primary);
            if (cfgInUse != null) cfgInUse.setText("In use: " + d.getAbsolutePath());
        } else {
            cfgStatus.setText(err);
            cfgStatus.setTextColor(ui.c.error);
        }
        refreshAll();
    }

    private void openAppSettings(String pkg) {
        Intent i = new Intent(this, SettingsActivity.class);
        if (pkg != null) i.putExtra(SettingsActivity.EXTRA_PKG, pkg);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // ------------------------------------------------------------- Storage access
    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                return (Boolean) Environment.class.getMethod("isExternalStorageManager").invoke(null);
            } catch (Throwable t) {
                return false;
            }
        }
        if (Build.VERSION.SDK_INT >= 23) {
            return checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE")
                    == PackageManager.PERMISSION_GRANTED;
        }
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
        refreshAll();
    }
}
