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
        InventoryStockEntity::class,
        ProcessedCloudReceiptEntity::class,
        ProcurementEntryEntity::class,
        PriceListVersionEntity::class,
        PriceListItemEntity::class,
        ReceiptEntity::class,
        ReceiptItemEntity::class,
        AppStateEntity::class,
    ],
    version = 9,
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
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .addMigrations(MIGRATION_5_6)
                    .addMigrations(MIGRATION_6_7)
                    .addMigrations(MIGRATION_7_8)
                    .addMigrations(MIGRATION_8_9)
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

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `price_list_versions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `effectiveDateLabel` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_price_list_versions_effectiveDateLabel` ON `price_list_versions` (`effectiveDateLabel`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_price_list_versions_isActive` ON `price_list_versions` (`isActive`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `price_list_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `versionId` INTEGER NOT NULL,
                `categoryName` TEXT NOT NULL,
                `productName` TEXT NOT NULL,
                `priceCents` INTEGER NOT NULL,
                `emoji` TEXT NOT NULL,
                `accentColor` INTEGER NOT NULL,
                `sortOrder` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_price_list_items_versionId` ON `price_list_items` (`versionId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_price_list_items_categoryName` ON `price_list_items` (`categoryName`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_price_list_items_productName` ON `price_list_items` (`productName`)"
        )
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `inventory_stock` (
                `productId` INTEGER NOT NULL,
                `quantityUnits` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`productId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_inventory_stock_updatedAt` ON `inventory_stock` (`updatedAt`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `processed_cloud_receipts` (
                `cafeId` TEXT NOT NULL,
                `receiptId` TEXT NOT NULL,
                `appliedAt` INTEGER NOT NULL,
                PRIMARY KEY(`cafeId`, `receiptId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_processed_cloud_receipts_appliedAt` ON `processed_cloud_receipts` (`appliedAt`)"
        )
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `procurement_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `productId` INTEGER NOT NULL,
                `productName` TEXT NOT NULL,
                `quantityUnits` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `note` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_procurement_entries_productId` ON `procurement_entries` (`productId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_procurement_entries_createdAt` ON `procurement_entries` (`createdAt`)"
        )
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE receipts ADD COLUMN note TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN imageDataUrl TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_state ADD COLUMN canUseHouseAccount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_state ADD COLUMN canUseMusic INTEGER NOT NULL DEFAULT 0")
    }
}
