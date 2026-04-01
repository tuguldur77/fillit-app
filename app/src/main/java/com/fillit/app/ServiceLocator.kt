package com.fillit.app

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.fillit.app.data.remote.SearchPlacesApi
import com.fillit.app.preferences.UserPreferencesRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ServiceLocator {
    private const val BASE_URL = "http://10.0.2.2:3000/" // Emulator loopback

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val okHttp: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val token = try {
                    runBlocking { auth.currentUser?.getIdToken(false)?.await()?.token }
                } catch (e: Exception) {
                    Log.w("ServiceLocator", "Failed to get Firebase ID token: ${e.message}")
                    null
                }
                val reqBuilder = chain.request().newBuilder()
                if (!token.isNullOrBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer $token")
                }
                chain.proceed(reqBuilder.build())
            }
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val searchPlacesApi: SearchPlacesApi by lazy {
        retrofit.create(SearchPlacesApi::class.java)
    }

    fun getDataStore(context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            corruptionHandler = null,
            migrations = emptyList(),
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("settings") }
        )
    }

    fun getUserPreferencesRepository(context: Context): UserPreferencesRepository {
        return UserPreferencesRepository(context)
    }
}
