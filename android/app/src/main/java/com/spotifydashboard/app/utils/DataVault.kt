package com.spotifydashboard.app.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Keeps a copy of the app's private data in a public folder so a reinstall
 * does not wipe the sign-ins, settings and library.
 *
 * Android deletes everything under /data/data/<pkg> when an app is
 * uninstalled, which is why a fresh install always asked for the Spotify and
 * media-server logins again. This vault mirrors `shared_prefs` (settings,
 * Spotify tokens, server URL + JWT) and `databases` (the Room library) into:
 *
 *  * `Documents/reSpotIt` — visible in the Files app, survives uninstall.
 *    Needs "All files access" on Android 11+ (the app offers a button for it).
 *  * `Android/media/<pkg>/reSpotIt` — the fallback. No permission needed and
 *    it also survives uninstall, it is just less discoverable.
 *
 * On a fresh install [restoreIfEmpty] copies the vault back before any
 * SharedPreferences or database is opened, so the app comes up already
 * signed in.
 */
object DataVault {
    private const val TAG = "DataVault"

    /** Public folder name under Documents (and under Android/media/<pkg>). */
    const val FOLDER_NAME = "reSpotIt"

    /** Written last, so a half-finished copy is never treated as a valid vault. */
    private const val MARKER = "vault-info.txt"

    private const val TMP_SUFFIX = ".vault-tmp"

    /** Private directories mirrored in both directions. */
    private val MIRRORED_DIRS = listOf("shared_prefs", "databases")

    /** Name of the Room database file (see AppDatabase.getDatabase). */
    private const val LIBRARY_DB = "albums_db"

    /** Prefs that only exist once the user has actually used the app. */
    private val KEY_PREFS = listOf("spotify_prefs", "dashboard_remote_prefs")

    data class Snapshot(
        val folder: String,
        val usingDocuments: Boolean,
        val lastSaved: Long,
        val fileCount: Int,
        val needsPermission: Boolean
    ) {
        val lastSavedText: String
            get() = if (lastSaved <= 0L) "Not saved yet"
            else "Last saved: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(lastSaved))
    }

    // -- Location -----------------------------------------------------------

    private fun documentsRoot(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER_NAME)

    private fun mediaRoot(context: Context): File? =
        context.getExternalMediaDirs()?.firstOrNull()?.let { File(it, FOLDER_NAME) }

    /** True when the app may write to the real public Documents folder. */
    fun hasDocumentsAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

    /** Where the vault lives right now: Documents when allowed, media dir otherwise. */
    fun rootDir(context: Context): File =
        vaultRoots(context).firstOrNull() ?: File(context.filesDir, FOLDER_NAME)

    /**
     * Every place the vault can live, best first. `Documents` is the visible
     * one, but it needs "All files access", which Android revokes on
     * uninstall. `Android/media/<pkg>` needs no permission and also survives
     * uninstall, so it is what makes a reinstall restore automatically.
     */
    private fun vaultRoots(context: Context): List<File> {
        val roots = mutableListOf<File>()
        if (hasDocumentsAccess(context)) roots += documentsRoot()
        mediaRoot(context)?.let { roots += it }
        return roots
    }

    /** Human-readable folder, e.g. `Documents/reSpotIt`. */
    fun displayPath(context: Context): String =
        if (hasDocumentsAccess(context)) {
            "Documents/$FOLDER_NAME"
        } else {
            "Android/media/${context.packageName}/$FOLDER_NAME"
        }

    /** Secondary copy the app always keeps, shown under the main folder. */
    fun fallbackPath(context: Context): String =
        "Android/media/${context.packageName}/$FOLDER_NAME"

    /**
     * Settings intent that grants "All files access" (Android 11+). Returns
     * null on older versions, where a normal WRITE_EXTERNAL_STORAGE runtime
     * permission is enough.
     */
    fun requestDocumentsAccessIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val perApp = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        return if (perApp.resolveActivity(context.packageManager) != null) {
            perApp
        } else {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    }

    fun snapshot(context: Context): Snapshot {
        val roots = vaultRoots(context)
        var lastSaved = 0L
        var fileCount = 0
        for (root in roots) {
            val marker = File(root, MARKER)
            if (marker.isFile) lastSaved = maxOf(lastSaved, marker.lastModified())
            val count = MIRRORED_DIRS.sumOf { dir ->
                File(root, dir).listFiles()?.count { it.isFile && !it.name.endsWith(TMP_SUFFIX) } ?: 0
            }
            fileCount = maxOf(fileCount, count)
        }
        return Snapshot(
            folder = displayPath(context),
            usingDocuments = hasDocumentsAccess(context),
            lastSaved = lastSaved,
            fileCount = fileCount,
            needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasDocumentsAccess(context)
        )
    }

