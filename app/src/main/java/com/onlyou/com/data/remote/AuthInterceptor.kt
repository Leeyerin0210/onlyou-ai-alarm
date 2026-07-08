package com.onlyou.com.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 모든 백엔드 요청에 Firebase ID 토큰을 부착한다.
 * 인터셉터는 OkHttp 워커 스레드에서 실행되므로 Tasks.await 블로킹이 안전하다.
 * 미로그인/토큰 획득 실패 시 헤더 없이 진행한다(백엔드가 401 반환).
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val user = firebaseAuth.currentUser ?: return chain.proceed(request)
        val token = try {
            // getIdToken(false): 캐시 사용, 만료 시에만 자동 갱신
            Tasks.await(user.getIdToken(false)).token
        } catch (e: Exception) {
            null
        }
        return if (token != null) {
            chain.proceed(
                request.newBuilder().header("Authorization", "Bearer $token").build(),
            )
        } else {
            chain.proceed(request)
        }
    }
}
