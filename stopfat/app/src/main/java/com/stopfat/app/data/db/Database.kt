package com.stopfat.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "food_entries", indices = [Index("epochDay")])
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val meal: String,
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val source: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Glycemic index of the product, -1 when unknown. */
    @androidx.room.ColumnInfo(defaultValue = "-1") val gi: Int = -1,
)

@Entity(tableName = "weights")
data class WeightEntry(
    @PrimaryKey val epochDay: Long,
    val kg: Double,
)

@Entity(tableName = "day_stats")
data class DayStat(
    @PrimaryKey val epochDay: Long,
    val waterMl: Int = 0,
    val steps: Int = 0,
)

@Entity(tableName = "workout_logs", indices = [Index("epochDay")])
data class WorkoutLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val name: String,
    val minutes: Int,
    val kcal: Int,
    val completedSets: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "custom_foods")
data class CustomFood(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val unitGrams: Double = 100.0,
    val barcode: String? = null,
    val lastUsed: Long = System.currentTimeMillis(),
)

@Entity(tableName = "fasting_logs")
data class FastingLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMillis: Long,
    val endMillis: Long,
    val targetHours: Int,
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromUser: Boolean,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

data class DayKcal(val epochDay: Long, val kcal: Double)

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_entries WHERE epochDay = :day ORDER BY createdAt")
    fun entriesForDay(day: Long): Flow<List<FoodEntry>>

    @Query("SELECT * FROM food_entries WHERE epochDay = :day")
    suspend fun entriesForDayOnce(day: Long): List<FoodEntry>

    @Query("SELECT epochDay, SUM(kcal) AS kcal FROM food_entries WHERE epochDay BETWEEN :from AND :to GROUP BY epochDay ORDER BY epochDay")
    fun kcalPerDay(from: Long, to: Long): Flow<List<DayKcal>>

    @Query("SELECT DISTINCT epochDay FROM food_entries ORDER BY epochDay DESC LIMIT 400")
    fun loggedDays(): Flow<List<Long>>

    @Query("SELECT * FROM food_entries WHERE id IN (SELECT MAX(id) FROM food_entries GROUP BY name) ORDER BY createdAt DESC LIMIT 20")
    fun recent(): Flow<List<FoodEntry>>

    @Query("SELECT COUNT(*) FROM food_entries WHERE source = :source")
    fun countBySource(source: String): Flow<Int>

    @Insert
    suspend fun insert(entry: FoodEntry): Long

    @Insert
    suspend fun insertAll(entries: List<FoodEntry>)

    @Update
    suspend fun update(entry: FoodEntry)

    @Query("DELETE FROM food_entries WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weights ORDER BY epochDay")
    fun all(): Flow<List<WeightEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntry)

    @Query("DELETE FROM weights WHERE epochDay = :day")
    suspend fun delete(day: Long)
}

@Dao
interface DayStatDao {
    @Query("SELECT * FROM day_stats WHERE epochDay = :day")
    fun day(day: Long): Flow<DayStat?>

    @Query("SELECT * FROM day_stats WHERE epochDay = :day")
    suspend fun dayOnce(day: Long): DayStat?

    @Query("SELECT * FROM day_stats WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    fun range(from: Long, to: Long): Flow<List<DayStat>>

    @Query("SELECT * FROM day_stats ORDER BY epochDay")
    fun all(): Flow<List<DayStat>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: DayStat)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_logs ORDER BY createdAt DESC")
    fun all(): Flow<List<WorkoutLog>>

    @Query("SELECT * FROM workout_logs WHERE epochDay = :day")
    fun forDay(day: Long): Flow<List<WorkoutLog>>

    @Insert
    suspend fun insert(log: WorkoutLog)

    @Query("DELETE FROM workout_logs WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CustomFoodDao {
    @Query("SELECT * FROM custom_foods ORDER BY lastUsed DESC")
    fun all(): Flow<List<CustomFood>>

    @Query("SELECT * FROM custom_foods WHERE barcode = :code LIMIT 1")
    suspend fun byBarcode(code: String): CustomFood?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: CustomFood): Long

    @Query("DELETE FROM custom_foods WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface FastingDao {
    @Query("SELECT * FROM fasting_logs ORDER BY startMillis DESC")
    fun all(): Flow<List<FastingLog>>

    @Insert
    suspend fun insert(log: FastingLog)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAt")
    fun all(): Flow<List<ChatMessage>>

    @Insert
    suspend fun insert(msg: ChatMessage)

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}

@Database(
    entities = [FoodEntry::class, WeightEntry::class, DayStat::class, WorkoutLog::class, CustomFood::class, FastingLog::class, ChatMessage::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun food(): FoodDao
    abstract fun weights(): WeightDao
    abstract fun dayStats(): DayStatDao
    abstract fun workouts(): WorkoutDao
    abstract fun customFoods(): CustomFoodDao
    abstract fun fasting(): FastingDao
    abstract fun chat(): ChatDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "stopfat.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()

        /** v2: glycemic index per food entry (insulin-resistance mode). Keeps all user data. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_entries ADD COLUMN gi INTEGER NOT NULL DEFAULT -1")
            }
        }
    }
}
