package io.github.gasserma.quadranttwodo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class, LogEntity::class, TagEntity::class, TaskTagEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class QuadrantTwoDoDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun logDao(): LogDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val FILE_NAME = "quadranttwodo.db"

        fun build(context: Context): QuadrantTwoDoDatabase =
            Room.databaseBuilder(context, QuadrantTwoDoDatabase::class.java, FILE_NAME).build()

        fun inMemory(context: Context): QuadrantTwoDoDatabase =
            Room.inMemoryDatabaseBuilder(context, QuadrantTwoDoDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
