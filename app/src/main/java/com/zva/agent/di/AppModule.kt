package com.zva.agent.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.zva.agent.data.api.AgentApi
import com.zva.agent.data.db.MemoryDatabase
import com.zva.agent.data.db.MemoryDao
import com.zva.agent.data.db.MessageDao
import com.zva.agent.data.db.SessionDao
import com.zva.agent.domain.tool.ToolRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.openai.com/") // default, overridden per-call via @Url
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideAgentApi(retrofit: Retrofit): AgentApi =
        retrofit.create(AgentApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MemoryDatabase =
        Room.databaseBuilder(context, MemoryDatabase::class.java, "zva_memory.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMessageDao(db: MemoryDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideMemoryDao(db: MemoryDatabase): MemoryDao = db.memoryDao()

    @Provides
    fun provideSessionDao(db: MemoryDatabase): SessionDao = db.sessionDao()

    @Provides
    @Singleton
    fun provideToolRegistry(): ToolRegistry = ToolRegistry()
}
