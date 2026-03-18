package com.nemuria.miya.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nemuria.miya.domain.model.MiyaAlarm
import com.nemuria.miya.domain.repository.AlarmRepository
import com.nemuria.miya.util.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler
) : ViewModel() {

    val alarms: StateFlow<List<MiyaAlarm>> = repository.getAllAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editingAlarm = MutableStateFlow<MiyaAlarm?>(null)
    val editingAlarm: StateFlow<MiyaAlarm?> = _editingAlarm.asStateFlow()

    fun startEditing(alarm: MiyaAlarm?) {
        _editingAlarm.value = alarm ?: MiyaAlarm(id = 0)
    }

    fun stopEditing() {
        _editingAlarm.value = null
    }

    fun saveAlarm(
        time : LocalTime,
        voiceId: String,
        title: String?,
        repeatDays: Set<DayOfWeek>,
        date: LocalDate?
    ) {
        val current = _editingAlarm.value ?: return
        viewModelScope.launch {
            val alarmToSave = current.copy(
                time = time,
                voiceId = voiceId,
                title = title,
                repeatDays = repeatDays,
                date = date,
                isEnabled = true
            )
            if (alarmToSave.id == 0) {
                val newId = repository.insertAlarm(alarmToSave)
                scheduler.schedule(alarmToSave.copy(id = newId))
            } else {
                repository.updateAlarm(alarmToSave)
                scheduler.schedule(alarmToSave)
            }
            stopEditing()
        }
    }

    fun toggleAlarm(alarm: MiyaAlarm) {
        val updated = alarm.copy(isEnabled = !alarm.isEnabled)
        viewModelScope.launch {
            repository.updateAlarm(updated)
            if (updated.isEnabled) {
                scheduler.schedule(updated)
            } else {
                scheduler.cancel(updated)
            }
        }
    }

    fun deleteAlarm(alarm: MiyaAlarm) {
        viewModelScope.launch {
            repository.deleteAlarm(alarm)
            scheduler.cancel(alarm)
        }
        if (_editingAlarm.value?.id == alarm.id) {
            stopEditing()
        }
    }
}
