package com.clipvault.smartclipboard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CategoryDao {

    @Query("SELECT * FROM categories ORDER BY position ASC, name COLLATE NOCASE ASC")
    abstract fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY position ASC")
    abstract suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    abstract suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE isDefault = 1 LIMIT 1")
    abstract suspend fun getDefault(): CategoryEntity?

    @Query("DELETE FROM categories")
    abstract suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(position), -1) FROM categories")
    abstract suspend fun maxPosition(): Int

    @Insert
    abstract suspend fun insert(category: CategoryEntity): Long

    @Update
    abstract suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id AND isDefault = 0")
    abstract suspend fun deleteIfNotDefault(id: Long): Int

    @Query("UPDATE categories SET isDefault = 0")
    abstract suspend fun clearDefault()

    @Query("UPDATE categories SET isDefault = 1 WHERE id = :id")
    abstract suspend fun markDefault(id: Long)

    @Transaction
    open suspend fun setDefault(id: Long) {
        clearDefault()
        markDefault(id)
    }

    /**
     * Move every note that lived in [sourceIds] onto [targetId], then drop the
     * source folders (the default folder is never deleted).
     */
    @Transaction
    open suspend fun mergeInto(sourceIds: List<Long>, targetId: Long, noteDao: NoteDao, now: Long) {
        if (sourceIds.isEmpty()) return
        noteDao.reassignCategories(sourceIds, targetId, now)
        sourceIds.forEach { deleteIfNotDefault(it) }
    }
}
