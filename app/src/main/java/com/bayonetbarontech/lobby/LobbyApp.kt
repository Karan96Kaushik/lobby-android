package com.bayonetbarontech.lobby

import android.app.Application
import com.bayonetbarontech.lobby.download.HlsDownloadManager
import com.bayonetbarontech.lobby.remote.RemoteHub

class LobbyApp : Application() {
    lateinit var downloads: HlsDownloadManager
        private set
    lateinit var remote: RemoteHub
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        downloads = HlsDownloadManager(this)
        remote = RemoteHub(this)
    }

    companion object {
        lateinit var instance: LobbyApp
            private set
    }
}
