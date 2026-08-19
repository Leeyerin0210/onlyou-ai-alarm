package com.onlyou.com.data.repository

import com.onlyou.com.data.local.ChatDao
import com.onlyou.com.data.local.MemoryDao
import com.onlyou.com.data.remote.MiyaApiService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "모든 기억 초기화" — 유저에 대해 앱과 서버가 들고 있는 것을 전부 지운다.
 *
 * 지우는 대상이 세 군데로 나뉘어 있다:
 *  1. 서버 벡터 기억 (pgvector의 fact/triple/insight) — DELETE /memory/clear
 *  2. 로컬 기억 (Room memories) — USER_NOTE가 매 채팅 요청의 user_notes로
 *     프롬프트에 실리므로, 이걸 남기면 서버만 지워도 AI는 유저를 계속 안다
 *  3. 로컬 대화 기록 (Room chat_messages)
 *
 * 순서가 계약의 핵심이다. 서버를 먼저 지우고 성공했을 때만 로컬을 지운다 —
 * 반대로 하면 서버 삭제 실패 시 "AI는 기억하는데 유저 화면엔 아무것도 없는"
 * 상태가 되고, 이건 초기화가 아예 안 된 것보다 나쁘다.
 */
@Singleton
class MemoryResetter
    @Inject
    constructor(
        private val chatDao: ChatDao,
        private val memoryDao: MemoryDao,
        private val apiService: MiyaApiService,
    ) {
        /** 전부 지워졌으면 true. 서버 삭제가 실패하면 로컬을 건드리지 않고 false. */
        suspend fun resetAll(): Boolean {
            val serverCleared = try {
                apiService.clearMemory().isSuccessful
            } catch (e: Exception) {
                // 오프라인 등 — 예외를 삼키되 반드시 실패로 보고한다.
                // (과거 ChatRepositoryImpl.clearHistory는 예외를 삼키고 성공처럼 보였다)
                false
            }
            if (!serverCleared) return false

            memoryDao.clearAll()
            chatDao.clearHistory()
            return true
        }
    }
