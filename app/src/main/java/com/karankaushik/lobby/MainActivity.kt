package com.karankaushik.lobby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karankaushik.lobby.ui.BrowserScreen
import com.karankaushik.lobby.ui.LobbyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LobbyApp
        setContent {
            LobbyTheme {
                BrowserScreen(
                    controller = viewModel(),
                    downloads = app.downloads,
                    remote = app.remote
                )
            }
        }
    }
}
