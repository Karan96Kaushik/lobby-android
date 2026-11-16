package com.karankaushik.lobby.ui

import android.view.KeyEvent

object FireTvKeySink {
    var handler: ((KeyEvent) -> Boolean)? = null
}
