package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.domain.model.SavedLocation
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_locations")
data class SavedLocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val adminRegion: String,
    val country: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val elevationMeters: Double?,
    val isCurrentGps: Boolean,
    val sortOrder: Int
)

fun SavedLocationEntity.toDomain(): SavedLocation = SavedLocation(
    id = id,
    name = name,
    adminRegion = adminRegion,
    country = country,
    countryCode = countryCode,
    latitude = latitude,
    longitude = longitude,
    timezone = timezone,
    elevationMeters = elevationMeters,
    isCurrentGps = isCurrentGps,
    sortOrder = sortOrder
)

fun SavedLocation.toEntity(): SavedLocationEntity = SavedLocationEntity(
    id = id,
    name = name,
    adminRegion = adminRegion,
    country = country,
    countryCode = countryCode,
    latitude = latitude,
    longitude = longitude,
    timezone = timezone,
    elevationMeters = elevationMeters,
    isCurrentGps = isCurrentGps,
    sortOrder = sortOrder
)

@Entity(tableName = "cached_weather")
data class CachedWeatherEntity(
    @PrimaryKey val locationId: String,
    val payloadJson: String,
    val fetchedAtUtcMillis: Long
)

@Entity(tableName = "verification_snapshots")
data class VerificationSnapshotEntity(
    @PrimaryKey val regionId: String,
    val reportJson: String,
    val generatedAtUtcMillis: Long
)

@Dao
interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY isCurrentGps DESC, sortOrder ASC")
    fun observeAllLocations(): Flow<List<SavedLocationEntity>>

    @Query("SELECT * FROM saved_locations ORDER BY isCurrentGps DESC, sortOrder ASC")
    suspend fun getAllLocationsOnce(): List<SavedLocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocation(location: SavedLocationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(locations: List<SavedLocationEntity>)

    @Query("DELETE FROM saved_locations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM saved_locations WHERE isCurrentGps = 1")
    suspend fun clearGpsLocations()
}

@Dao
interface CachedWeatherDao {
    @Query("SELECT * FROM cached_weather WHERE locationId = :locationId LIMIT 1")
    suspend fun getByLocationId(locationId: String): CachedWeatherEntity?

    @Query("SELECT * FROM cached_weather")
    fun observeAllCached(): Flow<List<CachedWeatherEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCache(cache: CachedWeatherEntity)

    @Query("DELETE FROM cached_weather WHERE locationId = :locationId")
    suspend fun deleteCache(locationId: String)
}

@Dao
interface VerificationSnapshotDao {
    @Query("SELECT * FROM verification_snapshots WHERE regionId = :regionId LIMIT 1")
    suspend fun getReport(regionId: String): VerificationSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReport(snapshot: VerificationSnapshotEntity)
}

@Database(
    entities = [
        SavedLocationEntity::class,
        CachedWeatherEntity::class,
        VerificationSnapshotEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AetherDatabase : RoomDatabase() {
    abstract fun savedLocationDao(): SavedLocationDao
    abstract fun cachedWeatherDao(): CachedWeatherDao
    abstract fun verificationSnapshotDao(): VerificationSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: AetherDatabase? = null

        fun getInstance(context: Context): AetherDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AetherDatabase::class.java,
                    "aether_weather.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
