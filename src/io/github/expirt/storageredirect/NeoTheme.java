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

    public static final int ACCENT_CRIMSON = 0;   // Neo Backup default (Red Comet)
    public static final int ACCENT_BLUE = 1;
    public static final int ACCENT_TEAL = 2;
    public static final int ACCENT_PURPLE = 3;
    public static final int ACCENT_DYNAMIC = 4;

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
            t.surfaceBright = 0xFF161616;
            t.surfaceContainerLowest = 0xFF000000;
            t.surfaceContainerLow = 0xFF0C0C0C;
            t.surfaceContainer = 0xFF141414;
            t.surfaceContainerHigh = 0xFF1E1E1E;
            t.surfaceContainerHighest = 0xFF2A2A2A;
            t.outline = 0xFF4E4E4E;
            t.outlineVariant = 0xFF2A2A2A;
        }

        return t;
    }

    private void loadNeoPalette() {
        if (!night) {
            // Light Theme (Neo Backup values)
            switch (currentAccent) {
                case ACCENT_BLUE:
                    primary = 0xFF155093; onPrimary = 0xFFFFFFFF;
                    primaryContainer = 0xFF4475BA; onPrimaryContainer = 0xFFFFFFFF;
                    break;
                case ACCENT_TEAL:
                    primary = 0xFF006A60; onPrimary = 0xFFFFFFFF;
                    primaryContainer = 0xFF73F8E6; onPrimaryContainer = 0xFF00201C;
                    break;
                case ACCENT_PURPLE:
                    primary = 0xFF7B1FA2; onPrimary = 0xFFFFFFFF;
                    primaryContainer = 0xFFEADDFF; onPrimaryContainer = 0xFF21005D;
                    break;
                default: // ACCENT_CRIMSON (Neo Backup canonical Red Comet)
                    primary = 0xFFA20034; onPrimary = 0xFFFFFFFF;
                    primaryContainer = 0xFFE11E51; onPrimaryContainer = 0xFFFFFFFF;
                    break;
            }

            secondary = 0xFFA63649;
            onSecondary = 0xFFFFFFFF;
            secondaryContainer = 0xFFFF8B98;
            onSecondaryContainer = 0xFF4D0014;

            tertiary = 0xFF155093;
            onTertiary = 0xFFFFFFFF;
            tertiaryContainer = 0xFF4475BA;
            onTertiaryContainer = 0xFFFFFFFF;

            surface = 0xFFFFF8F7;
            surfaceDim = 0xFFF1D3D4;
            surfaceBright = 0xFFFFF8F7;
            surfaceContainerLowest = 0xFFFFFFFF;
            surfaceContainerLow = 0xFFFFF0F0;
            surfaceContainer = 0xFFFFE9E9;
            surfaceContainerHigh = 0xFFFFE1E2;
            surfaceContainerHighest = 0xFFFADBDD;

            onSurface = 0xFF281719;
            onSurfaceVariant = 0xFF5C3F42;
            outline = 0xFF906F71;
            outlineVariant = 0xFFE4BDBF;
        } else {
            // Dark Theme (Neo Backup values)
            switch (currentAccent) {
                case ACCENT_BLUE:
                    primary = 0xFFA7C8FF; onPrimary = 0xFF003061;
                    primaryContainer = 0xFF265C9F; onPrimaryContainer = 0xFFFFFFFF;
                    break;
                case ACCENT_TEAL:
                    primary = 0xFF53DBC9; onPrimary = 0xFF003731;
                    primaryContainer = 0xFF005048; onPrimaryContainer = 0xFF73F8E6;
                    break;
                case ACCENT_PURPLE:
                    primary = 0xFFD0BCFF; onPrimary = 0xFF381E72;
                    primaryContainer = 0xFF4F378B; onPrimaryContainer = 0xFFEADDFF;
                    break;
                default: // ACCENT_CRIMSON (Neo Backup canonical)
                    primary = 0xFFFFB2B9; onPrimary = 0xFF67001E;
                    primaryContainer = 0xFFBC003E; onPrimaryContainer = 0xFFFFFFFF;
                    break;
            }

            secondary = 0xFFFFB2B9;
            onSecondary = 0xFF67011E;
            secondaryContainer = 0xFF7E162D;
            onSecondaryContainer = 0xFFFFC8CC;

            tertiary = 0xFFA7C8FF;
            onTertiary = 0xFF003061;
            tertiaryContainer = 0xFF265C9F;
            onTertiaryContainer = 0xFFFFFFFF;

            surface = 0xFF1F0F10;
            surfaceDim = 0xFF1F0F10;
            surfaceBright = 0xFF483436;
            surfaceContainerLowest = 0xFF190A0B;
            surfaceContainerLow = 0xFF281719;
            surfaceContainer = 0xFF2C1B1C;
            surfaceContainerHigh = 0xFF382527;
            surfaceContainerHighest = 0xFF433031;

            onSurface = 0xFFFADBDD;
            onSurfaceVariant = 0xFFE4BDBF;
            outline = 0xFFAB888A;
            outlineVariant = 0xFF5C3F42;
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
