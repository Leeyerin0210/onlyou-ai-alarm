package com.onlyou.com.data.repository

import com.onlyou.com.data.local.ChatDao
import com.onlyou.com.data.remote.MiyaApiService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "모든 기억 초기화" — 유저에 대해 앱과 서버가 들고 있는 것을 전부 지운다.
 *
 * 기억은 서버가 단일 소유하므로 지울 대상은 두 군데다:
 *  1. 서버 벡터 기억 (pgvector의 fact/triple/insight) — DELETE /memory/clear
 *  2. 로컬 대화 기록 (Room chat_messages)
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
        private val apiService: MiyaApiService,
    ) {
        /** 전부 지워졌으면 true. 서버 삭제가 실패하면 로컬을 건드리지 않고 false. */
        suspend fun resetAll(): Boolean {
            val serverCleared = try {
                apiService.clearMemory().isSuccessful
            } catch (e: Exception) {
                // 오프라인 등 — 예외를 삼키되 반드시 실패로 보고한다.
                false
            }
            if (!serverCleared) return false

            chatDao.clearHistory()
            return true
        }
    }
