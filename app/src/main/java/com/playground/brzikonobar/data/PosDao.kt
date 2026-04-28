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
        WHERE p.isActive = 1
        ORDER BY c.sortOrder ASC, p.sortOrder ASC
        """
    )
    fun observeCatalogRows(): Flow<List<CatalogRow>>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun countProducts(): Int

    @Query("SELECT COALESCE(MAX(id), 0) FROM products")
    suspend fun getMaxProductId(): Long

    @Query("SELECT COALESCE(MAX(id), 0) FROM categories")
    suspend fun getMaxCategoryId(): Long

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM categories")
    suspend fun getMaxCategorySortOrder(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM products WHERE categoryId = :categoryId")
    suspend fun getMaxProductSortOrder(categoryId: Long): Int

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getCategoryByName(name: String): CategoryEntity?

    @Query("SELECT * FROM products ORDER BY categoryId ASC, sortOrder ASC, id ASC")
    suspend fun getAllProducts(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY categoryId ASC, sortOrder ASC, id ASC")
    suspend fun getActiveProducts(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getProductById(productId: Long): ProductEntity?

    @Query("SELECT * FROM inventory_stock WHERE productId = :productId LIMIT 1")
    suspend fun getInventoryStockByProductId(productId: Long): InventoryStockEntity?

    @Query(
        """
        SELECT * FROM products
        WHERE categoryId = :categoryId AND name = :name COLLATE NOCASE
        LIMIT 1
        """
    )
    suspend fun getProductByCategoryAndName(
        categoryId: Long,
        name: String,
    ): ProductEntity?

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
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryStock(stock: InventoryStockEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryStocks(items: List<InventoryStockEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProcessedCloudReceipts(items: List<ProcessedCloudReceiptEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProcurementEntries(items: List<ProcurementEntryEntity>)

    @Query("SELECT receiptId FROM processed_cloud_receipts WHERE cafeId = :cafeId")
    suspend fun getProcessedCloudReceiptIds(cafeId: String): List<String>

    @Query("DELETE FROM processed_cloud_receipts")
    suspend fun deleteAllProcessedCloudReceipts()

    @Query("DELETE FROM inventory_stock")
    suspend fun deleteAllInventoryStocks()

    @Query("DELETE FROM procurement_entries")
    suspend fun deleteAllProcurementEntries()

    @Query("DELETE FROM price_list_items")
    suspend fun deleteAllPriceListItems()

    @Query("DELETE FROM price_list_versions")
    suspend fun deleteAllPriceListVersions()

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Query(
        """
        SELECT
            p.id AS productId,
            p.categoryId AS categoryId,
            c.name AS categoryName,
            p.name AS productName,
            p.priceCents AS priceCents,
            p.emoji AS emoji,
            COALESCE(s.quantityUnits, 0) AS quantityUnits,
            COALESCE(s.updatedAt, 0) AS updatedAt
        FROM products p
        INNER JOIN categories c ON c.id = p.categoryId
        LEFT JOIN inventory_stock s ON s.productId = p.id
        WHERE p.isActive = 1
        ORDER BY c.sortOrder ASC, p.sortOrder ASC, p.id ASC
        """
    )
    fun observeInventoryRows(): Flow<List<InventoryRow>>

    @Query(
        """
        SELECT
            id,
            productId,
            productName,
            quantityUnits,
            createdAt,
            note
        FROM procurement_entries
        ORDER BY createdAt DESC, id DESC
        """
    )
    fun observeProcurementHistory(): Flow<List<ProcurementHistoryRow>>

    @Query(
        """
        SELECT
            c.name AS categoryName,
            p.name AS productName,
            p.priceCents AS priceCents,
            p.emoji AS emoji,
            p.accentColor AS accentColor,
            p.sortOrder AS sortOrder
        FROM products p
        INNER JOIN categories c ON c.id = p.categoryId
        WHERE p.isActive = 1
        ORDER BY c.sortOrder ASC, p.sortOrder ASC, p.id ASC
        """
    )
    suspend fun getActivePriceListItems(): List<PriceListItemRow>

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
            COALESCE(SUM(receipt_items.quantity), 0) AS itemsCount,
            receipts.note AS note
        FROM receipts
        LEFT JOIN receipt_items ON receipt_items.receiptId = receipts.id
        GROUP BY receipts.id
        ORDER BY receipts.createdAt DESC, receipts.id DESC
        """
    )
    fun observeReceiptHistory(): Flow<List<ReceiptHistoryRow>>

    @Query(
        """
        SELECT
            receipts.id AS receiptId,
            receipts.receiptNumber AS receiptNumber,
            receipts.createdAt AS createdAt,
            receipts.note AS note,
            receipt_items.productName AS productName,
            receipt_items.quantity AS quantity,
            receipt_items.lineTotalCents AS lineTotalCents
        FROM receipts
        INNER JOIN receipt_items ON receipt_items.receiptId = receipts.id
        ORDER BY receipts.createdAt DESC, receipts.id DESC, receipt_items.id ASC
        """
    )
    suspend fun getReceiptExportRows(): List<ReceiptExportRow>

    @Query(
        """
        SELECT
            receipts.createdAt AS createdAt,
            categories.name AS categoryName,
            receipt_items.productName AS productName,
            receipt_items.quantity AS quantity,
            receipt_items.lineTotalCents AS lineTotalCents
        FROM receipt_items
        INNER JOIN receipts ON receipts.id = receipt_items.receiptId
        INNER JOIN products ON products.id = receipt_items.productId
        INNER JOIN categories ON categories.id = products.categoryId
        ORDER BY receipts.createdAt DESC, receipt_items.id DESC
        """
    )
    fun observeReceiptAnalyticsRows(): Flow<List<ReceiptAnalyticsRow>>

    @Query(
        """
        SELECT
            v.id AS versionId,
            v.name AS name,
            v.effectiveDateLabel AS effectiveDateLabel,
            v.createdAt AS createdAt,
            v.isActive AS isActive,
            COUNT(i.id) AS itemsCount
        FROM price_list_versions v
        LEFT JOIN price_list_items i ON i.versionId = v.id
        GROUP BY v.id
        ORDER BY v.isActive DESC, v.createdAt DESC, v.id DESC
        """
    )
    fun observePriceListVersions(): Flow<List<PriceListVersionRow>>

    @Query("SELECT * FROM price_list_versions WHERE id = :versionId LIMIT 1")
    suspend fun getPriceListVersion(versionId: Long): PriceListVersionEntity?

    @Query(
        """
        SELECT
            categoryName,
            productName,
            priceCents,
            emoji,
            accentColor,
            sortOrder
        FROM price_list_items
        WHERE versionId = :versionId
        ORDER BY id ASC
        """
    )
    suspend fun getPriceListItems(versionId: Long): List<PriceListItemRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceListVersion(version: PriceListVersionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceListVersions(items: List<PriceListVersionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceListItems(items: List<PriceListItemEntity>)

    @Query("UPDATE price_list_versions SET isActive = 0")
    suspend fun clearActivePriceListVersions()

    @Query("UPDATE price_list_versions SET isActive = 1 WHERE id = :versionId")
    suspend fun markPriceListVersionActive(versionId: Long)

    @Insert
    suspend fun insertReceipt(receipt: ReceiptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipts(items: List<ReceiptEntity>)

    @Insert
    suspend fun insertReceiptItems(items: List<ReceiptItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceiptItemsRaw(items: List<ReceiptItemEntity>)

    @Query("SELECT * FROM receipt_items WHERE receiptId = :receiptId ORDER BY id ASC")
    suspend fun getReceiptItemsForReceipt(receiptId: Long): List<ReceiptItemEntity>

    @Query("SELECT * FROM receipts ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun getLastReceipt(): ReceiptEntity?

    @Query("DELETE FROM receipt_items WHERE receiptId = :receiptId")
    suspend fun deleteItemsForReceipt(receiptId: Long)

    @Query("DELETE FROM receipt_items")
    suspend fun deleteAllReceiptItems()

    @Query("DELETE FROM receipts")
    suspend fun deleteAllReceipts()

    @Delete
    suspend fun deleteReceipt(receipt: ReceiptEntity)

    @Query("SELECT * FROM inventory_stock ORDER BY productId ASC")
    suspend fun getAllInventoryStocks(): List<InventoryStockEntity>

    @Query("SELECT * FROM processed_cloud_receipts ORDER BY cafeId ASC, receiptId ASC")
    suspend fun getAllProcessedCloudReceipts(): List<ProcessedCloudReceiptEntity>

    @Query("SELECT * FROM procurement_entries ORDER BY createdAt ASC, id ASC")
    suspend fun getAllProcurementEntries(): List<ProcurementEntryEntity>

    @Query("SELECT * FROM price_list_versions ORDER BY createdAt ASC, id ASC")
    suspend fun getAllPriceListVersions(): List<PriceListVersionEntity>

    @Query("SELECT * FROM price_list_items ORDER BY versionId ASC, id ASC")
    suspend fun getAllPriceListItemEntities(): List<PriceListItemEntity>

    @Query("SELECT * FROM receipts ORDER BY createdAt ASC, id ASC")
    suspend fun getAllReceipts(): List<ReceiptEntity>

    @Query("SELECT * FROM receipt_items ORDER BY receiptId ASC, id ASC")
    suspend fun getAllReceiptItems(): List<ReceiptItemEntity>
}