    // -- Restore ------------------------------------------------------------

    /**
     * Copies the vault back into the app's private storage, but only when the
     * app has no data of its own yet (i.e. right after a fresh install).
     * Must run before any SharedPreferences or Room database is opened.
     */
    fun restoreIfEmpty(context: Context): Boolean {
        return try {
            if (hasLocalData(context)) {
                Log.i(TAG, "local data present, not restoring")
                return false
            }
            val source = vaultRoots(context)
                .filter { File(it, MARKER).isFile }
                .maxByOrNull { File(it, MARKER).lastModified() }
            if (source == null) {
                Log.i(TAG, "no vault found")
                return false
            }
            var restored = 0
            for (name in MIRRORED_DIRS) {
                val src = File(source, name)
                if (!src.isDirectory) continue
                val dst = File(context.dataDir, name)
                dst.mkdirs()
                restored += copyDirContents(src, dst, onlyIfMissing = true)
            }
            Log.i(TAG, "restored $restored file(s) from ${source.absolutePath}")
            restored > 0
        } catch (e: Exception) {
            Log.w(TAG, "restore failed", e)
            false
        }
    }

    /** True when the app already has its own prefs or library. */
    private fun hasLocalData(context: Context): Boolean {
        val prefsDir = File(context.dataDir, "shared_prefs")
        // WorkManager writes androidx.work.*.xml before Application.onCreate,
        // so only our own files count as "the user has data".
        val hasKeyPrefs = KEY_PREFS.any { File(prefsDir, "$it.xml").isFile }
        val hasDb = File(context.dataDir, "databases/$LIBRARY_DB").isFile
        return hasKeyPrefs || hasDb
    }

    // -- Backup -------------------------------------------------------------

    /**
     * Mirrors prefs + library into the vault. Blocking I/O: call from a
     * background thread. Returns true when the marker was written.
     */
    @Synchronized
    fun backup(context: Context): Boolean {
        return try {
            // Fold the WAL back into the .db so the copy is self-contained.
            checkpointLibrary(context)
            var savedAny = false
            for (root in vaultRoots(context)) {
                if (!root.isDirectory && !root.mkdirs()) {
                    Log.w(TAG, "cannot create ${root.absolutePath}")
                    continue
                }
                var copied = 0
                for (name in MIRRORED_DIRS) {
                    val src = File(context.dataDir, name)
                    if (!src.isDirectory) continue
                    val dst = File(root, name)
                    dst.mkdirs()
                    copied += copyDirContents(src, dst)
                }
                File(root, MARKER).writeText(
                    buildString {
                        appendLine("reSpotIt data vault")
                        appendLine("package=${context.packageName}")
                        appendLine("saved=${System.currentTimeMillis()}")
                        appendLine("files=$copied")
                    }
                )
                Log.i(TAG, "saved $copied file(s) to ${root.absolutePath}")
                savedAny = true
            }
            savedAny
        } catch (e: Exception) {
            Log.w(TAG, "backup failed", e)
            false
        }
    }

    /**
     * Runs `PRAGMA wal_checkpoint(TRUNCATE)` so `albums_db` contains the
     * latest rows without needing the -wal sidecar. Best effort: a busy
     * checkpoint just leaves the WAL in place, which is copied too.
     */
    private fun checkpointLibrary(context: Context) {
        val dbFile = context.getDatabasePath(LIBRARY_DB)
        if (!dbFile.isFile) return
        try {
            SQLiteDatabase.openDatabase(
                dbFile.path,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "wal checkpoint skipped", e)
        }
    }

    // -- Copying ------------------------------------------------------------

    private fun copyDirContents(src: File, dst: File, onlyIfMissing: Boolean = false): Int {
        var copied = 0
        for (file in src.listFiles().orEmpty()) {
            if (!file.isFile) continue
            if (file.name.endsWith(TMP_SUFFIX)) continue
            val target = File(dst, file.name)
            if (onlyIfMissing && target.exists()) continue
            val tmp = File(dst, file.name + TMP_SUFFIX)
            try {
                file.inputStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                }
                if (target.exists() && !target.delete()) {
                    tmp.delete()
                    continue
                }
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
                copied++
            } catch (e: Exception) {
                Log.w(TAG, "could not copy ${file.name}", e)
                tmp.delete()
            }
        }
        return copied
    }
}
