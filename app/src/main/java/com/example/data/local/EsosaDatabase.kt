package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatRoomEntity::class,
        RoomMessageEntity::class,
        AiThreadEntity::class,
        AiMessageEntity::class,
        LiveVoiceThreadEntity::class,
        LiveVoiceMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class EsosaDatabase : RoomDatabase() {
    abstract fun esosaDao(): EsosaDao

    companion object {
        @Volatile
        private var INSTANCE: EsosaDatabase? = null

        fun getInstance(context: Context): EsosaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    EsosaDatabase::class.java,
                    "esosa_chat.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
