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

`getCodeCacheDir` and the `webview` dir are deliberately left in real private storage.

## Look and feel

Material You / Material 3 Expressive: **dynamic colors** from the system palette on Android 12+ (baseline
Material 3 palette on older versions), light and dark following the system, rounded-square app icons
and tiles, grouped list tiles, tonal and filled buttons, Material-style dialogs.

## Configuration folder (optional)

By default the settings live in `/sdcard/CustomData/.config`. On the home screen, **Configuration folder**
lets you choose any other folder (absolute, or relative to `/sdcard`). The existing settings are moved there
and the module remembers the location in one small pointer file, `/sdcard/CustomData/.config-location`
(the hooked apps read it first, so they always find the settings). **Reset to default** moves them back.

## Folder picker

Every path box has a **Browse** button (destination folders, and both boxes of each path redirect); the
block and never-redirect lists have **Browse and add...**. The picker opens at the folder already typed
(or its nearest existing parent), lists folders first, opens a folder on tap and returns the open folder
with **Select this folder**. In the redirect and list pickers files are listed too, and tapping a file
picks it. **Up**, **Storage** (back to the storage root) and **New folder** are in the bar. It needs the
all-files access the app already uses for its settings.

## Path redirects (per app, optional)

In an app's settings (or the defaults), tap **Add redirect**. Each redirect is two text boxes: the **Original
path** on top and the **Redirect to** path under it (stored as `/original => /redirect`). Both need full
paths starting with `/` (`{pkg}` becomes the app's package name); the screen warns about anything else and
the module ignores it.

When the app touches the original path, or anything below it, the I/O happens at the redirect path instead
(`/a/x/y` goes to `/b/x/y`). The app keeps using the original path. The redirect's parent folder is created at
start; if that fails the rule is dropped and logged.

- **Wildcards:** `*` matches any name inside one folder level, `**` matches anything (several levels).
  `{1}`, `{2}`... in the redirect path are replaced by what the 1st, 2nd... wildcard matched:
  `/sdcard/Android/data/*/cache => /sdcard/Onedroid/cache/{1}`. A `{n}` without a matching wildcard drops the rule.
- **Priority:** rules are checked from the top of the list and the first match wins (Up/Down buttons reorder).
  Before v2.13 the longest source won; put specific rules above general ones. A path that is already at a
  redirect path is not redirected again.
- **Never redirect:** a separate list of paths (wildcards allowed) that no rule and no folder redirect touches.
- Works with or without "Show standard paths"; spellings of the same place match (`/sdcard`,
  `/storage/emulated/0`, `/data/data/<pkg>` = `/data/user/0/<pkg>`). Java file I/O only; native code and
  `java.nio.file` are not covered, and a listing of the original's parent folder does not show the redirect.

## Block creation (per app, optional)

List **paths** (one per line) that must never be created: absolute, or relative to `/storage/emulated/0`;
`{pkg}` becomes the package name. `*` and `**` work: `/x/logs/**` blocks everything inside `logs` (not the
folder itself), `/x/logs/*` only its direct children. Without wildcards a path must match exactly. `mkdir`,
`open(O_CREAT)` and `rename` fail with *permission denied*. Different spellings of the same place match, as
do the standard and redirected location of a folder. Java file I/O only.

Upgrading from v2.9: the old `blockNames` list and the *Block everywhere* switch are gone; re-enter the
entries as paths.

## Fake standard paths (per app, optional)

With **Show standard paths to the app** on, the app keeps seeing the normal paths
(`/data/user/0/<pkg>/...`, `/storage/emulated/0/...`, `/storage/emulated/0/Android/data/<pkg>/...`)
while every Java-level file operation on them is redirected to your custom folders underneath
(hooks on libcore `Os`, `UnixFileSystem`, `File` natives and SQLite). Use it for apps that insist on
`/storage/emulated/0/` paths. Native (C/C++/Unity) file I/O and `java.nio.file` are not covered, so leave
it off for apps that pass paths to native code. `code_cache` and `lib` stay in real private storage.

## Backups (per app)

Open an app's settings -> **Backups**. Creates plain `.zip` archives of that app's redirected folders
(internal data / app external data / main storage, pick which).

- Multiple backups per app; each has **Restore**, **Rename** and **Delete** buttons in the list.
- **Restore** extracts the backup back into the exact folders it was made from (even if the app's folders
  were set differently since) and points the app's redirect settings at those paths again, so the app
  finds its data on the next launch. Optional: delete the existing files in those folders first.
  Force stop the app before restoring.
- File name template per app (`{pkg}-{date}-{time}` by default; `{app}` `{ver}` also work) and you can
  edit the name each time you tap **Back up now**.
- Compression: Normal / Maximum / Fast / None. Backup folder is configurable
  (default `/sdcard/CustomData/Backups/<package>`); it is never included in its own backups.
- Zip layout: `backup-info.properties` (package, source folders), `internal/...`, `external/...`, `storage/...`.
- Backs up the *redirected* folders (readable without root). If an app's internal redirect is off,
  its real `/data/data` can't be read by a rootless module.

## Use

1. Install `releases/StorageRedirect-v2.9.apk`, open it, grant all-files access, pick the defaults and/or an app and its switches.
2. Patch the target app with LSPatch and embed this module.
3. The **target app** also needs all-files access (`MANAGE_EXTERNAL_STORAGE`, Android 11+),
   granted before first launch. Without it the hooks fall back to the default paths
   (logged as `[StorageRedirect] cannot create ...`).
4. Force stop the app after changing settings; they are read at app start.

Settings are plain files (rootless mode has no shared-preferences channel between module and target):

```
/sdcard/CustomData/.config-location                      pointer to the configuration folder (only if moved)
/sdcard/CustomData/.config/redirect.properties          defaults (folder is configurable)
/sdcard/CustomData/.config/apps/<package>.properties    per-app override (file exists = override on)
/sdcard/CustomData/.config/backup/<package>.properties   per-app backup settings
```

## Build

```
sudo apt install openjdk-21-jdk-headless aapt apksigner dalvik-exchange libandroid-23-java zipalign
./build.sh        # -> build/StorageRedirect.apk (signed with a throwaway keystore.jks, gitignored)
```

Xposed API classes in `stubs/` are compile-only stubs and are not packaged.

## Limitations

- Native code (`open`/`fopen`) and NIO `Path` calls are not intercepted (fake-path mode is Java-level too).
- Main storage only affects the app's own file calls, not files written by system services.
- The app list shows user-installed (and updated system) apps with a launcher; it uses `QUERY_ALL_PACKAGES`.
