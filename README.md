# Storage Redirect

Xposed module for rootless **LSPatch** (also works under LSPosed). Redirects the hooked app's
storage to a custom folder on shared storage, so data is read and written there from the first
millisecond instead of the default paths.

It is generic: it redirects whichever app it is embedded in / scoped to (no hardcoded package).
Each redirect has its own fully customizable destination folder (absolute, or relative to `/sdcard`;
`{pkg}` = package name). Defaults: `CustomData/{pkg}/internal`, `.../external`, `.../storage`.
Inside a destination the app keeps its usual sub-folders (`files`, `cache`, `databases`, ...).
Don't point internal and external at the same folder: their `files`/`cache` would mix (the UI warns).

## What can be redirected (switches in the module's UI)

Each app can have its **own settings** (open the app in the module's list and turn on
"Custom settings for this app"); apps without an override follow the **Default settings**.

| Switch | Redirects |
|---|---|
| Internal data | `/data/data/<pkg>`: `getFilesDir`, `getCacheDir`, `getDir`, `getDatabasePath`, no_backup |
| SharedPreferences | `shared_prefs` (needs Internal data) |
| App external data | `/Android/data/<pkg>` and `/Android/obb/<pkg>` |
| Main storage | `/storage/emulated/0` becomes a private virtual sdcard per app |
| Hardcoded paths | Rewrites raw `/data/data/...`, `/sdcard/...` strings in `File(String)` |

`getCodeCacheDir`, `webview`, native library directories (`lib`, `libs`, `app_libs`), and LSPatch origin APKs/caches (`/cache/lspatch`, `/files/lspatch`, `/lspatch`, and any `.apk` inside private storage) are deliberately protected in real private storage so Android's `noexec` shared storage restriction and dynamic loaders (such as Facebook SoLoader / WhatsAppLibLoader) never fail.

## Look and feel — ViviMusic & Material 3 Expressive Design

