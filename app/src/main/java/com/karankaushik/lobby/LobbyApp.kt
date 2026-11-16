package com.karankaushik.lobby

import android.app.Application
import com.karankaushik.lobby.download.HlsDownloadManager

class LobbyApp : Application() {
    lateinit var downloads: HlsDownloadManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        downloads = HlsDownloadManager(this)
    }

    companion object {
        lateinit var instance: LobbyApp
            private set
    }
}
