package com.onlyou.com.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.onlyou.com.data.local.AiScheduleDao
import com.onlyou.com.data.local.ChatDao
import com.onlyou.com.data.local.MemoryDao
import com.onlyou.com.data.remote.MiyaApiService
import com.onlyou.com.domain.repository.BackupRepository
import com.onlyou.com.domain.repository.BackupState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class BackupRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val scheduleDao: AiScheduleDao,
    private val memoryDao: MemoryDao,
    private val api: MiyaApiService,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) : BackupRepository {

    private val prefs = context.getSharedPreferences("miya_backup_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    override val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val _restoreState = MutableStateFlow<BackupState>(BackupState.Idle)
    override val restoreState: StateFlow<BackupState> = _restoreState.asStateFlow()

    private val _lastBackupTime = MutableStateFlow<String?>(prefs.getString("last_backup_time", null))
    override val lastBackupTime: StateFlow<String?> = _lastBackupTime.asStateFlow()

    override suspend fun backupData() = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _backupState.value = BackupState.Error("로그인이 필요합니다.")
            return@withContext
        }

        _backupState.value = BackupState.Loading

        try {
            // 1. 모든 데이터 조회
            val chats = chatDao.getChatMessagesOnce()
            val schedules = scheduleDao.getAllSchedulesOnce()
            val memories = memoryDao.getAllMemoriesOnce()

            // 2. 직렬화
            // 3. 서버 업로드
            api.putBackup(
                com.onlyou.com.data.remote.BackupDto(
                    chats = gson.toJson(chats),
                    schedules = gson.toJson(schedules),
                    memories = gson.toJson(memories),
                    timestamp = System.currentTimeMillis(),
                ),
            )

            // 4. 로컬 시간 업데이트
            val timeString = SimpleDateFormat("yyyy.MM.dd a hh:mm", Locale.KOREA).format(Date())
            prefs.edit().putString("last_backup_time", timeString).apply()
            _lastBackupTime.value = timeString

            _backupState.value = BackupState.Success
        } catch (e: Exception) {
            e.printStackTrace()
            _backupState.value = BackupState.Error(e.message ?: "백업 중 오류가 발생했습니다.")
        }
    }

    override suspend fun restoreData() = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _restoreState.value = BackupState.Error("로그인이 필요합니다.")
            return@withContext
        }

        _restoreState.value = BackupState.Loading

        try {
            // 1. 서버에서 다운로드
            val response = api.getBackup()
            if (response.code() == 404 || response.body() == null) {
                _restoreState.value = BackupState.Error("클라우드에 저장된 백업 데이터가 없습니다.")
                return@withContext
            }
            val backup = response.body()!!
            val chatsJson = backup.chats
            val schedulesJson = backup.schedules
            val memoriesJson = backup.memories

            // 2. 역직렬화
            val chatsType = object : com.google.gson.reflect.TypeToken<List<com.onlyou.com.data.local.ChatMessageEntity>>() {}.type
            val schedulesType = object : com.google.gson.reflect.TypeToken<List<com.onlyou.com.data.local.AiScheduleEntity>>() {}.type
            val memoriesType = object : com.google.gson.reflect.TypeToken<List<com.onlyou.com.data.local.MemoryEntity>>() {}.type

            val chats: List<com.onlyou.com.data.local.ChatMessageEntity> = gson.fromJson(chatsJson, chatsType)
            val schedules: List<com.onlyou.com.data.local.AiScheduleEntity> = gson.fromJson(schedulesJson, schedulesType)
            val memories: List<com.onlyou.com.data.local.MemoryEntity> = gson.fromJson(memoriesJson, memoriesType)

            // 3. 기존 데이터 삭제 후 일괄 삽입
            chatDao.clearHistory()
            chats.forEach { chatDao.insertMessage(it) }

            // scheduleDao에는 clearAll이 없으므로 개별 삭제 혹은 유지할 수 있으나 깔끔한 복원을 위해
            // 여기서는 덮어쓰기 전략 사용 (기존에 있던 것을 전부 가져와 삭제 후 삽입)
            val existingSchedules = scheduleDao.getAllSchedulesOnce()
            existingSchedules.forEach { scheduleDao.deleteSchedule(it) }
            schedules.forEach { scheduleDao.insertSchedule(it) }

            memoryDao.clearAll()
            memories.forEach { memoryDao.insertMemory(it) }

            _restoreState.value = BackupState.Success
        } catch (e: Exception) {
            e.printStackTrace()
            _restoreState.value = BackupState.Error(e.message ?: "복원 중 오류가 발생했습니다.")
        }
    }
}
