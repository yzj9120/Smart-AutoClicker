package com.buzbuz.smartautoclicker.play

interface NERtcManagerCallback {

    fun onUserJoined(uid: Long)
    fun onUserLeave(uid: Long, reason: Int)
}