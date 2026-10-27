package org.attentionstats.qualification.storage

import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase

/** Artificial rows only; this is not the history/consent schema. */
@Entity(tableName = "synthetic_records")
data class FixtureRecord(@PrimaryKey val id: Int, val marker: String)

@Dao
interface FixtureDao {
    @Insert
    suspend fun insert(records: List<FixtureRecord>)

    @Query("SELECT COUNT(*) FROM synthetic_records")
    suspend fun count(): Int

    @Query("SELECT marker FROM synthetic_records WHERE id = :id")
    suspend fun marker(id: Int): String
}

@Database(entities = [FixtureRecord::class], version = 1, exportSchema = false)
abstract class FixtureDatabase : RoomDatabase() {
    abstract fun records(): FixtureDao
}
