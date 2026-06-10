package com.kiduyuk.klausk.kiduyutv.ui.components

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import androidx.core.content.edit

@Composable
fun TraktAnnouncementDialog(
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var showDialog by remember { 
        mutableStateOf(!prefs.getBoolean("trakt_announcement_shown", false)) 
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { 
                showDialog = false 
                prefs.edit { putBoolean("trakt_announcement_shown", true) }
            },
            containerColor = CardDark,
            title = {
                Text(
                    text = "Trakt Integration Active!",
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Trakt.tv integration is now fully functional! You can now sync your watch history, collection, and watchlist across all your devices.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog = false
                        prefs.edit().putBoolean("trakt_announcement_shown", true).apply()
                        onSettingsClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        prefs.edit().putBoolean("trakt_announcement_shown", true).apply()
                    }
                ) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }
}
