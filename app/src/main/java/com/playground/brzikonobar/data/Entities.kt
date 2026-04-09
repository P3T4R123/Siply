package com.playground.siply.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "products",
    indices = [
        Index("categoryId"),
        Index(value = ["cloudCafeId", "cloudProductId"]),
    ],
)
data class ProductEntity(
    @PrimaryKey val id: Long,
    val categoryId: Long,
    val name: String,
    val priceCents: Int,
    val emoji: String,
    val accentColor: Long,
    val sortOrder: Int,
    val cloudCafeId: String = "",
    val cloudProductId: String = "",
)

@Entity(
    tableName = "receipts",
    indices = [Index("createdAt"), Index("businessDayKey")],
)
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptNumber: String,
    val createdAt: Long,
    val businessDayKey: String,
    val totalCents: Int,
)

@Entity(
    tableName = "receipt_items",
    indices = [Index("receiptId"), Index("productId")],
)
data class ReceiptItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val productId: Long,
    val productName: String,
    val unitPriceCents: Int,
    val quantity: Int,
    val lineTotalCents: Int,
)

@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val id: Int = 1,
    val statsAnchorEpochMs: Long = 0L,
    val currentBusinessDayKey: String = "",
    val lastReceiptDayKey: String = "",
    val lastReceiptSequence: Int = 0,
    val darkMode: Boolean = false,
    val cloudApiKey: String = "",
    val cloudAppId: String = "",
    val cloudProjectId: String = "",
    val cloudCafeId: String = "",
    val cloudCafeName: String = "",
    val cloudUserId: String = "",
    val cloudUserName: String = "",
    val cloudUserRole: String = "",
    val cloudInviteCode: String = "",
)

data class CatalogRow(
    val productId: Long,
    val productName: String,
    val priceCents: Int,
    val emoji: String,
    val accentColor: Long,
    val productSortOrder: Int,
    val categoryId: Long,
    val categoryName: String,
    val categorySortOrder: Int,
)

data class DailyStatsRow(
    val receiptsCount: Long,
    val totalCents: Long,
)

data class SalesSummaryRow(
    val productName: String,
    val quantity: Long,
    val totalCents: Long,
)

data class CategorySalesRow(
    val categoryName: String,
    val quantity: Long,
    val totalCents: Long,
)

data class ReceiptHistoryRow(
    val receiptId: Long,
    val receiptNumber: String,
    val createdAt: Long,
    val totalCents: Int,
    val itemsCount: Int,
)
