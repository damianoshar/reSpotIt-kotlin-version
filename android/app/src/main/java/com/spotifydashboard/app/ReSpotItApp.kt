package com.spotifydashboard.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.spotifydashboard.app.utils.DataVault
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Restores the public data vault on a fresh install and mirrors data back to
 * it whenever the app leaves the foreground.
 *
 * The restore has to happen here, before MainActivity builds its repositories,
 * because SharedPreferences and the Room database are opened lazily and would
 * otherwise read the (empty) fresh-install state.
 */
class ReSpotItApp : Application(), Application.ActivityLifecycleCallbacks {

    private val vaultExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "respotit-vault").apply { isDaemon = true }
    }
    private val saving = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        val restored = DataVault.restoreIfEmpty(this)
        Log.i(
            TAG,
            if (restored) "restored data from ${DataVault.displayPath(this)}"
            else "no restore needed (${DataVault.displayPath(this)})"
        )
        registerActivityLifecycleCallbacks(this)
    }

    /**
     * Mirrors prefs and the library into the vault on a background thread.
     * Concurrent calls collapse into one save.
     */
    fun saveToVault() {
        if (!saving.compareAndSet(false, true)) return
        vaultExecutor.execute {
            try {
                DataVault.backup(this)
            } catch (e: Exception) {
                Log.w(TAG, "vault save failed", e)
            } finally {
                saving.set(false)
            }
        }
    }

    /** Leaving the app is the cheapest safe moment to snapshot everything. */
    override fun onActivityStopped(activity: Activity) {
        saveToVault()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    private companion object {
        const val TAG = "ReSpotItApp"
    }
}
