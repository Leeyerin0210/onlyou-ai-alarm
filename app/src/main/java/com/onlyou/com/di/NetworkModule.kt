package com.onlyou.com.di

import android.util.Log
import com.onlyou.com.BuildConfig
import com.onlyou.com.data.remote.AuthInterceptor
import com.onlyou.com.data.remote.MiyaApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // 릴리스에선 네트워크 로그를 남기지 않는다. 디버그에서만 헤더 로깅.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }

        return OkHttpClient
            .Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            // 스트리밍이므로 타임아웃을 넉넉히 잡음. 디버그는 Mac 로컬 TTS(tts-server/local_app.py)가
            // 알람 대본 전체를 합성하는 데 1분 넘게 걸려 더 길게 둔다
            .readTimeout(if (BuildConfig.DEBUG) 300L else 60L, TimeUnit.SECONDS)
            .connectTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideMiyaApiService(retrofit: Retrofit): MiyaApiService = retrofit.create(MiyaApiService::class.java)
}
