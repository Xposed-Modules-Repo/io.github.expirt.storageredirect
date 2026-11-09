package io.github.expirt.storageredirect;

import android.content.Context;

/**
 * Material 3 / Neo Design System adapter.
 * Wraps NeoTheme for backward compatibility while providing full Neo Backup color roles.
 */
final class M3 {

    int primary, onPrimary, primaryContainer, onPrimaryContainer;
    int secondary, onSecondary, secondaryContainer, onSecondaryContainer;
    int tertiary, onTertiary, tertiaryContainer, onTertiaryContainer;
    int surface, surfaceDim, surfaceBright;
    int surfaceContainerLowest, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest;
    int onSurface, onSurfaceVariant, outline, outlineVariant;
    int error, onError, errorContainer, onErrorContainer;
    int success, onSuccess, successContainer, onSuccessContainer;
    boolean night;
    boolean amoled;
    boolean dynamic;

    static boolean isNight(Context c) {
        return NeoTheme.isNight(c);
    }

    static M3 of(Context c) {
        NeoTheme nt = NeoTheme.of(c);
        M3 m = new M3();
        m.primary = nt.primary;
        m.onPrimary = nt.onPrimary;
        m.primaryContainer = nt.primaryContainer;
        m.onPrimaryContainer = nt.onPrimaryContainer;

        m.secondary = nt.secondary;
        m.onSecondary = nt.onSecondary;
        m.secondaryContainer = nt.secondaryContainer;
        m.onSecondaryContainer = nt.onSecondaryContainer;

        m.tertiary = nt.tertiary;
        m.onTertiary = nt.onTertiary;
        m.tertiaryContainer = nt.tertiaryContainer;
        m.onTertiaryContainer = nt.onTertiaryContainer;

        m.surface = nt.surface;
        m.surfaceDim = nt.surfaceDim;
        m.surfaceBright = nt.surfaceBright;
        m.surfaceContainerLowest = nt.surfaceContainerLowest;
        m.surfaceContainerLow = nt.surfaceContainerLow;
        m.surfaceContainer = nt.surfaceContainer;
        m.surfaceContainerHigh = nt.surfaceContainerHigh;
        m.surfaceContainerHighest = nt.surfaceContainerHighest;

        m.onSurface = nt.onSurface;
        m.onSurfaceVariant = nt.onSurfaceVariant;
        m.outline = nt.outline;
        m.outlineVariant = nt.outlineVariant;

        m.error = nt.error;
        m.onError = nt.onError;
        m.errorContainer = nt.errorContainer;
        m.onErrorContainer = nt.onErrorContainer;

        m.success = nt.success;
        m.onSuccess = nt.onSuccess;
        m.successContainer = nt.successContainer;
        m.onSuccessContainer = nt.onSuccessContainer;

        m.night = nt.night;
        m.amoled = nt.amoled;
        m.dynamic = nt.dynamic;
        return m;
    }
}
