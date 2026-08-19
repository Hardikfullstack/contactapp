package com.example.contactapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class FakeCallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ANSWER -> FakeCallManager.answerFromUi()
            ACTION_DECLINE -> FakeCallManager.endFromUi()
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.example.contactapp.FAKE_ACTION_ANSWER"
        const val ACTION_DECLINE = "com.example.contactapp.FAKE_ACTION_DECLINE"
    }
}
