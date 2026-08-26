package com.example.contactapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.contactapp.data.local.AppDatabase
import com.example.contactapp.util.AfterCallNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires when an After Call "remind me to call back" alarm goes off — shows the reminder
 * notification and removes the (now-fired) reminder row. */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        // Not Hilt-injected — BroadcastReceivers registered in the manifest (as this one must be,
        // to receive AlarmManager broadcasts after process death) aren't @AndroidEntryPoint here,
        // so the DAO is built directly rather than via DI.
        val dao = androidx.room.Room.databaseBuilder(appContext, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()
            .reminderDao()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = dao.getById(reminderId)
                if (reminder != null) {
                    AfterCallNotificationHelper.showCallBackNotification(
                        appContext, reminder.id, reminder.number, reminder.contactName, reminder.note
                    )
                    dao.deleteById(reminderId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
