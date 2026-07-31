package no.bikespeed.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.Instant

class InstantConverters {
    @TypeConverter
    fun fromEpochMillis(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun toEpochMillis(value: Instant?): Long? = value?.toEpochMilli()
}

@Database(
    entities = [TripEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(InstantConverters::class)
abstract class BikeSpeedDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        fun build(context: Context): BikeSpeedDatabase =
            Room.databaseBuilder(context, BikeSpeedDatabase::class.java, "bikespeed.db")
                .build()

        /** Parallel-safe test database with nothing on disk — the analogue of the iOS in-memory ModelContainer. */
        fun inMemory(context: Context): BikeSpeedDatabase =
            Room.inMemoryDatabaseBuilder(context, BikeSpeedDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
