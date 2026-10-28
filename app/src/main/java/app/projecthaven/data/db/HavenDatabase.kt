// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import app.projecthaven.domain.notes.Note
import app.projecthaven.domain.notes.NoteTag
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes", indices = [Index(value = ["uuid"], unique = true)])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val title: String,
    val body: String,
    val tag: String?,
    val pinned: Boolean,
    val favourite: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    @ColumnInfo(name = "accuracy_m") val accuracyMeters: Float?,
) {
    fun toDomain() = Note(id, uuid, title, body, NoteTag.fromName(tag), pinned, favourite, createdAt, updatedAt,
        latitude, longitude, accuracyMeters)

    companion object {
        fun from(n: Note) = NoteEntity(n.id, n.uuid, n.title, n.body, n.tag?.name, n.pinned, n.favourite,
            n.createdAt, n.updatedAt, n.latitude, n.longitude, n.accuracyMeters)
    }
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Query("SELECT uuid FROM notes")
    suspend fun allUuids(): List<String>

    @Query("SELECT * FROM notes")
    suspend fun all(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoringConflicts(notes: List<NoteEntity>): List<Long>

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int
}

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    @ColumnInfo(name = "is_primary") val isPrimary: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY is_primary DESC, created_at ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY is_primary DESC, created_at ASC")
    suspend fun all(): List<ContactEntity>

    @Insert
    suspend fun insert(contact: ContactEntity): Long

    @Update
    suspend fun update(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun delete(id: Long)

    /** One statement, so it is atomic: exactly the given contact becomes primary. */
    @Query("UPDATE contacts SET is_primary = (id = :id)")
    suspend fun makePrimary(id: Long)
}

@Database(entities = [NoteEntity::class, ContactEntity::class], version = 1, exportSchema = true)
abstract class HavenDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    abstract fun contacts(): ContactDao

    companion object {
        const val NAME = "haven.db"
    }
}
