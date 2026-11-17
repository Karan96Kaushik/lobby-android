package com.bayonetbarontech.lobby

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bayonetbarontech.lobby.ui.BrowserScreen
import com.bayonetbarontech.lobby.ui.FireTvKeySink
import com.bayonetbarontech.lobby.ui.LobbyTheme
import com.bayonetbarontech.lobby.web.VideoFullscreen

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
