package io.github.expirt.storageredirect;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

/**
 * Neo Backup Design System Theme Engine.
 * Matches Neo Backup's color tokens, multi-tiered surface containers, and theme modes:
 * Light, Dark, AMOLED Black, and Android 12+ Monet Dynamic Colors.
 * Reference: com.machiav3lli.backup.ui.compose.theme (Neo Backup)
 */
public final class NeoTheme {

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;
    public static final int MODE_AMOLED = 3;

    public static final int ACCENT_VIVI = 0;      // ViviMusic default (Coral Rose Red #ED5564)
    public static final int ACCENT_CRIMSON = 0;   // backwards compatibility alias
    public static final int ACCENT_BLUE = 1;      // Azure Blue
    public static final int ACCENT_TEAL = 2;      // Mint Teal
    public static final int ACCENT_PURPLE = 3;    // Electric Violet
    public static final int ACCENT_DYNAMIC = 4;   // Android 12+ Monet

    private static final String PREF_NAME = "neo_theme_prefs";
    private static final String KEY_MODE = "theme_mode";
    private static final String KEY_ACCENT = "theme_accent";

    public int primary;
    public int onPrimary;
    public int primaryContainer;
    public int onPrimaryContainer;

    public int secondary;
    public int onSecondary;
    public int secondaryContainer;
    public int onSecondaryContainer;

    public int tertiary;
    public int onTertiary;
    public int tertiaryContainer;
    public int onTertiaryContainer;

    public int surface;
    public int surfaceDim;
    public int surfaceBright;
    public int surfaceContainerLowest;
    public int surfaceContainerLow;
    public int surfaceContainer;
    public int surfaceContainerHigh;
    public int surfaceContainerHighest;

    public int onSurface;
    public int onSurfaceVariant;
    public int outline;
    public int outlineVariant;

    public int error;
    public int onError;
    public int errorContainer;
    public int onErrorContainer;

    public int success;
    public int onSuccess;
    public int successContainer;
    public int onSuccessContainer;

    public boolean night;
    public boolean amoled;
    public boolean dynamic;
    public int currentMode;
    public int currentAccent;

