package com.onlyou.com.domain.repository

import android.content.Context
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

/** 이메일 로그인 시 계정은 맞지만 이메일 인증이 아직 안 된 경우. */
class EmailNotVerifiedException : Exception("이메일 인증이 완료되지 않았습니다.")

interface AuthRepository {
    val currentUser: Flow<FirebaseUser?>

    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser>

    /**
     * 이메일/비밀번호 회원가입.
     * 계정 생성 → 표시 이름 설정 → 인증 메일 발송까지 수행한다.
     * 성공해도 이메일 인증 전이므로 앱 진입은 인증 확인 후에 해야 한다.
     */
    suspend fun signUpWithEmail(name: String, email: String, password: String): Result<FirebaseUser>

    /**
     * 이메일/비밀번호 로그인.
     * 인증이 안 된 계정이면 [EmailNotVerifiedException]으로 실패한다(인증 메일은 재발송됨).
     */
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser>

    /** 현재 사용자 정보를 새로고침해 이메일 인증 완료 여부를 반환한다. 인증 완료 시 서버에 프로필을 기록한다. */
    suspend fun reloadAndCheckEmailVerified(): Result<Boolean>

    /** 인증 메일 재발송. */
    suspend fun resendVerificationEmail(): Result<Unit>

    /** 비밀번호 재설정 메일 발송. */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    suspend fun signOut()

    /** 회원 탈퇴: 서버 개인정보 파기 → Firebase 계정 삭제 → 로컬 데이터 삭제. */
    suspend fun deleteAccount(context: Context): Result<Unit>
}
