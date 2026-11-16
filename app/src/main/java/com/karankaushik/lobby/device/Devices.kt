package com.karankaushik.lobby.device

import android.content.Context
import android.os.Build

fun Context.isFireTv(): Boolean {
    if (packageManager.hasSystemFeature("amazon.hardware.fire_tv")) return true
    if (!Build.MANUFACTURER.equals("Amazon", ignoreCase = true)) return false
    return Build.MODEL.orEmpty().startsWith("AFT", ignoreCase = true)
}