    public static int getSavedMode(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            return sp.getInt(KEY_MODE, MODE_SYSTEM);
        } catch (Throwable t) {
            return MODE_SYSTEM;
        }
    }

    public static void setSavedMode(Context c, int mode) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sp.edit().putInt(KEY_MODE, mode).apply();
        } catch (Throwable ignored) {}
    }

    public static int getSavedAccent(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            return sp.getInt(KEY_ACCENT, ACCENT_CRIMSON);
        } catch (Throwable t) {
            return ACCENT_CRIMSON;
        }
    }

    public static void setSavedAccent(Context c, int accent) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sp.edit().putInt(KEY_ACCENT, accent).apply();
        } catch (Throwable ignored) {}
    }

    public static boolean isNight(Context c) {
        int mode = getSavedMode(c);
        if (mode == MODE_LIGHT) return false;
        if (mode == MODE_DARK || mode == MODE_AMOLED) return true;
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    public static NeoTheme of(Context c) {
        NeoTheme t = new NeoTheme();
        t.currentMode = getSavedMode(c);
        t.currentAccent = getSavedAccent(c);
        t.amoled = t.currentMode == MODE_AMOLED;
        t.night = isNight(c);

        if (t.currentAccent == ACCENT_DYNAMIC && Build.VERSION.SDK_INT >= 31 && t.loadDynamic(c)) {
            t.dynamic = true;
        } else {
            t.dynamic = false;
            t.loadNeoPalette();
        }

        // Common status colors
        if (!t.night) {
            t.error = 0xFFBA1A1A;
            t.onError = 0xFFFFFFFF;
            t.errorContainer = 0xFFFFDAD6;
            t.onErrorContainer = 0xFF410002;

            t.success = 0xFF146C2E;
            t.onSuccess = 0xFFFFFFFF;
            t.successContainer = 0xFFB8F5D0;
            t.onSuccessContainer = 0xFF00210C;
        } else {
            t.error = 0xFFFFB4AB;
            t.onError = 0xFF690005;
            t.errorContainer = 0xFF93000A;
            t.onErrorContainer = 0xFFFFDAD6;

            t.success = 0xFF3AEA85;
            t.onSuccess = 0xFF003919;
            t.successContainer = 0xFF005227;
            t.onSuccessContainer = 0xFFB8F5D0;
        }

        if (t.amoled) {
            t.surface = 0xFF000000;
            t.surfaceDim = 0xFF000000;
            t.surfaceBright = 0xFF141418;
            t.surfaceContainerLowest = 0xFF000000;
            t.surfaceContainerLow = 0xFF0A0A0C;
            t.surfaceContainer = 0xFF121216;
            t.surfaceContainerHigh = 0xFF1A1A20;
            t.surfaceContainerHighest = 0xFF24242C;
            t.outline = 0xFF3A3A44;
            t.outlineVariant = 0xFF222228;
        }

        return t;
    }

    private void loadNeoPalette() {
        if (!night) {
            // Light Theme (ViviMusic Palette)
            switch (currentAccent) {
                case ACCENT_BLUE:
                    primary = 0xFF1D6FDB; onPrimary = 0xFFFFFFFF;
                    primaryContainer = 0x241D6FDB; onPrimaryContainer = 0xFF0B3A7A;
                    break;
                case ACCENT_TEAL:
                    primary = 0xFF0D9488; onPrimary = 0xFFFFFFFF;
                    primaryContainer = 0x240D9488; onPrimaryContainer = 0xFF04403B;
                    break;
                case ACCENT_PURPLE:
                    primary = 0xFF8B5CF6; onPrimary = 0xFFFFFFFF;
                    primaryContainer = 0x248B5CF6; onPrimaryContainer = 0xFF3B1578;
                    break;
                default: // ACCENT_VIVI / ACCENT_CRIMSON (ViviMusic Coral Red #ED5564)
                    primary = 0xFFED5564; onPrimary = 0xFFFFFFFF;
                    primaryContainer = 0x24ED5564; onPrimaryContainer = 0xFF821320;
                    break;
            }

            secondary = 0xFF5C5B66;
            onSecondary = 0xFFFFFFFF;
            secondaryContainer = 0xFFE2E1EC;
            onSecondaryContainer = 0xFF191A23;

            tertiary = primary;
            onTertiary = 0xFFFFFFFF;
            tertiaryContainer = primaryContainer;
            onTertiaryContainer = onPrimaryContainer;

            surface = 0xFFFDF8F8;
            surfaceDim = 0xFFF0EAEB;
            surfaceBright = 0xFFFFFFFF;
            surfaceContainerLowest = 0xFFFFFFFF;
            surfaceContainerLow = 0xFFF8F0F1;
            surfaceContainer = 0xFFF2E9EA;
            surfaceContainerHigh = 0xFFEBE2E3;
            surfaceContainerHighest = 0xFFE4DCDE;

            onSurface = 0xFF1E1A1B;
            onSurfaceVariant = 0xFF4F4446;
            outline = 0xFF827476;
            outlineVariant = 0xFFD4C3C5;
        } else {
            // Dark Theme (ViviMusic Signature Palette)
            switch (currentAccent) {
                case ACCENT_BLUE:
                    primary = 0xFF3D8BFF; onPrimary = 0xFF002B66;
                    primaryContainer = 0x293D8BFF; onPrimaryContainer = 0xFFD8E4FF;
                    break;
                case ACCENT_TEAL:
                    primary = 0xFF2DD4BF; onPrimary = 0xFF003731;
                    primaryContainer = 0x292DD4BF; onPrimaryContainer = 0xFFCCFBF1;
                    break;
                case ACCENT_PURPLE:
                    primary = 0xFFA855F7; onPrimary = 0xFF381E72;
                    primaryContainer = 0x29A855F7; onPrimaryContainer = 0xFFF3E8FF;
                    break;
                default: // ACCENT_VIVI (ViviMusic DefaultThemeColor #ED5564)
                    primary = 0xFFED5564; onPrimary = 0xFFFFFFFF;
                    primaryContainer = 0x2BED5564; onPrimaryContainer = 0xFFFFD9DD;
                    break;
            }

            secondary = 0xFFC6C4D0;
            onSecondary = 0xFF2F2F39;
            secondaryContainer = 0xFF454550;
            onSecondaryContainer = 0xFFE2E0EC;

            tertiary = primary;
            onTertiary = onPrimary;
            tertiaryContainer = primaryContainer;
            onTertiaryContainer = onPrimaryContainer;

            surface = 0xFF0E0E12;
            surfaceDim = 0xFF0A0A0C;
            surfaceBright = 0xFF202028;
            surfaceContainerLowest = 0xFF08080A;
            surfaceContainerLow = 0xFF141418;
            surfaceContainer = 0xFF18181E;
            surfaceContainerHigh = 0xFF202026;
            surfaceContainerHighest = 0xFF282830;

            onSurface = 0xFFEEEEF2;
            onSurfaceVariant = 0xFF9E9EA8;
            outline = 0xFF383842;
            outlineVariant = 0xFF222228;
        }
    }

    private static int sys(Context c, String name) {
        try {
            int id = android.R.color.class.getField(name).getInt(null);
            return c.getResources().getColor(id, c.getTheme());
        } catch (Throwable t) {
            return 0;
        }
    }

    private boolean loadDynamic(Context c) {
        if (sys(c, "system_accent1_600") == 0) return false;
        if (!night) {
            primary = sys(c, "system_accent1_600");
            onPrimary = sys(c, "system_accent1_0");
            primaryContainer = sys(c, "system_accent1_100");
            onPrimaryContainer = sys(c, "system_accent1_900");
            secondary = sys(c, "system_accent2_600");
            onSecondary = 0xFFFFFFFF;
            secondaryContainer = sys(c, "system_accent2_100");
            onSecondaryContainer = sys(c, "system_accent2_900");
            tertiary = sys(c, "system_accent3_600");
            onTertiary = 0xFFFFFFFF;
            tertiaryContainer = sys(c, "system_accent3_100");
            onTertiaryContainer = sys(c, "system_accent3_900");

            surface = sys(c, "system_neutral1_10");
            surfaceDim = sys(c, "system_neutral1_50");
            surfaceBright = sys(c, "system_neutral1_10");
            surfaceContainerLowest = 0xFFFFFFFF;
            surfaceContainerLow = sys(c, "system_neutral1_20");
            surfaceContainer = sys(c, "system_neutral1_50");
            surfaceContainerHigh = sys(c, "system_neutral1_100");
            surfaceContainerHighest = sys(c, "system_neutral1_200");

            onSurface = sys(c, "system_neutral1_900");
            onSurfaceVariant = sys(c, "system_neutral2_700");
            outline = sys(c, "system_neutral2_500");
            outlineVariant = sys(c, "system_neutral2_200");
        } else {
            primary = sys(c, "system_accent1_200");
            onPrimary = sys(c, "system_accent1_800");
            primaryContainer = sys(c, "system_accent1_700");
            onPrimaryContainer = sys(c, "system_accent1_100");
            secondary = sys(c, "system_accent2_200");
            onSecondary = sys(c, "system_accent2_800");
            secondaryContainer = sys(c, "system_accent2_700");
            onSecondaryContainer = sys(c, "system_accent2_100");
            tertiary = sys(c, "system_accent3_200");
            onTertiary = sys(c, "system_accent3_800");
            tertiaryContainer = sys(c, "system_accent3_700");
            onTertiaryContainer = sys(c, "system_accent3_100");

            surface = sys(c, "system_neutral1_900");
            surfaceDim = sys(c, "system_neutral1_900");
            surfaceBright = sys(c, "system_neutral1_700");
            surfaceContainerLowest = sys(c, "system_neutral1_950");
            surfaceContainerLow = sys(c, "system_neutral1_850");
            surfaceContainer = sys(c, "system_neutral1_800");
            surfaceContainerHigh = sys(c, "system_neutral1_700");
            surfaceContainerHighest = sys(c, "system_neutral1_600");

            onSurface = sys(c, "system_neutral1_100");
            onSurfaceVariant = sys(c, "system_neutral2_200");
            outline = sys(c, "system_neutral2_400");
            outlineVariant = sys(c, "system_neutral2_700");
        }
        return true;
    }
}
