package com.playground.siply.data

import android.content.Context
import androidx.room.withTransaction
import com.playground.siply.currentDayKey
import com.playground.siply.exportFileName
import com.playground.siply.formatCsvEuro
import com.playground.siply.formatCurrency
import com.playground.siply.formatDateTime
import com.playground.siply.formatReceiptNumber
import com.playground.siply.priceListFileName
import com.playground.siply.receiptSequenceFrom
import com.playground.siply.startOfDayMillis
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class ReceiptDraftLine(
    val productId: Long,
    val productName: String,
    val unitPriceCents: Int,
    val quantity: Int,
) {
    val lineTotalCents: Int = unitPriceCents * quantity
}

data class ExportPayload(
    val filename: String,
    val content: String,
)

data class LastReceiptInfo(
    val receiptNumber: String,
    val totalLabel: String,
    val createdAtLabel: String,
)

data class ReceiptHistoryItem(
    val id: Long,
    val receiptNumber: String,
    val createdAtMillis: Long,
    val createdAtLabel: String,
    val totalCents: Int,
    val totalLabel: String,
    val itemsCountLabel: String,
    val note: String,
)

data class DashboardSalesItem(
    val name: String,
    val quantityLabel: String,
    val totalLabel: String,
)

data class LocalSalesLineItem(
    val createdAtMillis: Long,
    val categoryName: String,
    val productName: String,
    val quantity: Int,
    val lineTotalCents: Int,
)

data class PriceListVersionItem(
    val id: Long,
    val name: String,
    val effectiveDateLabel: String,
    val createdAtLabel: String,
    val itemsCountLabel: String,
    val isActive: Boolean,
)

data class InventoryItem(
    val productId: Long,
    val categoryId: Long,
    val categoryName: String,
    val productName: String,
    val priceCents: Int,
    val emoji: String,
    val quantityUnits: Int,
    val updatedAtMillis: Long,
)

data class ProcurementHistoryItem(
    val id: Long,
    val productId: Long,
    val productName: String,
    val quantityUnits: Int,
    val createdAtMillis: Long,
    val note: String,
)

data class CloudReceiptHistoryItem(
    val id: String,
    val receiptNumber: String,
    val waiterName: String,
    val createdAtMillis: Long,
    val createdAtLabel: String,
    val totalLabel: String,
    val totalCents: Int,
    val note: String,
    val items: List<CloudReceiptHistoryLineItem>,
    val itemsSummary: String,
)

data class CloudReceiptHistoryLineItem(
    val productId: Long?,
    val name: String,
    val quantity: Int,
    val lineTotalCents: Int,
)

data class SaveReceiptResult(
    val receiptNumber: String,
    val cloudSynced: Boolean,
)

private data class ImportedPriceListItem(
    val categoryName: String,
    val productName: String,
    val priceCents: Int,
    val emoji: String,
    val accentColor: Long,
    val sortOrder: Int,
)

private data class CompleteSalesExportLine(
    val createdAtMillis: Long,
    val waiterName: String,
    val receiptNumber: String,
    val note: String,
    val productName: String,
    val quantity: Int,
    val lineTotalCents: Int,
    val source: String,
)

