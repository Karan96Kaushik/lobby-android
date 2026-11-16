package com.karankaushik.lobby

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karankaushik.lobby.ui.BrowserScreen
import com.karankaushik.lobby.ui.FireTvKeySink
import com.karankaushik.lobby.ui.LobbyTheme
import com.karankaushik.lobby.web.VideoFullscreen

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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (FireTvKeySink.handler?.invoke(event) == true) return true
        if (VideoFullscreen.dispatch(event)) return true
        return super.dispatchKeyEvent(event)
    }
}