Redesigned following the signature visual design and animations of [ViviMusic](https://github.com/vivi-music/vivi-music):
- **ModernSwitch Animated Toggles:** Material 3 Expressive switches with fluid spring thumb morphing (horizontal elongation during travel), micro-vector in-thumb checkmark (`✓`) in Vivi Coral and dismiss cross (`✕`), smooth ArgbEvaluator color interpolation, and tactile haptic feedback. Clicking anywhere on the item row triggers toggle with ripple feedback.
- **Vivi Textbox Styling:** 16dp rounded input containers with subtle idle outline and focused Vivi Coral (`#ED5564`) accent borders. Clean pill search bar (`26dp` radius, `surfaceContainerLow` fill, Coral search icon).
- **Grouped Settings Hierarchy:** Preference groups shaped with Vivi's `listItemShape` (24dp single card, 24dp top / 6dp bottom for first, 6dp for middle, 6dp top / 24dp bottom for last item; 4dp vertical gap).
- **Color Tokens & Palettes:** Signature Vivi Coral Red (`#ED5564`) default accent, pure AMOLED black and dark slate palettes (`#0A0A0C` background, `#141418` low container, `#18181E` container, `#202026` high container, `#EEEEF2` text, `#9E9EA8` secondary).
- **Navigation Suite:** 4-tab bottom navigation (Apps, Rules, Backups, Settings) with selection pill indicators and horizontal swipe paging.
- **Iconography:** 100% crisp Phosphor vector icons.

## Configuration Storage & Architecture

### Fixed Configuration Directory
The module configuration resides in a fixed shared directory:
```
/storage/emulated/0/Android/media/uninviting/config.properties        # Global defaults
/storage/emulated/0/Android/media/uninviting/redirect.properties      # Compatibility mirror
/storage/emulated/0/Android/media/uninviting/.config-location         # Location pointer
/storage/emulated/0/Android/media/uninviting/apps/<pkg>.properties   # Per-app overrides
/storage/emulated/0/Android/media/uninviting/backup/<pkg>.properties # Per-app backup schedules
```
- **Unified Path:** Accessible by both the module UI and all hooked apps across separate processes and UIDs under LSPatch and LSPosed.
- **Dual Filename Support:** `config.properties` is the primary configuration file, mirrored with `redirect.properties` for seamless backwards compatibility.
- **Location Pointer:** `.config-location` lives directly inside `/storage/emulated/0/Android/media/uninviting/` alongside the configuration files.
- **Relative Path Support:** Whenever customizing any path (configuration directory, redirect rules, folder redirects, block/exclude paths, or backups), if the path starts without `/`, the absolute path defaults to `/storage/emulated/0/*path*`. If it starts with `/`, the full path is used.
- **One-Time Migration:** On first launch, if `/storage/emulated/0/Android/media/uninviting/` has no configuration files, any existing configuration from legacy locations (`Android/data/uninviting`, `CustomData/.config`, or internal storage) is automatically migrated into the fixed directory.
- **Portable Backups:** Settings can be exported and imported at any time via the **Module configuration backup** feature in Settings.

## Live Configuration Reload

No more force-stopping hooked apps after changing rules in the UI:
- **Lock-Free Freshness Check:** On entry to hooked path methods (`File` constructors, `libcore.io.Linux`, `UnixFileSystem`, `java.nio.file`, SQLite), a rate-limited freshness check stats the configuration file at most once every 800 ms per process using atomic compare-and-swap.
- **Steady-State Performance:** The hot I/O path executes only a single volatile read and timestamp check before proceeding, ensuring negligible CPU and memory overhead.
- **Atomic State Swap:** When modification or deletion is detected, a complete immutable `ActiveState` (including compiled Glob patterns, `PathEngine` rule objects, block sets, and root directories) is built before swapping a single volatile reference. No hooked thread ever observes a partial or inconsistent state.
- **Dynamic Activation:** Creating destinations, parent directories, and updating redirect tables takes effect immediately while the target app is running without re-installing hooks.

## Module Configuration Backup & Restore

On the **Settings** screen:
- **Backup:** Archives all module configuration files (defaults, per-app rules, backup schedules) into a single portable `.zip` archive at any user-specified path (default `/storage/emulated/0/Android/media/uninviting/storageredirect-module-config.zip`).
- **Restore:** Reads a backup archive, safely verifies its contents, and restores the configuration tree directly into the active configuration root with instant live reloading.

## Path redirects (per app, optional)

In an app's settings (or the defaults), tap **Add redirect**. Each redirect is two text boxes: the **Original path** on top and the **Redirect to** path under it (stored as `original => redirect`). When a path starts without `/`, it automatically defaults to `/storage/emulated/0/*path*`. If it starts with `/`, write the full path (`{pkg}` becomes the app's package name).

- **Wildcards:** `*` matches any name inside one folder level, `**` matches anything (several levels).
  `{1}`, `{2}`... in the redirect path are replaced by what the 1st, 2nd... wildcard matched:
  `/sdcard/Android/data/*/cache => /sdcard/Onedroid/cache/{1}`.
- **Priority:** more specific rules (longest static prefix) always take priority over general rules.
- **Directories and Files:** supported transparently for both individual files and entire directory trees.
- **Never redirect:** a separate list of paths (wildcards allowed) that no rule and no folder redirect touches.
- Covers Java file I/O (`File`, streams, `UnixFileSystem`, `libcore.io.Linux`), `java.nio.file` (Android API 26+), and SQLite databases.

## Block creation (per app, optional)

List **paths** (one per line) that must never be created: absolute, or relative to `/storage/emulated/0`; `{pkg}` becomes the package name. `*` and `**` work: `/x/logs/**` blocks everything inside `logs` (not the folder itself), `/x/logs/*` only its direct children. `mkdir`, `open(O_CREAT)` and `rename` fail with *permission denied*.