private data class BackupSnapshot(
    val appState: AppStateEntity,
    val categories: List<CategoryEntity>,
    val products: List<ProductEntity>,
    val inventoryStocks: List<InventoryStockEntity>,
    val processedCloudReceipts: List<ProcessedCloudReceiptEntity>,
    val procurementEntries: List<ProcurementEntryEntity>,
    val priceListVersions: List<PriceListVersionEntity>,
    val priceListItems: List<PriceListItemEntity>,
    val receipts: List<ReceiptEntity>,
    val receiptItems: List<ReceiptItemEntity>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PosRepository(
    private val database: AppDatabase,
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val dao = database.posDao()
    private val zoneId: ZoneId = clock.zone
    private val cloudSyncService = CloudSyncService(context)
    private val priceListLabelFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale("hr", "HR"))

    fun observeCatalogRows(): Flow<List<CatalogRow>> = dao.observeCatalogRows()

    fun observeAppState(): Flow<AppStateEntity> = dao.observeAppState().map { state ->
        state ?: defaultState(Instant.now(clock))
    }

    fun observeDailyStats(anchorMs: Long): Flow<DailyStatsRow> = dao.observeDailyStats(anchorMs)

    fun observeCategorySales(anchorMs: Long): Flow<List<DashboardSalesItem>> =
        dao.observeCategorySales(anchorMs).map { rows ->
            rows.map { row ->
                DashboardSalesItem(
                    name = row.categoryName,
                    quantityLabel = "${row.quantity} kom",
                    totalLabel = formatCurrency(row.totalCents.toInt()),
                )
            }
        }

    fun observeProductSales(anchorMs: Long): Flow<List<DashboardSalesItem>> =
        dao.observeProductSales(anchorMs).map { rows ->
            rows.map { row ->
                DashboardSalesItem(
                    name = row.productName,
                    quantityLabel = "${row.quantity} kom",
                    totalLabel = formatCurrency(row.totalCents.toInt()),
                )
            }
        }

    fun observeReceiptHistory(): Flow<List<ReceiptHistoryItem>> = dao.observeReceiptHistory().map { rows ->
        rows.map { row ->
            ReceiptHistoryItem(
                id = row.receiptId,
                receiptNumber = row.receiptNumber,
                createdAtMillis = row.createdAt,
                createdAtLabel = formatDateTime(row.createdAt, zoneId),
                totalCents = row.totalCents,
                totalLabel = formatCurrency(row.totalCents),
                itemsCountLabel = "${row.itemsCount} stavki",
                note = row.note,
            )
        }
    }

    fun observeLocalSalesLines(): Flow<List<LocalSalesLineItem>> =
        dao.observeReceiptAnalyticsRows().map { rows ->
            rows.map { row ->
                LocalSalesLineItem(
                    createdAtMillis = row.createdAt,
                    categoryName = row.categoryName,
                    productName = row.productName,
                    quantity = row.quantity,
                    lineTotalCents = row.lineTotalCents,
                )
            }
        }

    fun observePriceListVersions(): Flow<List<PriceListVersionItem>> =
        dao.observePriceListVersions().map { rows ->
            rows.map { row ->
                PriceListVersionItem(
                    id = row.versionId,
                    name = row.name,
                    effectiveDateLabel = row.effectiveDateLabel,
                    createdAtLabel = formatDateTime(row.createdAt, zoneId),
                    itemsCountLabel = "${row.itemsCount} artikala",
                    isActive = row.isActive,
                )
            }
        }

    fun observeInventoryItems(): Flow<List<InventoryItem>> =
        dao.observeInventoryRows().map { rows ->
            rows.map { row ->
                InventoryItem(
                    productId = row.productId,
                    categoryId = row.categoryId,
                    categoryName = row.categoryName,
                    productName = row.productName,
                    priceCents = row.priceCents,
                    emoji = row.emoji,
                    quantityUnits = row.quantityUnits,
                    updatedAtMillis = row.updatedAt,
                )
            }
        }

    fun observeProcurementHistory(): Flow<List<ProcurementHistoryItem>> =
        dao.observeProcurementHistory().map { rows ->
            rows.map { row ->
                ProcurementHistoryItem(
                    id = row.id,
                    productId = row.productId,
                    productName = row.productName,
                    quantityUnits = row.quantityUnits,
                    createdAtMillis = row.createdAt,
                    note = row.note,
                )
            }
        }

    fun observeCloudReceiptHistory(): Flow<List<CloudReceiptHistoryItem>> =
        observeAppState().flatMapLatest { state ->
            val config = state.toCloudConfigOrNull() ?: return@flatMapLatest flowOf(emptyList())
            val session = state.toCloudSessionOrNull() ?: return@flatMapLatest flowOf(emptyList())
            if (session.userRole != "admin") {
                return@flatMapLatest flowOf(emptyList())
            }

            cloudSyncService.observeReceipts(config, session).map { rows ->
                val inventoryChanged = applyWaiterReceiptsToInventory(
                    cafeId = session.cafeId,
                    receipts = rows,
                )
                if (inventoryChanged) {
                    runCatching {
                        syncCatalogToCloud(config, session)
                    }
                }
                rows.map { row ->
                    CloudReceiptHistoryItem(
                        id = row.id,
                        receiptNumber = row.receiptNumber,
                        waiterName = row.waiterName,
                        createdAtMillis = row.createdAtMillis,
                        createdAtLabel = formatDateTime(row.createdAtMillis, zoneId),
                        totalLabel = formatCurrency(row.totalCents),
                        totalCents = row.totalCents,
                        note = row.note,
                        items = row.items.map { item ->
                            CloudReceiptHistoryLineItem(
                                productId = item.productId,
                                name = item.name,
                                quantity = item.quantity,
                                lineTotalCents = item.lineTotalCents,
                            )
                        },
                        itemsSummary = row.itemsSummary,
                    )
                }
            }
        }

    fun observeCloudCatalog(): Flow<List<CloudCatalogProduct>> =
        observeAppState().flatMapLatest { state ->
            val config = state.toCloudConfigOrNull() ?: return@flatMapLatest flowOf(emptyList())
            val session = state.toCloudSessionOrNull() ?: return@flatMapLatest flowOf(emptyList())
            cloudSyncService.observeCatalogProducts(config, session)
        }

    suspend fun syncCloudCatalog(products: List<CloudCatalogProduct>) {
        if (products.isEmpty()) {
            return
        }

        database.withTransaction {
            ensureSeededAndState(now = Instant.now(clock))

            val categoriesByKey = dao.getAllCategories()
                .associateByTo(linkedMapOf()) { category -> normalizeName(category.name) }
            var nextCategoryId = dao.getMaxCategoryId()
            var nextCategorySort = dao.getMaxCategorySortOrder()
            var nextProductId = dao.getMaxProductId()

            products.forEach { product ->
                val categoryKey = normalizeName(product.categoryName)
                val category = categoriesByKey[categoryKey] ?: run {
                    nextCategoryId += 1
                    nextCategorySort += 1
                    val created = CategoryEntity(
                        id = nextCategoryId,
                        name = product.categoryName,
                        sortOrder = nextCategorySort,
                    )
                    dao.insertCategory(created)
                    categoriesByKey[categoryKey] = created
                    created
                }

                val existing = dao.getProductByCloudRef(product.cafeId, product.id)
                    ?: dao.getProductByCategoryAndName(category.id, product.name)

                val localId = existing?.id ?: run {
                    nextProductId += 1
                    nextProductId
                }
                val style = defaultStyleForCategory(category.id)

                dao.insertProduct(
                    ProductEntity(
                        id = localId,
                        categoryId = category.id,
                        name = product.name,
                        priceCents = product.priceCents,
                        emoji = product.emoji.ifBlank { existing?.emoji ?: style.emoji },
                        accentColor = product.accentColor.takeIf { it != 0L }
                            ?: existing?.accentColor
                            ?: style.accentColor,
                        sortOrder = product.sortOrder.takeIf { it > 0 } ?: existing?.sortOrder ?: 1,
                        isActive = product.isActive,
                        cloudCafeId = product.cafeId,
                        cloudProductId = product.id,
                    ),
                )
                val currentStock = dao.getInventoryStockByProductId(localId)
                if (currentStock == null || product.stockUpdatedAtMillis >= currentStock.updatedAt) {
                    dao.insertInventoryStock(
                        InventoryStockEntity(
                            productId = localId,
                            quantityUnits = product.stockQuantityUnits,
                            updatedAt = product.stockUpdatedAtMillis,
                        ),
                    )
                }
            }
        }
    }

    suspend fun prepare() {
        database.withTransaction {
            ensureSeededAndState(now = Instant.now(clock))
        }
    }

    suspend fun addProduct(
        categoryId: Long,
        name: String,
        priceCents: Int,
    ) {
        var pendingConfig: CloudConfig? = null
        var pendingSession: CloudSession? = null

        database.withTransaction {
            val state = ensureSeededAndState(now = Instant.now(clock))
            val nextId = dao.getMaxProductId() + 1
            val nextSortOrder = dao.getMaxProductSortOrder(categoryId) + 1
            val style = defaultStyleForCategory(categoryId)

            dao.insertProduct(
                ProductEntity(
                    id = nextId,
                    categoryId = categoryId,
                    name = name,
                    priceCents = priceCents,
                    emoji = style.emoji,
                    accentColor = style.accentColor,
                    sortOrder = nextSortOrder,
                    isActive = true,
                ),
            )
            ensureInventoryRecord(
                productId = nextId,
                updatedAtMillis = Instant.now(clock).toEpochMilli(),
            )

            pendingConfig = state.toCloudConfigOrNull()
            pendingSession = state.toCloudSessionOrNull()?.takeIf { session ->
                session.userRole == "admin"
            }
        }

        if (pendingConfig != null && pendingSession != null) {
            syncCatalogToCloud(pendingConfig!!, pendingSession!!)
        }
    }

    suspend fun saveReceipt(
        lines: List<ReceiptDraftLine>,
        note: String = "",
    ): SaveReceiptResult? {
        if (lines.isEmpty()) {
            return null
        }
        val cleanNote = note.trim()

        var pendingConfig: CloudConfig? = null
        var pendingSession: CloudSession? = null
        var saveResult: SaveReceiptResult? = null

        database.withTransaction {
            val now = Instant.now(clock)
            val state = ensureSeededAndState(now)
            val nowMillis = now.toEpochMilli()
            val dayKey = currentDayKey(now, zoneId)
            val nextSequence = if (state.lastReceiptDayKey == dayKey) {
                state.lastReceiptSequence + 1
            } else {
                1
            }
            val receiptNumber = formatReceiptNumber(dayKey, nextSequence)
            val receiptId = dao.insertReceipt(
                ReceiptEntity(
                    receiptNumber = receiptNumber,
                    createdAt = nowMillis,
                    businessDayKey = dayKey,
                    totalCents = lines.sumOf { it.lineTotalCents },
                    note = cleanNote,
                ),
            )

            dao.insertReceiptItems(
                lines.map { line ->
                    ReceiptItemEntity(
                        receiptId = receiptId,
                        productId = line.productId,
                        productName = line.productName,
                        unitPriceCents = line.unitPriceCents,
                        quantity = line.quantity,
                        lineTotalCents = line.lineTotalCents,
                    )
                },
            )

            dao.upsertAppState(
                state.copy(
                    lastReceiptDayKey = dayKey,
                    lastReceiptSequence = nextSequence,
                ),
            )

            adjustInventoryForDraftLines(
                lines = lines,
                multiplier = -1,
                changedAtMillis = nowMillis,
            )

            pendingConfig = state.toCloudConfigOrNull()
            pendingSession = state.toCloudSessionOrNull()
            saveResult = SaveReceiptResult(
                receiptNumber = receiptNumber,
                cloudSynced = false,
            )
        }

        val result = saveResult ?: return null
        val cloudSynced = runCatching {
            if (pendingConfig != null && pendingSession != null) {
                cloudSyncService.pushReceipt(
                    config = pendingConfig!!,
                    session = pendingSession!!,
                    receiptNumber = result.receiptNumber,
                    totalCents = lines.sumOf { it.lineTotalCents },
                    note = cleanNote,
                    lines = lines.map { line ->
                        CloudReceiptLine(
                            productId = line.productId,
                            name = line.productName,
                            quantity = line.quantity,
                            lineTotalCents = line.lineTotalCents,
                        )
                    },
                )
                true
            } else {
                false
            }
        }.getOrDefault(false)

        if (cloudSynced && pendingConfig != null && pendingSession?.userRole == "admin") {
            runCatching {
                syncCatalogToCloud(pendingConfig!!, pendingSession!!)
            }
        }

        return result.copy(cloudSynced = cloudSynced)
    }

    suspend fun createOnlineCafe(
        cafeName: String,
        adminName: String,
    ) {
        val config = cloudSyncService.defaultConfig()
        val session = cloudSyncService.createAdminCafe(
            config = config,
            cafeName = cafeName.trim(),
            adminName = adminName.trim(),
        )

        database.withTransaction {
            val state = ensureSeededAndState(Instant.now(clock))
            dao.upsertAppState(
                state.copy(
                    cloudApiKey = config.apiKey,
                    cloudAppId = config.appId,
                    cloudProjectId = config.projectId,
                    cloudCafeId = session.cafeId,
                    cloudCafeName = session.cafeName,
                    cloudUserId = session.userId,
                    cloudUserName = session.userName,
                    cloudUserRole = session.userRole,
                    cloudInviteCode = session.inviteCode,
                ),
            )
        }

        syncCatalogToCloud(config, session)
    }

    suspend fun refreshWaiterInvite(): String {
        val state = dao.getAppState() ?: error("Cloud nije konfiguriran.")
        val config = state.toCloudConfigOrNull() ?: error("Cloud nije konfiguriran.")
        val session = state.toCloudSessionOrNull() ?: error("Cloud nije konfiguriran.")
        if (session.userRole != "admin") {
            error("Samo admin može generirati QR za waitere.")
        }
        val inviteCode = cloudSyncService.refreshInvite(config, session, role = "waiter")

        database.withTransaction {
            dao.upsertAppState(state.copy(cloudInviteCode = inviteCode))
        }

        return inviteCode
    }

    suspend fun refreshWebAdminInvitePayload(): String {
        val state = dao.getAppState() ?: error("Cloud nije konfiguriran.")
        val config = state.toCloudConfigOrNull() ?: error("Cloud nije konfiguriran.")
        val session = state.toCloudSessionOrNull() ?: error("Cloud nije konfiguriran.")
        if (session.userRole != "admin") {
            error("Samo admin može generirati web admin kod.")
        }
        val inviteCode = cloudSyncService.refreshInvite(config, session, role = "admin")
        return cloudSyncService.buildInvitePayload(
            config = config,
            cafeId = session.cafeId,
            inviteCode = inviteCode,
            role = "admin",
        )
    }

    suspend fun joinCafeAsWaiter(
        rawInvitePayload: String,
        waiterName: String,
    ) {
        val payload = cloudSyncService.parseInvitePayload(rawInvitePayload)
        val session = cloudSyncService.joinCafeAsWaiter(
            payload = payload,
            waiterName = waiterName.trim(),
        )
        val config = CloudConfig(
            apiKey = payload.apiKey,
            appId = payload.appId,
            projectId = payload.projectId,
        )

        database.withTransaction {
            val state = ensureSeededAndState(Instant.now(clock))
            dao.upsertAppState(
                state.copy(
                    cloudApiKey = config.apiKey,
                    cloudAppId = config.appId,
                    cloudProjectId = config.projectId,
                    cloudCafeId = session.cafeId,
                    cloudCafeName = session.cafeName,
                    cloudUserId = session.userId,
                    cloudUserName = session.userName,
                    cloudUserRole = session.userRole,
                    cloudInviteCode = session.inviteCode,
                ),
            )
        }
    }

    fun buildInvitePayload(state: AppStateEntity): String? {
        val config = state.toCloudConfigOrNull() ?: cloudSyncService.defaultConfigOrNull() ?: return null
        if (state.cloudCafeId.isBlank() || state.cloudInviteCode.isBlank()) {
            return null
        }
        return cloudSyncService.buildInvitePayload(
            config = config,
            cafeId = state.cloudCafeId,
            inviteCode = state.cloudInviteCode,
            role = "waiter",
        )
    }

    suspend fun buildSalesExport(): ExportPayload? {
        prepare()
        val state = dao.getAppState() ?: defaultState(Instant.now(clock))
        val summary = dao.getSalesSummary(state.statsAnchorEpochMs)
        if (summary.isEmpty()) {
            return null
        }

        val rows = summary.joinToString(separator = "\n") { item ->
            "${escapeCsv(item.productName)};${item.quantity};${formatCsvEuro(item.totalCents.toInt())}"
        }
        val grandTotal = summary.sumOf { it.totalCents.toInt() }
        val content = buildString {
            appendLine("Piće;Količina;Ukupno €")
            append(rows)
            appendLine()
            append("UKUPNO;;${formatCsvEuro(grandTotal)}")
        }

        return ExportPayload(
            filename = exportFileName(Instant.now(clock), zoneId),
            content = content,
        )
    }

    suspend fun buildCompleteSalesExport(
        cloudReceipts: List<CloudReceiptHistoryItem>,
    ): ExportPayload? {
        prepare()
        val state = dao.getAppState() ?: defaultState(Instant.now(clock))
        val localWaiterName = when {
            state.cloudUserRole == "admin" && state.cloudUserName.isNotBlank() -> state.cloudUserName
            else -> "Admin lokalno"
        }

        val localRows = dao.getReceiptExportRows().map { row ->
            CompleteSalesExportLine(
                createdAtMillis = row.createdAt,
                waiterName = localWaiterName,
                receiptNumber = row.receiptNumber,
                note = row.note,
                productName = row.productName,
                quantity = row.quantity,
                lineTotalCents = row.lineTotalCents,
                source = "Lokalno",
            )
        }
        val cloudRows = cloudReceipts.flatMap { receipt ->
            receipt.items.map { item ->
                CompleteSalesExportLine(
                    createdAtMillis = receipt.createdAtMillis,
                    waiterName = receipt.waiterName.ifBlank { "Nepoznati konobar" },
                    receiptNumber = receipt.receiptNumber,
                    note = receipt.note,
                    productName = item.name,
                    quantity = item.quantity,
                    lineTotalCents = item.lineTotalCents,
                    source = "Cloud",
                )
            }
        }
        val rows = (localRows + cloudRows)
            .sortedByDescending { it.createdAtMillis }

        if (rows.isEmpty()) {
            return null
        }

        val content = buildString {
            appendLine("Datum;Dan;Konobar;Račun;Bilješka;Artikl;Količina;Ukupno €;Izvor")
            rows.forEach { row ->
                appendLine(
                    listOf(
                        escapeCsv(formatDateTime(row.createdAtMillis, zoneId)),
                        currentDayKey(Instant.ofEpochMilli(row.createdAtMillis), zoneId),
                        escapeCsv(row.waiterName),
                        escapeCsv(row.receiptNumber),
                        escapeCsv(row.note),
                        escapeCsv(row.productName),
                        row.quantity.toString(),
                        formatCsvEuro(row.lineTotalCents),
                        row.source,
                    ).joinToString(";"),
                )
            }
            appendLine()
            append("UKUPNO;;;;;;${rows.sumOf { it.quantity }};${formatCsvEuro(rows.sumOf { it.lineTotalCents })};")
        }

        return ExportPayload(
            filename = "prodaja_sve_${Instant.now(clock).atZone(zoneId).toLocalDate()}.csv",
            content = content,
        )
    }

    suspend fun buildPriceListExport(): ExportPayload? {
        prepare()
        val rows = dao.getActivePriceListItems()
        if (rows.isEmpty()) {
            return null
        }

        val content = buildString {
            appendLine("Kategorija;Artikl;Cijena EUR;Emoji;Boja HEX;Redoslijed")
            rows.forEach { row ->
                appendLine(
                    listOf(
                        escapeCsv(row.categoryName),
                        escapeCsv(row.productName),
                        formatCsvEuro(row.priceCents),
                        escapeCsv(row.emoji),
                        formatColorHex(row.accentColor),
                        row.sortOrder.toString(),
                    ).joinToString(";"),
                )
            }
        }

        return ExportPayload(
            filename = priceListFileName(Instant.now(clock), zoneId),
            content = content,
        )
    }

    suspend fun buildDatabaseBackup(): ExportPayload {
        prepare()
        val now = Instant.now(clock)
        val snapshot = database.withTransaction {
            ensureSeededAndState(now)
            BackupSnapshot(
                appState = dao.getAppState() ?: defaultState(now),
                categories = dao.getAllCategories(),
                products = dao.getAllProducts(),
                inventoryStocks = dao.getAllInventoryStocks(),
                processedCloudReceipts = dao.getAllProcessedCloudReceipts(),
                procurementEntries = dao.getAllProcurementEntries(),
                priceListVersions = dao.getAllPriceListVersions(),
                priceListItems = dao.getAllPriceListItemEntities(),
                receipts = dao.getAllReceipts(),
                receiptItems = dao.getAllReceiptItems(),
            )
        }

        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("createdAt", now.toEpochMilli())
            put("appState", snapshot.appState.toJson())
            put("categories", snapshot.categories.toJsonArray { entity -> entity.toJson() })
            put("products", snapshot.products.toJsonArray { entity -> entity.toJson() })
            put("inventoryStocks", snapshot.inventoryStocks.toJsonArray { entity -> entity.toJson() })
            put(
                "processedCloudReceipts",
                snapshot.processedCloudReceipts.toJsonArray { entity -> entity.toJson() },
            )
            put("procurementEntries", snapshot.procurementEntries.toJsonArray { entity -> entity.toJson() })
            put("priceListVersions", snapshot.priceListVersions.toJsonArray { entity -> entity.toJson() })
            put("priceListItems", snapshot.priceListItems.toJsonArray { entity -> entity.toJson() })
            put("receipts", snapshot.receipts.toJsonArray { entity -> entity.toJson() })
            put("receiptItems", snapshot.receiptItems.toJsonArray { entity -> entity.toJson() })
        }

        return ExportPayload(
            filename = "siply_backup_${now.atZone(zoneId).toLocalDate()}.json",
            content = json.toString(2),
        )
    }

    suspend fun restoreDatabaseFromBackup(content: String) {
        val snapshot = parseBackupSnapshot(content)

        database.withTransaction {
            dao.deleteAllReceiptItems()
            dao.deleteAllReceipts()
            dao.deleteAllProcessedCloudReceipts()
            dao.deleteAllProcurementEntries()
            dao.deleteAllInventoryStocks()
            dao.deleteAllPriceListItems()
            dao.deleteAllPriceListVersions()
            dao.deleteAllProducts()
            dao.deleteAllCategories()

            if (snapshot.categories.isNotEmpty()) {
                dao.insertCategories(snapshot.categories)
            }
            if (snapshot.products.isNotEmpty()) {
                dao.insertProducts(snapshot.products)
            }
            if (snapshot.inventoryStocks.isNotEmpty()) {
                dao.insertInventoryStocks(snapshot.inventoryStocks)
            }
            if (snapshot.processedCloudReceipts.isNotEmpty()) {
                dao.insertProcessedCloudReceipts(snapshot.processedCloudReceipts)
            }
            if (snapshot.procurementEntries.isNotEmpty()) {
                dao.insertProcurementEntries(snapshot.procurementEntries)
            }
            if (snapshot.priceListVersions.isNotEmpty()) {
                dao.insertPriceListVersions(snapshot.priceListVersions)
            }
            if (snapshot.priceListItems.isNotEmpty()) {
                dao.insertPriceListItems(snapshot.priceListItems)
            }
            if (snapshot.receipts.isNotEmpty()) {
                dao.insertReceipts(snapshot.receipts)
            }
            if (snapshot.receiptItems.isNotEmpty()) {
                dao.insertReceiptItemsRaw(snapshot.receiptItems)
            }
            dao.upsertAppState(snapshot.appState)
        }

        val restoredConfig = snapshot.appState.toCloudConfigOrNull()
        val restoredSession = snapshot.appState.toCloudSessionOrNull()?.takeIf { session ->
            session.userRole == "admin"
        }
        if (restoredConfig != null && restoredSession != null) {
            syncCatalogToCloud(restoredConfig, restoredSession)
        }
    }

    suspend fun setInventoryQuantity(
        productId: Long,
        quantityUnits: Int,
    ) {
        require(quantityUnits >= 0) { "Količina ne može biti negativna." }
        var pendingConfig: CloudConfig? = null
        var pendingSession: CloudSession? = null

        database.withTransaction {
            val state = ensureSeededAndState(Instant.now(clock))
            dao.insertInventoryStock(
                InventoryStockEntity(
                    productId = productId,
                    quantityUnits = quantityUnits,
                    updatedAt = Instant.now(clock).toEpochMilli(),
                ),
            )
            pendingConfig = state.toCloudConfigOrNull()
            pendingSession = state.toCloudSessionOrNull()?.takeIf { session ->
                session.userRole == "admin"
            }
        }

        if (pendingConfig != null && pendingSession != null) {
            syncCatalogToCloud(pendingConfig!!, pendingSession!!)
        }
    }

    suspend fun setInventoryQuantities(
        updates: Map<Long, Int>,
    ) {
        if (updates.isEmpty()) {
            return
        }

        require(updates.values.none { quantity -> quantity < 0 }) {
            "Količina ne može biti negativna."
        }

        var pendingConfig: CloudConfig? = null
        var pendingSession: CloudSession? = null

        database.withTransaction {
            val state = ensureSeededAndState(Instant.now(clock))
            val updatedAtMillis = Instant.now(clock).toEpochMilli()
            updates.forEach { (productId, quantityUnits) ->
                dao.insertInventoryStock(
                    InventoryStockEntity(
                        productId = productId,
                        quantityUnits = quantityUnits,
                        updatedAt = updatedAtMillis,
                    ),
                )
            }
            pendingConfig = state.toCloudConfigOrNull()
            pendingSession = state.toCloudSessionOrNull()?.takeIf { session ->
                session.userRole == "admin"
            }
        }

        if (pendingConfig != null && pendingSession != null) {
            syncCatalogToCloud(pendingConfig!!, pendingSession!!)
        }
    }

    suspend fun addProcurementEntries(
        updates: Map<Long, Int>,
        note: String,
    ) {
        val normalizedUpdates = updates
            .mapValues { entry -> entry.value.coerceAtLeast(0) }
            .filterValues { quantity -> quantity > 0 }
        if (normalizedUpdates.isEmpty()) {
            return
        }

        var pendingConfig: CloudConfig? = null
        var pendingSession: CloudSession? = null

        database.withTransaction {
            val now = Instant.now(clock)
            val state = ensureSeededAndState(now)
            val nowMillis = now.toEpochMilli()
            val productsById = dao.getAllProducts().associateBy { product -> product.id }

            dao.insertProcurementEntries(
                normalizedUpdates.mapNotNull { (productId, quantityUnits) ->
                    val product = productsById[productId] ?: return@mapNotNull null
                    adjustInventoryQuantity(
                        productId = productId,
                        deltaUnits = quantityUnits,
                        changedAtMillis = nowMillis,
                    )
                    ProcurementEntryEntity(
                        productId = productId,
                        productName = product.name,
                        quantityUnits = quantityUnits,
                        createdAt = nowMillis,
                        note = note.trim(),
                    )
                },
            )

            pendingConfig = state.toCloudConfigOrNull()
            pendingSession = state.toCloudSessionOrNull()?.takeIf { session ->
                session.userRole == "admin"
            }
        }

        if (pendingConfig != null && pendingSession != null) {
            syncCatalogToCloud(pendingConfig!!, pendingSession!!)
        }
    }

    suspend fun saveCurrentPriceListVersion(label: String) {
        database.withTransaction {
            val now = Instant.now(clock)
            ensureSeededAndState(now)
            val activeItems = dao.getActivePriceListItems()
            if (activeItems.isEmpty()) {
                error("Nema aktivnog cjenika za spremanje.")
            }
            savePriceListVersion(
                label = sanitizePriceListLabel(label, now),
                items = activeItems,
                now = now,
                isActive = true,
            )
        }
    }

    suspend fun importPriceList(
        csvContent: String,
        label: String,
    ) {
        val importedItems = normalizeImportedItems(parsePriceListCsv(csvContent))
        if (importedItems.isEmpty()) {
            error("CSV nema valjanih artikala.")
        }

        var pendingConfig: CloudConfig? = null
        var pendingSession: CloudSession? = null

        database.withTransaction {
            val now = Instant.now(clock)
            val state = ensureSeededAndState(now)

            savePriceListVersion(
                label = sanitizePriceListLabel(label, now),
                items = importedItems,
                now = now,
                isActive = true,
            )
            applyPriceListItems(importedItems)

            pendingConfig = state.toCloudConfigOrNull()
            pendingSession = state.toCloudSessionOrNull()?.takeIf { session ->
                session.userRole == "admin"
            }
        }

        if (pendingConfig != null && pendingSession != null) {
            syncCatalogToCloud(pendingConfig!!, pendingSession!!)
        }
    }

    suspend fun activatePriceListVersion(versionId: Long) {
        var pendingConfig: CloudConfig? = null
        var pendingSession: CloudSession? = null

        database.withTransaction {
            val now = Instant.now(clock)
            val state = ensureSeededAndState(now)
            val version = dao.getPriceListVersion(versionId) ?: error("Cjenik nije pronađen.")
            val items = dao.getPriceListItems(version.id)
            if (items.isEmpty()) {
                error("Odabrani cjenik nema artikala.")
            }

            applyPriceListItems(items)
            dao.clearActivePriceListVersions()
            dao.markPriceListVersionActive(versionId)

            pendingConfig = state.toCloudConfigOrNull()
            pendingSession = state.toCloudSessionOrNull()?.takeIf { session ->
                session.userRole == "admin"
            }
        }

        if (pendingConfig != null && pendingSession != null) {
            syncCatalogToCloud(pendingConfig!!, pendingSession!!)
        }
    }

    suspend fun peekLastReceiptInfo(): LastReceiptInfo? {
        prepare()
        return dao.getLastReceipt()?.toInfo()
    }

    suspend fun deleteLastReceipt(): LastReceiptInfo? {
        return database.withTransaction {
            val state = ensureSeededAndState(now = Instant.now(clock))
            val receipt = dao.getLastReceipt() ?: return@withTransaction null
            val deletedInfo = receipt.toInfo()
            val deletedSequence = receiptSequenceFrom(receipt.receiptNumber)
            val receiptItems = dao.getReceiptItemsForReceipt(receipt.id)

            if (state.cloudUserRole != "waiter" && receiptItems.isNotEmpty()) {
                adjustInventoryForReceiptItems(
                    items = receiptItems,
                    multiplier = 1,
                    changedAtMillis = Instant.now(clock).toEpochMilli(),
                )
            }

            dao.deleteItemsForReceipt(receipt.id)
            dao.deleteReceipt(receipt)

            val nextSequence = if (state.lastReceiptDayKey == receipt.businessDayKey) {
                (deletedSequence - 1).coerceAtLeast(0)
            } else {
                state.lastReceiptSequence
            }

            dao.upsertAppState(
                state.copy(
                    lastReceiptDayKey = if (nextSequence == 0) "" else state.lastReceiptDayKey,
                    lastReceiptSequence = nextSequence,
                ),
            )

            deletedInfo
        }
    }

    suspend fun clearAllSalesData() {
        prepare()
        val state = dao.getAppState() ?: defaultState(Instant.now(clock))
        val config = state.toCloudConfigOrNull()
        val session = state.toCloudSessionOrNull()

        if (config != null && session != null && session.userRole == "admin") {
            cloudSyncService.deleteAllReceipts(config, session)
        }

        database.withTransaction {
            val now = Instant.now(clock)
            val currentState = ensureSeededAndState(now)
            dao.deleteAllReceiptItems()
            dao.deleteAllReceipts()
            dao.deleteAllProcessedCloudReceipts()
            dao.deleteAllInventoryStocks()
            dao.upsertAppState(
                currentState.copy(
                    statsAnchorEpochMs = startOfDayMillis(now, zoneId),
                    currentBusinessDayKey = currentDayKey(now, zoneId),
                    lastReceiptDayKey = "",
                    lastReceiptSequence = 0,
                ),
            )
        }
    }

    suspend fun resetDailyStats() {
        database.withTransaction {
            val now = Instant.now(clock)
            val state = ensureSeededAndState(now)
            dao.upsertAppState(
                state.copy(
                    statsAnchorEpochMs = now.toEpochMilli(),
                    currentBusinessDayKey = currentDayKey(now, zoneId),
                ),
            )
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        database.withTransaction {
            val now = Instant.now(clock)
            val state = ensureSeededAndState(now)
            dao.upsertAppState(state.copy(darkMode = enabled))
        }
    }

    private suspend fun syncCatalogToCloud(
        config: CloudConfig,
        session: CloudSession,
    ) {
        if (session.userRole != "admin") {
            return
        }

        val categories = dao.getAllCategories().associateBy { it.id }
        val products = dao.getAllProducts()
        val inventoryByProductId = dao.getAllInventoryStocks().associateBy { stock -> stock.productId }

        products.forEach { product ->
            val categoryName = categories[product.categoryId]?.name ?: return@forEach
            val stock = inventoryByProductId[product.id]
            runCatching {
                val cloudProductId = cloudSyncService.upsertCatalogProduct(
                    config = config,
                    session = session,
                    cloudProductId = product.cloudProductId.takeIf { it.isNotBlank() },
                    product = CloudCatalogProductDraft(
                        categoryId = product.categoryId,
                        categoryName = categoryName,
                        name = product.name,
                        priceCents = product.priceCents,
                        emoji = product.emoji,
                        accentColor = product.accentColor,
                        sortOrder = product.sortOrder,
                        isActive = product.isActive,
                        stockQuantityUnits = stock?.quantityUnits ?: 0,
                        stockUpdatedAtMillis = stock?.updatedAt ?: 0L,
                    ),
                )

                database.withTransaction {
                    val current = dao.getProductById(product.id) ?: return@withTransaction
                    dao.insertProduct(
                        current.copy(
                            cloudCafeId = session.cafeId,
                            cloudProductId = cloudProductId,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun savePriceListVersion(
        label: String,
        items: List<PriceListItemRow>,
        now: Instant,
        isActive: Boolean,
    ): Long {
        if (isActive) {
            dao.clearActivePriceListVersions()
        }

        val versionId = dao.insertPriceListVersion(
            PriceListVersionEntity(
                name = label,
                effectiveDateLabel = label,
                createdAt = now.toEpochMilli(),
                isActive = isActive,
            ),
        )

        dao.insertPriceListItems(
            items.map { item ->
                PriceListItemEntity(
                    versionId = versionId,
                    categoryName = item.categoryName,
                    productName = item.productName,
                    priceCents = item.priceCents,
                    emoji = item.emoji,
                    accentColor = item.accentColor,
                    sortOrder = item.sortOrder,
                )
            },
        )

        return versionId
    }

    private suspend fun applyPriceListItems(items: List<PriceListItemRow>) {
        val categoriesByKey = dao.getAllCategories()
            .associateByTo(linkedMapOf()) { category -> normalizeName(category.name) }
        var nextCategoryId = dao.getMaxCategoryId()
        var nextCategorySort = dao.getMaxCategorySortOrder()
        var nextProductId = dao.getMaxProductId()
        val nextSortByCategory = linkedMapOf<Long, Int>()
        val activeProductIds = linkedSetOf<Long>()

        items.forEach { item ->
            val categoryName = item.categoryName.trim()
            val productName = item.productName.trim()
            if (categoryName.isEmpty() || productName.isEmpty()) {
                return@forEach
            }

            val categoryKey = normalizeName(categoryName)
            val category = categoriesByKey[categoryKey] ?: run {
                nextCategoryId += 1
                nextCategorySort += 1
                val created = CategoryEntity(
                    id = nextCategoryId,
                    name = categoryName,
                    sortOrder = nextCategorySort,
                )
                dao.insertCategory(created)
                categoriesByKey[categoryKey] = created
                created
            }

            val existing = dao.getProductByCategoryAndName(category.id, productName)
            val localId = existing?.id ?: run {
                nextProductId += 1
                nextProductId
            }

            val sortOrder = item.sortOrder.takeIf { it > 0 } ?: run {
                val nextSort = (nextSortByCategory[category.id] ?: dao.getMaxProductSortOrder(category.id)) + 1
                nextSortByCategory[category.id] = nextSort
                nextSort
            }
            val style = defaultStyleForCategory(category.id)

            dao.insertProduct(
                ProductEntity(
                    id = localId,
                    categoryId = category.id,
                    name = productName,
                    priceCents = item.priceCents,
                    emoji = item.emoji.ifBlank { existing?.emoji ?: style.emoji },
                    accentColor = item.accentColor.takeIf { it != 0L }
                        ?: existing?.accentColor
                        ?: style.accentColor,
                    sortOrder = sortOrder,
                    isActive = true,
                    cloudCafeId = existing?.cloudCafeId.orEmpty(),
                    cloudProductId = existing?.cloudProductId.orEmpty(),
                ),
            )
            ensureInventoryRecord(
                productId = localId,
                updatedAtMillis = Instant.now(clock).toEpochMilli(),
            )
            activeProductIds += localId
        }

        dao.getAllProducts().forEach { product ->
            if (product.id !in activeProductIds && product.isActive) {
                dao.insertProduct(product.copy(isActive = false))
            }
        }
    }

    private suspend fun ensureInventoryRecord(
        productId: Long,
        updatedAtMillis: Long,
    ) {
        if (dao.getInventoryStockByProductId(productId) == null) {
            dao.insertInventoryStock(
                InventoryStockEntity(
                    productId = productId,
                    quantityUnits = 0,
                    updatedAt = updatedAtMillis,
                ),
            )
        }
    }

    private suspend fun adjustInventoryForDraftLines(
        lines: List<ReceiptDraftLine>,
        multiplier: Int,
        changedAtMillis: Long,
    ) {
        lines.forEach { line ->
            adjustInventoryQuantity(
                productId = line.productId,
                deltaUnits = line.quantity * multiplier,
                changedAtMillis = changedAtMillis,
            )
        }
    }

    private suspend fun adjustInventoryForReceiptItems(
        items: List<ReceiptItemEntity>,
        multiplier: Int,
        changedAtMillis: Long,
    ) {
        items.forEach { item ->
            adjustInventoryQuantity(
                productId = item.productId,
                deltaUnits = item.quantity * multiplier,
                changedAtMillis = changedAtMillis,
            )
        }
    }

    private suspend fun adjustInventoryQuantity(
        productId: Long,
        deltaUnits: Int,
        changedAtMillis: Long,
    ) {
        val current = dao.getInventoryStockByProductId(productId)
        val nextQuantity = (current?.quantityUnits ?: 0) + deltaUnits
        dao.insertInventoryStock(
            InventoryStockEntity(
                productId = productId,
                quantityUnits = nextQuantity,
                updatedAt = changedAtMillis,
            ),
        )
    }

    private suspend fun applyWaiterReceiptsToInventory(
        cafeId: String,
        receipts: List<CloudReceiptItem>,
    ): Boolean {
        if (cafeId.isBlank() || receipts.isEmpty()) {
            return false
        }

        return database.withTransaction {
            val processedIds = dao.getProcessedCloudReceiptIds(cafeId).toHashSet()
            val nowMillis = Instant.now(clock).toEpochMilli()
            val newProcessed = mutableListOf<ProcessedCloudReceiptEntity>()

            receipts
                .sortedBy { receipt -> receipt.createdAtMillis }
                .forEach { receipt ->
                    if (!processedIds.add(receipt.id)) {
                        return@forEach
                    }

                    receipt.items.forEach itemLoop@{ item ->
                        val productId = item.productId ?: return@itemLoop
                        adjustInventoryQuantity(
                            productId = productId,
                            deltaUnits = -item.quantity,
                            changedAtMillis = nowMillis,
                        )
                    }

                    newProcessed += ProcessedCloudReceiptEntity(
                        cafeId = cafeId,
                        receiptId = receipt.id,
                        appliedAt = nowMillis,
                    )
                }

            if (newProcessed.isNotEmpty()) {
                dao.insertProcessedCloudReceipts(newProcessed)
            }

            newProcessed.isNotEmpty()
        }
    }

    private suspend fun ensureSeededAndState(now: Instant): AppStateEntity {
        if (dao.countProducts() == 0) {
            dao.insertCategories(SeedCatalog.categories)
            dao.insertProducts(SeedCatalog.products)
        }

        val existing = dao.getAppState()
        val todayKey = currentDayKey(now, zoneId)
        val defaultState = defaultState(now)

        return when {
            existing == null -> {
                dao.upsertAppState(defaultState)
                defaultState
            }

            existing.currentBusinessDayKey != todayKey -> {
                val refreshed = existing.copy(
                    statsAnchorEpochMs = startOfDayMillis(now, zoneId),
                    currentBusinessDayKey = todayKey,
                )
                dao.upsertAppState(refreshed)
                refreshed
            }

            else -> existing
        }
    }

    private fun defaultState(now: Instant): AppStateEntity = AppStateEntity(
        statsAnchorEpochMs = startOfDayMillis(now, zoneId),
        currentBusinessDayKey = currentDayKey(now, zoneId),
        lastReceiptDayKey = "",
        lastReceiptSequence = 0,
        darkMode = false,
    )

    private fun ReceiptEntity.toInfo(): LastReceiptInfo = LastReceiptInfo(
        receiptNumber = receiptNumber,
        totalLabel = formatCurrency(totalCents),
        createdAtLabel = formatDateTime(createdAt, zoneId),
    )

    private fun AppStateEntity.toCloudConfigOrNull(): CloudConfig? {
        if (cloudApiKey.isBlank() || cloudAppId.isBlank() || cloudProjectId.isBlank()) {
            return null
        }
        return CloudConfig(
            apiKey = cloudApiKey,
            appId = cloudAppId,
            projectId = cloudProjectId,
        )
    }

    private fun AppStateEntity.toCloudSessionOrNull(): CloudSession? {
        if (cloudCafeId.isBlank() || cloudUserId.isBlank() || cloudUserName.isBlank() || cloudUserRole.isBlank()) {
            return null
        }
        return CloudSession(
            cafeId = cloudCafeId,
            cafeName = cloudCafeName,
            userId = cloudUserId,
            userName = cloudUserName,
            userRole = cloudUserRole,
            inviteCode = cloudInviteCode,
        )
    }

    private fun parseBackupSnapshot(content: String): BackupSnapshot {
        val root = runCatching { JSONObject(content) }
            .getOrElse { error("Backup datoteka nije valjan JSON.") }

        return BackupSnapshot(
            appState = root.optJSONObject("appState")?.toAppStateEntity() ?: defaultState(Instant.now(clock)),
            categories = root.optJSONArray("categories").toEntityList { json -> json.toCategoryEntity() },
            products = root.optJSONArray("products").toEntityList { json -> json.toProductEntity() },
            inventoryStocks = root.optJSONArray("inventoryStocks").toEntityList { json -> json.toInventoryStockEntity() },
            processedCloudReceipts = root.optJSONArray("processedCloudReceipts").toEntityList { json ->
                json.toProcessedCloudReceiptEntity()
            },
            procurementEntries = root.optJSONArray("procurementEntries").toEntityList { json ->
                json.toProcurementEntryEntity()
            },
            priceListVersions = root.optJSONArray("priceListVersions").toEntityList { json ->
                json.toPriceListVersionEntity()
            },
            priceListItems = root.optJSONArray("priceListItems").toEntityList { json ->
                json.toPriceListItemEntity()
            },
            receipts = root.optJSONArray("receipts").toEntityList { json -> json.toReceiptEntity() },
            receiptItems = root.optJSONArray("receiptItems").toEntityList { json -> json.toReceiptItemEntity() },
        )
    }

    private fun AppStateEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("statsAnchorEpochMs", statsAnchorEpochMs)
        .put("currentBusinessDayKey", currentBusinessDayKey)
        .put("lastReceiptDayKey", lastReceiptDayKey)
        .put("lastReceiptSequence", lastReceiptSequence)
        .put("darkMode", darkMode)
        .put("cloudApiKey", cloudApiKey)
        .put("cloudAppId", cloudAppId)
        .put("cloudProjectId", cloudProjectId)
        .put("cloudCafeId", cloudCafeId)
        .put("cloudCafeName", cloudCafeName)
        .put("cloudUserId", cloudUserId)
        .put("cloudUserName", cloudUserName)
        .put("cloudUserRole", cloudUserRole)
        .put("cloudInviteCode", cloudInviteCode)

    private fun CategoryEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("sortOrder", sortOrder)

    private fun ProductEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("categoryId", categoryId)
        .put("name", name)
        .put("priceCents", priceCents)
        .put("emoji", emoji)
        .put("accentColor", accentColor)
        .put("sortOrder", sortOrder)
        .put("isActive", isActive)
        .put("cloudCafeId", cloudCafeId)
        .put("cloudProductId", cloudProductId)

    private fun InventoryStockEntity.toJson(): JSONObject = JSONObject()
        .put("productId", productId)
        .put("quantityUnits", quantityUnits)
        .put("updatedAt", updatedAt)

    private fun ProcessedCloudReceiptEntity.toJson(): JSONObject = JSONObject()
        .put("cafeId", cafeId)
        .put("receiptId", receiptId)
        .put("appliedAt", appliedAt)

    private fun ProcurementEntryEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("productId", productId)
        .put("productName", productName)
        .put("quantityUnits", quantityUnits)
        .put("createdAt", createdAt)
        .put("note", note)

    private fun PriceListVersionEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("effectiveDateLabel", effectiveDateLabel)
        .put("createdAt", createdAt)
        .put("isActive", isActive)

    private fun PriceListItemEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("versionId", versionId)
        .put("categoryName", categoryName)
        .put("productName", productName)
        .put("priceCents", priceCents)
        .put("emoji", emoji)
        .put("accentColor", accentColor)
        .put("sortOrder", sortOrder)

    private fun ReceiptEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("receiptNumber", receiptNumber)
        .put("createdAt", createdAt)
        .put("businessDayKey", businessDayKey)
        .put("totalCents", totalCents)
        .put("note", note)

    private fun ReceiptItemEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("receiptId", receiptId)
        .put("productId", productId)
        .put("productName", productName)
        .put("unitPriceCents", unitPriceCents)
        .put("quantity", quantity)
        .put("lineTotalCents", lineTotalCents)

    private fun JSONObject.toAppStateEntity(): AppStateEntity = AppStateEntity(
        id = optInt("id", 1),
        statsAnchorEpochMs = optLong("statsAnchorEpochMs", 0L),
        currentBusinessDayKey = optString("currentBusinessDayKey"),
        lastReceiptDayKey = optString("lastReceiptDayKey"),
        lastReceiptSequence = optInt("lastReceiptSequence", 0),
        darkMode = optBoolean("darkMode", false),
        cloudApiKey = optString("cloudApiKey"),
        cloudAppId = optString("cloudAppId"),
        cloudProjectId = optString("cloudProjectId"),
        cloudCafeId = optString("cloudCafeId"),
        cloudCafeName = optString("cloudCafeName"),
        cloudUserId = optString("cloudUserId"),
        cloudUserName = optString("cloudUserName"),
        cloudUserRole = optString("cloudUserRole"),
        cloudInviteCode = optString("cloudInviteCode"),
    )

    private fun JSONObject.toCategoryEntity(): CategoryEntity = CategoryEntity(
        id = optLong("id", 0L),
        name = optString("name"),
        sortOrder = optInt("sortOrder", 0),
    )

    private fun JSONObject.toProductEntity(): ProductEntity = ProductEntity(
        id = optLong("id", 0L),
        categoryId = optLong("categoryId", 0L),
        name = optString("name"),
        priceCents = optInt("priceCents", 0),
        emoji = optString("emoji"),
        accentColor = optLong("accentColor", 0L),
        sortOrder = optInt("sortOrder", 0),
        isActive = optBoolean("isActive", true),
        cloudCafeId = optString("cloudCafeId"),
        cloudProductId = optString("cloudProductId"),
    )

    private fun JSONObject.toInventoryStockEntity(): InventoryStockEntity = InventoryStockEntity(
        productId = optLong("productId", 0L),
        quantityUnits = optInt("quantityUnits", 0),
        updatedAt = optLong("updatedAt", 0L),
    )

    private fun JSONObject.toProcessedCloudReceiptEntity(): ProcessedCloudReceiptEntity =
        ProcessedCloudReceiptEntity(
            cafeId = optString("cafeId"),
            receiptId = optString("receiptId"),
            appliedAt = optLong("appliedAt", 0L),
        )

    private fun JSONObject.toProcurementEntryEntity(): ProcurementEntryEntity = ProcurementEntryEntity(
        id = optLong("id", 0L),
        productId = optLong("productId", 0L),
        productName = optString("productName"),
        quantityUnits = optInt("quantityUnits", 0),
        createdAt = optLong("createdAt", 0L),
        note = optString("note"),
    )

    private fun JSONObject.toPriceListVersionEntity(): PriceListVersionEntity = PriceListVersionEntity(
        id = optLong("id", 0L),
        name = optString("name"),
        effectiveDateLabel = optString("effectiveDateLabel"),
        createdAt = optLong("createdAt", 0L),
        isActive = optBoolean("isActive", false),
    )

    private fun JSONObject.toPriceListItemEntity(): PriceListItemEntity = PriceListItemEntity(
        id = optLong("id", 0L),
        versionId = optLong("versionId", 0L),
        categoryName = optString("categoryName"),
        productName = optString("productName"),
        priceCents = optInt("priceCents", 0),
        emoji = optString("emoji"),
        accentColor = optLong("accentColor", 0L),
        sortOrder = optInt("sortOrder", 0),
    )

    private fun JSONObject.toReceiptEntity(): ReceiptEntity = ReceiptEntity(
        id = optLong("id", 0L),
        receiptNumber = optString("receiptNumber"),
        createdAt = optLong("createdAt", 0L),
        businessDayKey = optString("businessDayKey"),
        totalCents = optInt("totalCents", 0),
        note = optString("note"),
    )

    private fun JSONObject.toReceiptItemEntity(): ReceiptItemEntity = ReceiptItemEntity(
        id = optLong("id", 0L),
        receiptId = optLong("receiptId", 0L),
        productId = optLong("productId", 0L),
        productName = optString("productName"),
        unitPriceCents = optInt("unitPriceCents", 0),
        quantity = optInt("quantity", 0),
        lineTotalCents = optInt("lineTotalCents", 0),
    )

    private inline fun <T> List<T>.toJsonArray(transform: (T) -> JSONObject): JSONArray = JSONArray().apply {
        forEach { item -> put(transform(item)) }
    }

    private inline fun <T> JSONArray?.toEntityList(transform: (JSONObject) -> T): List<T> {
        if (this == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until length()) {
                val value = optJSONObject(index) ?: continue
                add(transform(value))
            }
        }
    }

    private fun parsePriceListCsv(content: String): List<ImportedPriceListItem> {
        val rows = content
            .lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotEmpty() }
            .toList()

        return rows.mapIndexedNotNull { index, line ->
            val columns = parseCsvColumns(line)
            if (columns.isEmpty()) {
                return@mapIndexedNotNull null
            }
            if (index == 0 && isPriceListHeader(columns)) {
                return@mapIndexedNotNull null
            }

            val categoryName = columns.getOrNull(0).orEmpty().trim()
            val productName = columns.getOrNull(1).orEmpty().trim()
            val priceCents = parsePriceToCents(columns.getOrNull(2).orEmpty())
                ?: error("Ne mogu pročitati cijenu za artikl $productName.")

            if (categoryName.isEmpty() || productName.isEmpty()) {
                return@mapIndexedNotNull null
            }

            ImportedPriceListItem(
                categoryName = categoryName,
                productName = productName,
                priceCents = priceCents,
                emoji = columns.getOrNull(3).orEmpty().trim(),
                accentColor = parseColor(columns.getOrNull(4).orEmpty()),
                sortOrder = columns.getOrNull(5)?.trim()?.toIntOrNull() ?: (index + 1),
            )
        }
    }

    private fun normalizeImportedItems(items: List<ImportedPriceListItem>): List<PriceListItemRow> {
        val deduplicated = linkedMapOf<String, PriceListItemRow>()

        items.forEach { item ->
            val key = "${normalizeName(item.categoryName)}|${normalizeName(item.productName)}"
            if (deduplicated.containsKey(key)) {
                deduplicated.remove(key)
            }
            deduplicated[key] = PriceListItemRow(
                categoryName = item.categoryName,
                productName = item.productName,
                priceCents = item.priceCents,
                emoji = item.emoji,
                accentColor = item.accentColor,
                sortOrder = item.sortOrder,
            )
        }

        return deduplicated.values.toList()
    }

    private fun parseCsvColumns(line: String): List<String> {
        val columns = mutableListOf<String>()
        val current = StringBuilder()
        var insideQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && insideQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }

                char == '"' -> insideQuotes = !insideQuotes
                char == ';' && !insideQuotes -> {
                    columns += current.toString()
                    current.clear()
                }

                else -> current.append(char)
            }
            index += 1
        }

        columns += current.toString()
        return columns
    }

    private fun isPriceListHeader(columns: List<String>): Boolean {
        val first = normalizeName(columns.firstOrNull().orEmpty())
        val second = normalizeName(columns.getOrNull(1).orEmpty())
        return first in setOf("kategorija", "category") && second in setOf(
            "artikl",
            "artikl naziv",
            "artikal",
            "product",
        )
    }

    private fun parsePriceToCents(input: String): Int? {
        val normalized = input
            .trim()
            .replace("€", "")
            .replace("eur", "", ignoreCase = true)
            .replace(',', '.')
        if (normalized.isEmpty()) {
            return null
        }

        val value = normalized.toBigDecimalOrNull() ?: return null
        if (value < BigDecimal.ZERO) {
            return null
        }

        return value
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toInt()
    }

    private fun parseColor(input: String): Long {
        val value = input.trim()
        if (value.isEmpty()) {
            return 0L
        }

        return when {
            value.startsWith("#") -> value.removePrefix("#").toLongOrNull(16) ?: 0L
            else -> value.toLongOrNull() ?: 0L
        }
    }

    private fun sanitizePriceListLabel(
        label: String,
        now: Instant,
    ): String {
        val trimmed = label.trim()
        if (trimmed.isNotEmpty()) {
            return trimmed
        }
        return now.atZone(zoneId).toLocalDate().format(priceListLabelFormatter)
    }

    private fun normalizeName(value: String): String = value.trim().lowercase()

    private fun escapeCsv(value: String): String {
        if (!value.contains(';') && !value.contains('"') && !value.contains('\n')) {
            return value
        }
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun formatColorHex(value: Long): String =
        "#${value.toString(16).uppercase().padStart(8, '0')}"

    private fun defaultStyleForCategory(categoryId: Long): ProductStyle = when (categoryId) {
        1L -> ProductStyle("☕", 0xFF8C5E3C)
        2L -> ProductStyle("🍺", 0xFFF2A900)
        3L -> ProductStyle("🥤", 0xFF3A86FF)
        4L -> ProductStyle("🍸", 0xFF14B8A6)
        else -> ProductStyle("🥤", 0xFF6B7280)
    }

    private data class ProductStyle(
        val emoji: String,
        val accentColor: Long,
    )
}
