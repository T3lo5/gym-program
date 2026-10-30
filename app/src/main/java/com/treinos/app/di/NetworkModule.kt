package com.treinos.app.di

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.rx2.RxPreferenceDataStoreBuilder
import androidx.datastore.preferences.rx2.preferencesDataStore
import com.treinos.shared.util.BackendApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val KEY_BACKEND_URL = "backend_base_url"
    private const val DEFAULT_BACKEND_URL = "http://10.0.2.2:3000/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor()
        logging.level = HttpLoggingInterceptor.Level.BODY
        
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideBackendBaseUrl(@ApplicationContext context: Context): String {
        // This will be overridden by the dynamic URL from SettingsViewModel
        // For initial creation, we use the default
        val dataStore = context.preferencesDataStore("treinos_settings")
        val url = dataStore.data
            .map { prefs -> prefs.getString("backend_base_url") ?: DEFAULT_BACKEND_URL }
            .blockingFirst()
        return url
    }

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
            .addConverterFactory(Json {
                ignoreUnknownKeys = true
                isLenient = true
            }.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideBackendApi(retrofit: Retrofit): BackendApi = retrofit.create(BackendApi::class.java)

    @Provides
    @Singleton
    fun provideBackendRepository(api: BackendApi): com.treinos.shared.util.BackendRepository = com.treinos.shared.util.BackendRepository(api)
}