## Fake standard paths (per app, optional)

With **Show standard paths to the app** on, the app keeps seeing the normal paths (`/data/user/0/<pkg>/...`, `/storage/emulated/0/...`, `/storage/emulated/0/Android/data/<pkg>/...`) while every Java-level file operation on them is redirected to your custom folders underneath (hooks on libcore `Os`, `UnixFileSystem`, `File` natives and SQLite).

## App Backups & Universal Discovery

Open an app's settings -> **Backups** (or the global **Backups** tab in the main navigation):
- **Universal Backup Discovery:** Automatically discovers backups across all locations (`Android/media/uninviting/backup/`, `CustomData/Backups/`, and custom user paths), displaying app icons, labels, package identifiers, file size, and creation timestamp.
- **Granular Internal Storage Selection:** Choose exactly what to include from redirected internal data (`files`, `databases`, `shared_prefs`, `no_backup`, `cache`) with live item-count detection.
- **Full Management:** Direct **Restore**, **Rename**, and **Delete** actions available per-app and globally.
- **Clean Restore:** Supports deleting existing folder contents prior to extraction or in-place overwrite.
- **Compression:** Normal / Maximum / Fast / None (store only).

## Use

1. Install `release/StorageRedirect-v2.22.apk`, open it, grant all-files access, configure defaults and/or per-app rules.
2. Patch the target app with LSPatch and embed this module.
3. The **target app** needs all-files access (`MANAGE_EXTERNAL_STORAGE`, Android 11+).
4. Rules and overrides take effect immediately with live reload.

## Build and Test

```bash
# Automated tests:
./run_tests.sh    # -> Executes PathEngine test suite (58 tests) + Security suite (50 tests) [108 total, 100% pass]

# Build APK:
export KEYSTORE_PASS="your_password"
./build.sh        # -> Builds signed release/StorageRedirect-v2.22.apk
```

## Security Architecture

- **ZIP Slip Prevention:** All ZIP extraction operations (`RedirectConfig.safeResolveZipEntry` and `Backups.restore`) resolve canonical paths against canonical base directories, strictly enforcing boundary containment and rejecting absolute paths, traversal sequences (`../`), Windows-style separators (`..\`), drive prefixes (`C:`), null bytes (`\0`), and intermediate symlinks. Decompression is guarded by total-size and entry-count limits.
- **Backup Metadata Validation:** Restore targets in `backup-info.properties` are strictly validated against approved application storage roots. Rejects any attempt to target the storage root itself (`/storage/emulated/0`), the backup archive itself, module configuration directories (`Android/media/uninviting`), root Android data containers (`/Android/data`), or unauthorized system directories.
- **Protected Clean Restore:** Before any destructive deletion occurs, all target directories are pre-validated. Symbolic links are detected and unlinked without recursively traversing into target destinations outside approved directories. Any failure to delete immediately aborts restoration with an error.
- **Package Name Sanitization:** All package names passed via intents, file lookups, or metadata are validated against strict Android package naming rules, eliminating path traversal risks via `EXTRA_PKG` or crafted configs.
- **Hook Recursion Guards:** Method hooks and NIO translators use `ThreadLocal<Boolean>` recursion guards to prevent re-entrant loops during directory creation and path translation.
- **Build Credential Security:** No hardcoded signing credentials in build scripts. Passwords are provided via secure interactive prompt or `KEYSTORE_PASS` environment variable, never echoed, and unset immediately after signing.

Xposed API classes in `stubs/` are compile-only stubs and are not packaged into the output APK.

## Limitations

- Native C/C++ code directly issuing syscalls (`open`/`fopen` inside native shared libraries without calling Java APIs) is not intercepted.
- `java.nio.file` operations are supported on Android 8.0+ (API 26+); Android versions below API 26 fall back to standard `java.io.*` and `libcore.io.Linux` hooks.
- Main storage only affects the app's own file calls, not files written by system services.
