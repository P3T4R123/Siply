package com.playground.siply.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PosDao {
    @Query(
        """
        SELECT
            p.id AS productId,
            p.name AS productName,
            p.priceCents AS priceCents,
            p.emoji AS emoji,
            p.accentColor AS accentColor,
            p.sortOrder AS productSortOrder,
            c.id AS categoryId,
            c.name AS categoryName,
            c.sortOrder AS categorySortOrder
        FROM products p
        INNER JOIN categories c ON c.id = p.categoryId
        ORDER BY c.sortOrder ASC, p.sortOrder ASC
        """
    )
    fun observeCatalogRows(): Flow<List<CatalogRow>>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun countProducts(): Int

    @Query("SELECT COALESCE(MAX(id), 0) FROM products")
    suspend fun getMaxProductId(): Long

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM products WHERE categoryId = :categoryId")
    suspend fun getMaxProductSortOrder(categoryId: Long): Int

    @Query("SELECT * FROM products ORDER BY categoryId ASC, sortOrder ASC, id ASC")
    suspend fun getAllProducts(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getProductById(productId: Long): ProductEntity?

    @Query(
        """
        SELECT * FROM products
        WHERE cloudCafeId = :cloudCafeId AND cloudProductId = :cloudProductId
        LIMIT 1
        """
    )
    suspend fun getProductByCloudRef(
        cloudCafeId: String,
        cloudProductId: String,
    ): ProductEntity?

    @Query(
        """
        SELECT * FROM products
        WHERE categoryId = :categoryId
            AND name = :name
            AND priceCents = :priceCents
            AND cloudCafeId = ''
            AND cloudProductId = ''
        LIMIT 1
        """
    )
    suspend fun findUnsyncedProductByShape(
        categoryId: Long,
        name: String,
        priceCents: Int,
    ): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Query("SELECT * FROM app_state WHERE id = 1")
    fun observeAppState(): Flow<AppStateEntity?>

    @Query("SELECT * FROM app_state WHERE id = 1")
    suspend fun getAppState(): AppStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppState(state: AppStateEntity)

    @Query(
        """
        SELECT
            COUNT(*) AS receiptsCount,
            COALESCE(SUM(totalCents), 0) AS totalCents
        FROM receipts
        WHERE createdAt >= :anchorMs
        """
    )
    fun observeDailyStats(anchorMs: Long): Flow<DailyStatsRow>

    @Query(
        """
        SELECT
            receipt_items.productName AS productName,
            SUM(receipt_items.quantity) AS quantity,
            SUM(receipt_items.lineTotalCents) AS totalCents
        FROM receipt_items
        INNER JOIN receipts ON receipts.id = receipt_items.receiptId
        WHERE receipts.createdAt >= :anchorMs
        GROUP BY receipt_items.productName
        ORDER BY totalCents DESC, receipt_items.productName ASC
        """
    )
    suspend fun getSalesSummary(anchorMs: Long): List<SalesSummaryRow>

    @Query(
        """
        SELECT
            receipt_items.productName AS productName,
            SUM(receipt_items.quantity) AS quantity,
            SUM(receipt_items.lineTotalCents) AS totalCents
        FROM receipt_items
        INNER JOIN receipts ON receipts.id = receipt_items.receiptId
        WHERE receipts.createdAt >= :anchorMs
        GROUP BY receipt_items.productName
        ORDER BY totalCents DESC, receipt_items.productName ASC
        """
    )
    fun observeProductSales(anchorMs: Long): Flow<List<SalesSummaryRow>>

    @Query(
        """
        SELECT
            categories.name AS categoryName,
            SUM(receipt_items.quantity) AS quantity,
            SUM(receipt_items.lineTotalCents) AS totalCents
        FROM receipt_items
        INNER JOIN receipts ON receipts.id = receipt_items.receiptId
        INNER JOIN products ON products.id = receipt_items.productId
        INNER JOIN categories ON categories.id = products.categoryId
        WHERE receipts.createdAt >= :anchorMs
        GROUP BY categories.name
        ORDER BY totalCents DESC, categories.name ASC
        """
    )
    fun observeCategorySales(anchorMs: Long): Flow<List<CategorySalesRow>>

    @Query(
        """
        SELECT
            receipts.id AS receiptId,
            receipts.receiptNumber AS receiptNumber,
            receipts.createdAt AS createdAt,
            receipts.totalCents AS totalCents,
            COALESCE(SUM(receipt_items.quantity), 0) AS itemsCount
        FROM receipts
        LEFT JOIN receipt_items ON receipt_items.receiptId = receipts.id
        GROUP BY receipts.id
        ORDER BY receipts.createdAt DESC, receipts.id DESC
        """
    )
    fun observeReceiptHistory(): Flow<List<ReceiptHistoryRow>>

    @Insert
    suspend fun insertReceipt(receipt: ReceiptEntity): Long

    @Insert
    suspend fun insertReceiptItems(items: List<ReceiptItemEntity>)

    @Query("SELECT * FROM receipts ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun getLastReceipt(): ReceiptEntity?

    @Query("DELETE FROM receipt_items WHERE receiptId = :receiptId")
    suspend fun deleteItemsForReceipt(receiptId: Long)

    @Delete
    suspend fun deleteReceipt(receipt: ReceiptEntity)
}
