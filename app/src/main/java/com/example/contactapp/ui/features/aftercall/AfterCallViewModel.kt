package com.example.contactapp.ui.features.aftercall

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactapp.data.local.dao.ReminderDao
import com.example.contactapp.data.local.entity.ReminderEntity
import com.example.contactapp.domain.model.CallLogItem
import com.example.contactapp.domain.repository.CallLogRepository
import com.example.contactapp.util.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AfterCallViewModel @Inject constructor(
    private val callLogRepository: CallLogRepository,
    private val reminderDao: ReminderDao,
    @ApplicationContext context: Context
) : ViewModel() {

    private val alarmScheduler = AlarmScheduler(context)

    fun callHistory(number: String): Flow<List<CallLogItem>> = callLogRepository.fetchCallHistory(number)

    fun remindersFor(number: String): Flow<List<ReminderEntity>> = reminderDao.getAllByNumberFlow(number)

    fun saveReminder(
        existing: ReminderEntity?,
        number: String,
        contactName: String?,
        note: String,
        targetMillis: Long,
        colorIndex: Int
    ) {
        viewModelScope.launch {
            if (existing != null) {
                alarmScheduler.cancelReminder(existing.id)
                reminderDao.insert(existing.copy(note = note, reminderTimeMillis = targetMillis, colorIndex = colorIndex))
                alarmScheduler.scheduleReminder(existing.id, targetMillis)
            } else {
                val id = reminderDao.insert(
                    ReminderEntity(number = number, contactName = contactName, reminderTimeMillis = targetMillis, note = note, colorIndex = colorIndex)
                )
                alarmScheduler.scheduleReminder(id, targetMillis)
            }
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            alarmScheduler.cancelReminder(reminder.id)
            reminderDao.deleteById(reminder.id)
        }
    }
}
