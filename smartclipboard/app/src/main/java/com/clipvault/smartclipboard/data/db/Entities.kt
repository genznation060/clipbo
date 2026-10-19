package com.clipvault.smartclipboard.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-defined folder. [isDefault] marks the bucket new clips land in.
 * [position] is the drawer order.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["position"])],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isDefault: Boolean = false,
    val position: Int = 0,
)

/**
 * A saved clip or manually written note.
 *
 * Required columns: id, title, body, categoryId, isFavorite, isTrash, position, createdAt.
 * [updatedAt] and [trashedAt] support dirty-checks and the auto-delete-trash worker
 * without overloading [createdAt].
 */
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("categoryId"),
        Index("position"),
        Index("isFavorite"),
        Index("isTrash"),
    ],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val categoryId: Long?,
    val isFavorite: Boolean = false,
    val isTrash: Boolean = false,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val trashedAt: Long? = null,
)
