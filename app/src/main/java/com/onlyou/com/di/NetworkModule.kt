package com.onlyou.com.di

import com.onlyou.com.data.remote.MiyaApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    // 로컬 에뮬레이터에서 띄운 FastAPI 테스트용 주소 (10.0.2.2)
    private const val BASE_URL = "http://10.0.2.2:8000/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideMiyaApiService(retrofit: Retrofit): MiyaApiService = retrofit.create(MiyaApiService::class.java)
}
