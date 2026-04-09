package com.playground.siply.data

import android.content.Context
import androidx.room.withTransaction
import com.playground.siply.currentDayKey
import com.playground.siply.exportFileName
import com.playground.siply.formatCsvEuro
import com.playground.siply.formatCurrency
import com.playground.siply.formatDateTime
import com.playground.siply.formatReceiptNumber
import com.playground.siply.receiptSequenceFrom
import com.playground.siply.startOfDayMillis
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

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
    val createdAtLabel: String,
    val totalLabel: String,
    val itemsCountLabel: String,
)

data class DashboardSalesItem(
    val name: String,
    val quantityLabel: String,
    val totalLabel: String,
)

data class CloudReceiptHistoryItem(
    val id: String,
    val receiptNumber: String,
    val waiterName: String,
    val createdAtLabel: String,
    val totalLabel: String,
    val totalCents: Int,
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

@OptIn(ExperimentalCoroutinesApi::class)
class PosRepository(
    private val database: AppDatabase,
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val dao = database.posDao()
    private val zoneId: ZoneId = clock.zone
    private val cloudSyncService = CloudSyncService(context)

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
                createdAtLabel = formatDateTime(row.createdAt, zoneId),
                totalLabel = formatCurrency(row.totalCents),
                itemsCountLabel = "${row.itemsCount} stavki",
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
                rows.map { row ->
                    CloudReceiptHistoryItem(
                        id = row.id,
                        receiptNumber = row.receiptNumber,
                        waiterName = row.waiterName,
                        createdAtLabel = formatDateTime(row.createdAtMillis, zoneId),
                        totalLabel = formatCurrency(row.totalCents),
                        totalCents = row.totalCents,
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
            var nextId = dao.getMaxProductId()

            products.forEach { product ->
                val existing = dao.getProductByCloudRef(product.cafeId, product.id)
                    ?: dao.findUnsyncedProductByShape(
                        categoryId = product.categoryId,
                        name = product.name,
                        priceCents = product.priceCents,
                    )

                val localId = existing?.id ?: run {
                    nextId += 1
                    nextId
                }

                dao.insertProduct(
                    ProductEntity(
                        id = localId,
                        categoryId = product.categoryId,
                        name = product.name,
                        priceCents = product.priceCents,
                        emoji = product.emoji,
                        accentColor = product.accentColor,
                        sortOrder = product.sortOrder,
                        cloudCafeId = product.cafeId,
                        cloudProductId = product.id,
                    ),
                )
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
        var insertedProduct: ProductEntity? = null
        var pendingConfig: CloudConfig? = null
        var pendingSession: CloudSession? = null

        database.withTransaction {
            val state = ensureSeededAndState(now = Instant.now(clock))
            val nextId = dao.getMaxProductId() + 1
            val nextSortOrder = dao.getMaxProductSortOrder(categoryId) + 1
            val style = defaultStyleForCategory(categoryId)

            insertedProduct = ProductEntity(
                id = nextId,
                categoryId = categoryId,
                name = name,
                priceCents = priceCents,
                emoji = style.emoji,
                accentColor = style.accentColor,
                sortOrder = nextSortOrder,
            )
            dao.insertProduct(insertedProduct!!)

            pendingConfig = state.toCloudConfigOrNull()
            pendingSession = state.toCloudSessionOrNull()
                ?.takeIf { session -> session.userRole == "admin" }
        }

        val product = insertedProduct ?: return
        if (pendingConfig == null || pendingSession == null) {
            return
        }

        runCatching {
            val cloudProductId = cloudSyncService.addCatalogProduct(
                config = pendingConfig!!,
                session = pendingSession!!,
                product = CloudCatalogProductDraft(
                    categoryId = product.categoryId,
                    name = product.name,
                    priceCents = product.priceCents,
                    emoji = product.emoji,
                    accentColor = product.accentColor,
                    sortOrder = product.sortOrder,
                ),
            )

            database.withTransaction {
                val local = dao.getProductById(product.id) ?: return@withTransaction
                dao.insertProduct(
                    local.copy(
                        cloudCafeId = pendingSession!!.cafeId,
                        cloudProductId = cloudProductId,
                    ),
                )
            }
        }
    }

    suspend fun saveReceipt(lines: List<ReceiptDraftLine>): SaveReceiptResult? {
        if (lines.isEmpty()) {
            return null
        }

        var pendingConfig: CloudConfig? = null
        var pendingSession: CloudSession? = null
        var saveResult: SaveReceiptResult? = null

        database.withTransaction {
            val now = Instant.now(clock)
            val state = ensureSeededAndState(now)
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
                    createdAt = now.toEpochMilli(),
                    businessDayKey = dayKey,
                    totalCents = lines.sumOf { it.lineTotalCents },
                )
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
                }
            )

            dao.upsertAppState(
                state.copy(
                    lastReceiptDayKey = dayKey,
                    lastReceiptSequence = nextSequence,
                )
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
                )
            )
        }

        syncExistingCustomProductsToCloud(config, session)
    }

    suspend fun refreshWaiterInvite(): String {
        val state = dao.getAppState() ?: error("Cloud nije konfiguriran.")
        val config = state.toCloudConfigOrNull() ?: error("Cloud nije konfiguriran.")
        val session = state.toCloudSessionOrNull() ?: error("Cloud nije konfiguriran.")
        if (session.userRole != "admin") {
            error("Samo admin može generirati QR za waitere.")
        }
        val inviteCode = cloudSyncService.refreshInvite(config, session)

        database.withTransaction {
            dao.upsertAppState(state.copy(cloudInviteCode = inviteCode))
        }

        return inviteCode
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
                )
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
                )
            )

            deletedInfo
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
                )
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

    private suspend fun syncExistingCustomProductsToCloud(
        config: CloudConfig,
        session: CloudSession,
    ) {
        if (session.userRole != "admin") {
            return
        }

        val seedIds = SeedCatalog.products.mapTo(linkedSetOf()) { it.id }
        val candidates = dao.getAllProducts().filter { product ->
            product.cloudProductId.isBlank() && product.id !in seedIds
        }

        candidates.forEach { product ->
            runCatching {
                val cloudProductId = cloudSyncService.addCatalogProduct(
                    config = config,
                    session = session,
                    product = CloudCatalogProductDraft(
                        categoryId = product.categoryId,
                        name = product.name,
                        priceCents = product.priceCents,
                        emoji = product.emoji,
                        accentColor = product.accentColor,
                        sortOrder = product.sortOrder,
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

    private fun escapeCsv(value: String): String {
        if (!value.contains(';') && !value.contains('"')) {
            return value
        }
        return "\"${value.replace("\"", "\"\"")}\""
    }

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
