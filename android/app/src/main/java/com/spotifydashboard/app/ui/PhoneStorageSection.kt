package com.spotifydashboard.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.spotifydashboard.app.ui.theme.SpotifyLightGray
import com.spotifydashboard.app.utils.DataVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings card for the public data vault: shows where the copy lives, lets
 * the user grant Documents access and forces a save on demand.
 */
@Composable
fun PhoneStorageSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var state by remember { mutableStateOf(DataVault.snapshot(context)) }
    var busy by remember { mutableStateOf(false) }
    var justSaved by remember { mutableStateOf(false) }

    // Coming back from the system "All files access" screen must refresh.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state = DataVault.snapshot(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val legacyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { state = DataVault.snapshot(context) }

    Text("Saved copy", fontWeight = FontWeight.Medium)
    Text(
        "Sign-ins, settings and the library are copied to the phone so reinstalling the app keeps them.",
        style = MaterialTheme.typography.bodySmall,
        color = SpotifyLightGray
    )
    Spacer(Modifier.height(10.dp))
    Text(state.folder, style = MaterialTheme.typography.bodyMedium)
    Text(
        if (state.fileCount > 0) "${state.fileCount} files · ${state.lastSavedText}" else state.lastSavedText,
        style = MaterialTheme.typography.bodySmall,
        color = SpotifyLightGray
    )
    Text(
        "A second copy is kept in ${DataVault.fallbackPath(context)}; a reinstall restores whichever is newest.",
        style = MaterialTheme.typography.bodySmall,
        color = SpotifyLightGray,
        modifier = Modifier.padding(top = 4.dp)
    )

    if (!state.usingDocuments) {
        Text(
            "Grant storage access to also keep a visible copy in Documents/reSpotIt.",
            style = MaterialTheme.typography.bodySmall,
            color = SpotifyLightGray,
            modifier = Modifier.padding(top = 8.dp)
        )
        OutlinedButton(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    DataVault.requestDocumentsAccessIntent(context)?.let { intent ->
                        runCatching { context.startActivity(intent) }
                    }
                } else {
                    legacyPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("Save to Documents")
        }
    }

    Button(
        onClick = {
            busy = true
            justSaved = false
            scope.launch {
                withContext(Dispatchers.IO) {
                    DataVault.backup(context)
                }
                state = DataVault.snapshot(context)
                busy = false
                justSaved = true
            }
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        Text(if (busy) "Saving…" else if (justSaved) "Saved ✓" else "Save now")
    }
}
