package io.github.expirt.storageredirect;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

/**
 * Material 3 color roles. On Android 12+ they come from the system's dynamic ("Monet") palette,
 * read by reflection because the app is compiled against an older SDK; older versions fall back
 * to the baseline Material 3 palette. Light/dark follows the system setting.
 */
final class M3 {

    int primary, onPrimary, primaryContainer, onPrimaryContainer;
    int secondaryContainer, onSecondaryContainer;
    int tertiaryContainer, onTertiaryContainer;
    int surface, surfaceContainer, surfaceContainerHigh;
    int onSurface, onSurfaceVariant, outline, outlineVariant;
    int error, onError, errorContainer, onErrorContainer;
    boolean night;
    boolean dynamic;

    static boolean isNight(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static M3 of(Context c) {
        M3 m = new M3();
        m.night = isNight(c);
        m.dynamic = Build.VERSION.SDK_INT >= 31 && m.loadDynamic(c);
        if (!m.dynamic) m.loadBaseline();
        if (m.night) {
            m.error = 0xFFFFB4AB; m.onError = 0xFF690005;
            m.errorContainer = 0xFF93000A; m.onErrorContainer = 0xFFFFDAD6;
        } else {
            m.error = 0xFFBA1A1A; m.onError = 0xFFFFFFFF;
            m.errorContainer = 0xFFFFDAD6; m.onErrorContainer = 0xFF410002;
        }
        return m;
    }

    /** system_accent1_600 etc.; returns 0 if the resource doesn't exist. */
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
            secondaryContainer = sys(c, "system_accent2_100");
            onSecondaryContainer = sys(c, "system_accent2_900");
            tertiaryContainer = sys(c, "system_accent3_100");
            onTertiaryContainer = sys(c, "system_accent3_900");
            surface = sys(c, "system_neutral1_10");
            surfaceContainer = sys(c, "system_neutral1_50");
            surfaceContainerHigh = sys(c, "system_neutral1_100");
            onSurface = sys(c, "system_neutral1_900");
            onSurfaceVariant = sys(c, "system_neutral2_700");
            outline = sys(c, "system_neutral2_500");
            outlineVariant = sys(c, "system_neutral2_200");
        } else {
            primary = sys(c, "system_accent1_200");
            onPrimary = sys(c, "system_accent1_800");
            primaryContainer = sys(c, "system_accent1_700");
            onPrimaryContainer = sys(c, "system_accent1_100");
            secondaryContainer = sys(c, "system_accent2_700");
            onSecondaryContainer = sys(c, "system_accent2_100");
            tertiaryContainer = sys(c, "system_accent3_700");
            onTertiaryContainer = sys(c, "system_accent3_100");
            surface = sys(c, "system_neutral1_900");
            surfaceContainer = sys(c, "system_neutral1_800");
            surfaceContainerHigh = sys(c, "system_neutral1_700");
            onSurface = sys(c, "system_neutral1_100");
            onSurfaceVariant = sys(c, "system_neutral2_200");
            outline = sys(c, "system_neutral2_400");
            outlineVariant = sys(c, "system_neutral2_700");
        }
        return true;
    }

    private void loadBaseline() {
        if (!night) {
            primary = 0xFF6750A4; onPrimary = 0xFFFFFFFF;
            primaryContainer = 0xFFEADDFF; onPrimaryContainer = 0xFF21005D;
            secondaryContainer = 0xFFE8DEF8; onSecondaryContainer = 0xFF1D192B;
            tertiaryContainer = 0xFFFFD8E4; onTertiaryContainer = 0xFF31111D;
            surface = 0xFFFEF7FF; surfaceContainer = 0xFFF3EDF7; surfaceContainerHigh = 0xFFECE6F0;
            onSurface = 0xFF1D1B20; onSurfaceVariant = 0xFF49454F;
            outline = 0xFF79747E; outlineVariant = 0xFFCAC4D0;
        } else {
            primary = 0xFFD0BCFF; onPrimary = 0xFF381E72;
            primaryContainer = 0xFF4F378B; onPrimaryContainer = 0xFFEADDFF;
            secondaryContainer = 0xFF4A4458; onSecondaryContainer = 0xFFE8DEF8;
            tertiaryContainer = 0xFF633B48; onTertiaryContainer = 0xFFFFD8E4;
            surface = 0xFF141218; surfaceContainer = 0xFF211F26; surfaceContainerHigh = 0xFF2B2930;
            onSurface = 0xFFE6E0E9; onSurfaceVariant = 0xFFCAC4D0;
            outline = 0xFF938F99; outlineVariant = 0xFF49454F;
        }
    }
}
