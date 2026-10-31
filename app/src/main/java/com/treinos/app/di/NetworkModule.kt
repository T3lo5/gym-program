package com.treinos.app.di

import com.treinos.shared.util.BackendApi
import com.treinos.shared.util.BackendRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val DEFAULT_BACKEND_URL = "http://10.0.2.2:3000/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideBackendBaseUrl(): String = DEFAULT_BACKEND_URL

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, baseUrl: String): Retrofit {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideBackendApi(retrofit: Retrofit): BackendApi = retrofit.create(BackendApi::class.java)

    @Provides
    @Singleton
    fun provideBackendRepository(api: BackendApi): BackendRepository = BackendRepository(api)
}
