package com.playground.siply.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        ProductEntity::class,
        ReceiptEntity::class,
        ReceiptItemEntity::class,
        AppStateEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun posDao(): PosDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "brzi-konobar.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_state ADD COLUMN cloudApiKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE app_state ADD COLUMN cloudAppId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE app_state ADD COLUMN cloudProjectId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE app_state ADD COLUMN cloudCafeId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE app_state ADD COLUMN cloudCafeName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE app_state ADD COLUMN cloudUserId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE app_state ADD COLUMN cloudUserName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE app_state ADD COLUMN cloudUserRole TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE app_state ADD COLUMN cloudInviteCode TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN cloudCafeId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE products ADD COLUMN cloudProductId TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_products_cloudCafeId_cloudProductId ON products(cloudCafeId, cloudProductId)"
        )
    }
}
