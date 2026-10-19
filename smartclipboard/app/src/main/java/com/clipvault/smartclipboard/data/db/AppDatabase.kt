package com.clipvault.smartclipboard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [NoteEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao

    suspend fun importSnapshot(importedCategories: List<CategoryEntity>, importedNotes: List<NoteEntity>) {
        withTransaction {
            noteDao().deleteAll()
            categoryDao().deleteAll()
            importedCategories.forEach { categoryDao().insert(it) }
            importedNotes.forEach { noteDao().insert(it) }
        }
    }

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        private fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, "clipvault.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed after the connection is usable. Room forbids queries
                        // inside onCreate on the same SupportSQLiteDatabase.
                        CoroutineScope(Dispatchers.IO).launch {
                            get(context).categoryDao().insert(
                                CategoryEntity(name = "Inbox", isDefault = true, position = 0),
                            )
                        }
                    }
                })
                .build()
        }
    }
}
