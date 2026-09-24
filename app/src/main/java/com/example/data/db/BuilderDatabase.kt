package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        BuildHistoryEntity::class,
        ToolEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BuilderDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun buildHistoryDao(): BuildHistoryDao
    abstract fun toolDao(): ToolDao

    companion object {
        @Volatile
        private var INSTANCE: BuilderDatabase? = null

        fun getDatabase(context: Context): BuilderDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BuilderDatabase::class.java,
                    "atp_builder_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
