package com.clipvault.smartclipboard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Note queries used by the list, search, keyboard and clipboard service.
 * Ordering, favorites, trash, search and merge are all expressed here so the
 * repository does not hand-roll SQL.
 */
@Dao
abstract class NoteDao {

    @Query("SELECT * FROM notes")
    abstract fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrash = 0 ORDER BY position ASC, createdAt DESC")
    abstract fun observeActiveByPosition(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isFavorite = 1 ORDER BY position ASC")
    abstract fun observeFavorites(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrash = 1 ORDER BY trashedAt DESC")
    abstract fun observeTrash(): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE isTrash = 0
          AND (title LIKE '%' || :query || '%' ESCAPE '\'
            OR body LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY updatedAt DESC
        """,
    )
    abstract fun search(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    abstract fun observeById(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    abstract suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    abstract suspend fun getByIds(ids: List<Long>): List<NoteEntity>

    @Query("SELECT * FROM notes")
    abstract suspend fun snapshot(): List<NoteEntity>

    @Query("DELETE FROM notes")
    abstract suspend fun deleteAll()

    @Query("SELECT * FROM notes WHERE isTrash = 0 AND body = :body LIMIT 1")
    abstract suspend fun findActiveByExactBody(body: String): NoteEntity?

    @Query("SELECT COALESCE(MIN(position), 0) FROM notes WHERE isTrash = 0")
    abstract suspend fun minActivePosition(): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM notes WHERE isTrash = 0")
    abstract suspend fun maxActivePosition(): Int

    @Insert
    abstract suspend fun insert(note: NoteEntity): Long

    @Update
    abstract suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    abstract suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM notes WHERE isTrash = 1")
    abstract suspend fun emptyTrash()

    @Query(
        """
        DELETE FROM notes
        WHERE isTrash = 1 AND trashedAt IS NOT NULL AND trashedAt < :before
        """,
    )
    abstract suspend fun deleteTrashOlderThan(before: Long): Int

    @Query("UPDATE notes SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    abstract suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    @Query("UPDATE notes SET isTrash = 1, trashedAt = :now, updatedAt = :now WHERE id = :id")
    abstract suspend fun moveToTrash(id: Long, now: Long)

    @Query("UPDATE notes SET isTrash = 1, trashedAt = :now, updatedAt = :now WHERE id IN (:ids)")
    abstract suspend fun moveAllToTrash(ids: List<Long>, now: Long)

    @Query("UPDATE notes SET isTrash = 0, trashedAt = NULL, updatedAt = :now WHERE id = :id")
    abstract suspend fun restore(id: Long, now: Long)

    @Query("UPDATE notes SET isTrash = 0, trashedAt = NULL, updatedAt = :now WHERE id IN (:ids)")
    abstract suspend fun restoreAll(ids: List<Long>, now: Long)

    @Query("UPDATE notes SET position = :position WHERE id = :id")
    abstract suspend fun updatePosition(id: Long, position: Int)

    @Query("UPDATE notes SET categoryId = :categoryId, updatedAt = :now WHERE id = :id")
    abstract suspend fun setCategory(id: Long, categoryId: Long?, now: Long)

    @Query("UPDATE notes SET categoryId = :target, updatedAt = :now WHERE categoryId IN (:sources)")
    abstract suspend fun reassignCategories(sources: List<Long>, target: Long, now: Long)

    @Query("UPDATE notes SET categoryId = :target, updatedAt = :now WHERE categoryId = :source")
    abstract suspend fun reassignCategory(source: Long, target: Long, now: Long)

    @Transaction
    open suspend fun replaceManualOrder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id -> updatePosition(id, index) }
    }

    /**
     * Insert a combined note and optionally trash the sources, atomically.
     * Body/title are built by [com.clipvault.smartclipboard.data.repo.NotesRepository]
     * using the user's merge separator.
     */
    @Transaction
    open suspend fun mergeIntoNew(
        title: String,
        body: String,
        categoryId: Long?,
        position: Int,
        now: Long,
        trashSourceIds: List<Long>,
    ): Long {
        val id = insert(
            NoteEntity(
                title = title,
                body = body,
                categoryId = categoryId,
                position = position,
                createdAt = now,
                updatedAt = now,
            ),
        )
        if (trashSourceIds.isNotEmpty()) moveAllToTrash(trashSourceIds, now)
        return id
    }
}
