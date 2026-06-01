package com.onlyou.com.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onlyou.com.domain.model.MiyaAlarm
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.AlarmRepository
import com.onlyou.com.domain.repository.PersonaRepository
import com.onlyou.com.util.AlarmScheduler
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
class AlarmViewModel
    @Inject
    constructor(
        private val repository: AlarmRepository,
        private val personaRepository: PersonaRepository,
        private val scheduler: AlarmScheduler,
    ) : ViewModel() {
        private val _singleAlarm = MutableStateFlow<MiyaAlarm?>(null)
        val singleAlarm: StateFlow<MiyaAlarm?> = _singleAlarm.asStateFlow()

        val personas: StateFlow<List<Persona>> = personaRepository
            .getAllPersonas()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        init {
            loadSingleAlarm()
        }

        private fun loadSingleAlarm() {
            viewModelScope.launch {
                repository.getAllAlarms().collect { alarms ->
                    if (alarms.isEmpty()) {
                        // 기본 알람 생성 (ID=1)
                        val defaultAlarm = MiyaAlarm(id = 1, time = LocalTime.of(7, 0))
                        repository.insertAlarm(defaultAlarm)
                        _singleAlarm.value = defaultAlarm
                    } else {
                        // 첫 번째 알람을 메인 알람으로 사용
                        _singleAlarm.value = alarms.first()
                    }
                }
            }
        }

        fun saveAlarm(
            time: LocalTime,
            personaId: String,
            title: String?,
            repeatDays: Set<DayOfWeek>,
            date: LocalDate?,
            isWeatherEnabled: Boolean,
        ) {
            val current = _singleAlarm.value ?: return
            viewModelScope.launch {
                val alarmToSave = current.copy(
                    time = time,
                    personaId = personaId,
                    title = title,
                    repeatDays = repeatDays,
                    date = date,
                    isEnabled = true,
                    isWeatherEnabled = isWeatherEnabled,
                )
                repository.updateAlarm(alarmToSave)
                scheduler.schedule(alarmToSave)
                _singleAlarm.value = alarmToSave
            }
        }

        fun toggleAlarm(enabled: Boolean) {
            val current = _singleAlarm.value ?: return
            val updated = current.copy(isEnabled = enabled)
            viewModelScope.launch {
                repository.updateAlarm(updated)
                if (updated.isEnabled) {
                    scheduler.schedule(updated)
                } else {
                    scheduler.cancel(updated)
                }
                _singleAlarm.value = updated
            }
        }
    }
