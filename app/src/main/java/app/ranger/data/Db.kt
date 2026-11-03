package app.ranger.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RangerDao {
    @Query("SELECT * FROM runs ORDER BY start") fun runs(): Flow<List<Run>>
    @Query("SELECT * FROM runs ORDER BY start") suspend fun runList(): List<Run>
    @Query("SELECT * FROM runs WHERE id = :id") suspend fun run(id: Long): Run?
    @Query("SELECT * FROM runs WHERE start BETWEEN :from AND :to") suspend fun runsBetween(from: Long, to: Long): List<Run>
    @Query("SELECT * FROM runs WHERE weatherDone = 0 AND lat IS NOT NULL ORDER BY start DESC LIMIT :n") suspend fun needWeather(n: Int): List<Run>
    @Query("SELECT * FROM runs WHERE terrainDone = 0 AND hasTrack = 1 ORDER BY start DESC LIMIT :n") suspend fun needTerrain(n: Int): List<Run>
    @Query("SELECT COUNT(*) FROM runs") suspend fun count(): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(r: Run): Long
    @Update suspend fun update(r: Run)
    @Delete suspend fun delete(r: Run)

    @Query("SELECT * FROM shoes ORDER BY addedAt") fun shoes(): Flow<List<Shoe>>
    @Query("SELECT * FROM shoes ORDER BY addedAt") suspend fun shoeList(): List<Shoe>
    @Insert suspend fun insert(s: Shoe): Long
    @Update suspend fun update(s: Shoe)
    @Delete suspend fun delete(s: Shoe)
    @Query("UPDATE runs SET shoeId = NULL WHERE shoeId = :id") suspend fun unshoe(id: Long)

    @Query("SELECT * FROM companions ORDER BY addedAt") fun companions(): Flow<List<Friend>>
    @Insert suspend fun insert(c: Friend): Long
    @Update suspend fun update(c: Friend)
    @Delete suspend fun delete(c: Friend)
    @Query("DELETE FROM run_companions WHERE companionId = :id") suspend fun unlinkCompanion(id: Long)

    @Query("SELECT * FROM run_companions") fun links(): Flow<List<RunCompanion>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun link(l: RunCompanion)
    @Query("DELETE FROM run_companions WHERE runId = :run AND companionId = :c") suspend fun unlink(run: Long, c: Long)
    @Query("DELETE FROM run_companions WHERE runId = :run") suspend fun unlinkRun(run: Long)

    @Query("SELECT * FROM plays ORDER BY at") fun plays(): Flow<List<Play>>
    @Query("SELECT * FROM plays ORDER BY at DESC LIMIT 1") suspend fun lastPlay(): Play?
    @Insert suspend fun insert(p: Play): Long
    @Update suspend fun update(p: Play)
    @Query("DELETE FROM plays WHERE at < :before") suspend fun prunePlays(before: Long)

    @Query("SELECT * FROM wounds ORDER BY at") fun wounds(): Flow<List<Wound>>
    @Insert suspend fun insert(w: Wound): Long
    @Update suspend fun update(w: Wound)
    @Delete suspend fun delete(w: Wound)

    @Query("SELECT * FROM days ORDER BY date") fun days(): Flow<List<Day>>
    @Query("SELECT * FROM days WHERE date = :date") suspend fun day(date: String): Day?
    @Upsert suspend fun upsert(d: Day)

    @Query("SELECT * FROM quests ORDER BY startedAt") fun quests(): Flow<List<Quest>>
    @Insert suspend fun insert(q: Quest): Long
    @Update suspend fun update(q: Quest)
    @Delete suspend fun delete(q: Quest)

    @Transaction
    suspend fun forget(r: Run) { unlinkRun(r.id); delete(r) }
}

@Database(entities = [Run::class, Shoe::class, Friend::class, RunCompanion::class, Play::class, Wound::class, Day::class, Quest::class], version = 2, exportSchema = true)
abstract class RangerDb : RoomDatabase() {
    abstract fun dao(): RangerDao

    companion object {
        /** 1 -> 2: steps per day. */
        private val TO_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE days ADD COLUMN steps INTEGER")
                db.execSQL("ALTER TABLE days ADD COLUMN stepsByHand INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(ctx: Context): RangerDb = Room.databaseBuilder(ctx, RangerDb::class.java, "ranger.db").addMigrations(TO_2).build()
        fun memory(ctx: Context): RangerDb = Room.inMemoryDatabaseBuilder(ctx, RangerDb::class.java).allowMainThreadQueries().build()
    }
}
