package com.playground.siply.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.playground.siply.currentDayKey
import com.playground.siply.data.AppStateEntity
import com.playground.siply.data.CatalogRow
import com.playground.siply.data.CloudReceiptHistoryItem
import com.playground.siply.data.DashboardSalesItem
import com.playground.siply.data.DailyStatsRow
import com.playground.siply.data.ExportPayload
import com.playground.siply.data.LastReceiptInfo
import com.playground.siply.data.PosRepository
import com.playground.siply.data.ReceiptDraftLine
import com.playground.siply.data.ReceiptHistoryItem
import com.playground.siply.formatCurrency
import com.playground.siply.formatReceiptNumber
import com.playground.siply.formatResetLabel
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryUi(
    val id: Long,
    val name: String,
    val sortOrder: Int,
)

data class ProductUi(
    val id: Long,
    val name: String,
    val priceLabel: String,
    val emoji: String,
    val accentColor: Long,
    val quantityInCart: Int,
)

data class CartLineUi(
    val productId: Long,
    val name: String,
    val emoji: String,
    val unitPriceLabel: String,
    val quantity: Int,
    val lineTotalLabel: String,
    val accentColor: Long,
)

data class DailyStatsUi(
    val totalLabel: String = formatCurrency(0),
    val receiptsCountLabel: String = "0",
    val resetLabel: String = "Od početka dana",
)

data class ReceiptHistoryUi(
    val id: Long,
    val receiptNumber: String,
    val createdAtLabel: String,
    val totalLabel: String,
    val itemsCountLabel: String,
)

data class DashboardSalesUi(
    val name: String,
    val quantityLabel: String,
    val totalLabel: String,
)

data class StaffOverviewUi(
    val waiterTotalLabel: String = formatCurrency(0),
    val waiterReceiptsLabel: String = "0",
    val waiterItemsLabel: String = "0 kom",
    val combinedTotalLabel: String = formatCurrency(0),
    val combinedReceiptsLabel: String = "0",
)

data class ComparisonBarUi(
    val label: String,
    val valueLabel: String,
    val supportingLabel: String,
    val progress: Float,
)

data class WaiterProductUi(
    val name: String,
    val quantityLabel: String,
    val totalLabel: String,
)

data class WaiterAnalyticsUi(
    val name: String,
    val totalLabel: String,
    val receiptsCountLabel: String,
    val itemsCountLabel: String,
    val averageTicketLabel: String,
    val topProducts: List<WaiterProductUi>,
)

data class CloudReceiptUi(
    val id: String,
    val receiptNumber: String,
    val waiterName: String,
    val createdAtLabel: String,
    val totalLabel: String,
    val itemsSummary: String,
)

data class PosUiState(
    val loading: Boolean = true,
    val darkMode: Boolean = false,
    val nextReceiptNumber: String = "00000000-001",
    val categories: List<CategoryUi> = emptyList(),
    val selectedCategoryId: Long? = null,
    val products: List<ProductUi> = emptyList(),
    val cartItems: List<CartLineUi> = emptyList(),
    val subtotalLabel: String = formatCurrency(0),
    val cartItemsCount: Int = 0,
    val dailyStats: DailyStatsUi = DailyStatsUi(),
    val staffOverview: StaffOverviewUi = StaffOverviewUi(),
    val categorySales: List<DashboardSalesUi> = emptyList(),
    val productSales: List<DashboardSalesUi> = emptyList(),
    val staffProductSales: List<DashboardSalesUi> = emptyList(),
    val waiterRevenueComparison: List<ComparisonBarUi> = emptyList(),
    val waiterItemsComparison: List<ComparisonBarUi> = emptyList(),
    val waiterAnalytics: List<WaiterAnalyticsUi> = emptyList(),
    val receiptHistory: List<ReceiptHistoryUi> = emptyList(),
    val cloudReceiptHistory: List<CloudReceiptUi> = emptyList(),
    val cloudCafeName: String = "",
    val cloudUserName: String = "",
    val cloudUserRole: String = "",
    val waiterInvitePayload: String = "",
)

private data class PosCoreInputs(
    val rows: List<CatalogRow>,
    val state: AppStateEntity,
    val stats: DailyStatsRow,
    val selectedId: Long?,
    val cart: Map<Long, Int>,
)

private data class ProductAggregate(
    var quantity: Int = 0,
    var totalCents: Int = 0,
)

private data class WaiterAggregate(
    val name: String,
    var receiptsCount: Int = 0,
    var itemsCount: Int = 0,
    var totalCents: Int = 0,
    val products: LinkedHashMap<String, ProductAggregate> = linkedMapOf(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: PosRepository,
) : ViewModel() {
    private val selectedCategoryId = MutableStateFlow<Long?>(null)
    private val cartQuantities = MutableStateFlow<Map<Long, Int>>(linkedMapOf())
    private val _messages = MutableSharedFlow<String>()

    private val catalogRows = repository.observeCatalogRows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val appState = repository.observeAppState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppStateEntity())

    private val dailyStats = appState
        .flatMapLatest { repository.observeDailyStats(it.statsAnchorEpochMs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyStatsRow(0, 0))

    private val receiptHistory = repository.observeReceiptHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val categorySales = appState
        .flatMapLatest { repository.observeCategorySales(it.statsAnchorEpochMs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val productSales = appState
        .flatMapLatest { repository.observeProductSales(it.statsAnchorEpochMs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val cloudReceiptHistory = repository.observeCloudReceiptHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val messages = _messages.asSharedFlow()

    private val coreInputs = combine(
        catalogRows,
        appState,
        dailyStats,
        selectedCategoryId,
        cartQuantities,
    ) { rows, state, stats, selectedId, cart ->
        PosCoreInputs(rows, state, stats, selectedId, cart)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PosCoreInputs(
            rows = emptyList(),
            state = AppStateEntity(),
            stats = DailyStatsRow(0, 0),
            selectedId = null,
            cart = emptyMap(),
        ),
    )

    val uiState = combine(
        coreInputs,
        receiptHistory,
        categorySales,
        productSales,
        cloudReceiptHistory,
    ) { core, history, categories, products, cloudHistory ->
        buildUiState(
            rows = core.rows,
            state = core.state,
            stats = core.stats,
            history = history,
            categorySales = categories,
            productSales = products,
            cloudHistory = cloudHistory,
            selectedId = core.selectedId,
            cart = core.cart,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PosUiState())

    init {
        viewModelScope.launch {
            repository.prepare()
        }
        viewModelScope.launch {
            repository.observeCloudCatalog()
                .catch {
                    _messages.emit("Cloud katalog trenutno nije dostupan.")
                }
                .collect { products ->
                    runCatching {
                        repository.syncCloudCatalog(products)
                    }.onFailure {
                        _messages.emit("Greška pri sinkronizaciji cloud kataloga.")
                    }
            }
        }
        viewModelScope.launch {
            catalogRows.collect { rows ->
                val categoryIds = rows.map { it.categoryId }.distinct()
                val current = selectedCategoryId.value
                if (categoryIds.isNotEmpty() && (current == null || current !in categoryIds)) {
                    selectedCategoryId.value = categoryIds.first()
                }
            }
        }
    }

    fun selectCategory(categoryId: Long) {
        selectedCategoryId.value = categoryId
    }

    fun addProduct(productId: Long) {
        cartQuantities.update { current ->
            LinkedHashMap(current).apply {
                put(productId, (this[productId] ?: 0) + 1)
            }
        }
    }

    fun adjustQuantity(productId: Long, delta: Int) {
        cartQuantities.update { current ->
            LinkedHashMap(current).apply {
                val nextValue = (this[productId] ?: 0) + delta
                if (nextValue <= 0) {
                    remove(productId)
                } else {
                    put(productId, nextValue)
                }
            }
        }
    }

    fun saveReceipt() {
        viewModelScope.launch {
            val draft = buildDraftLines()
            if (draft.isEmpty()) {
                _messages.emit("Račun je prazan.")
                return@launch
            }

            val result = repository.saveReceipt(draft)
            if (result != null) {
                cartQuantities.value = linkedMapOf()
                val cloudNote = if (result.cloudSynced) " i syncan u cloud." else "."
                _messages.emit("Račun ${result.receiptNumber} je spremljen$cloudNote")
            }
        }
    }

    suspend fun addCatalogProduct(
        categoryId: Long?,
        name: String,
        priceInput: String,
    ): Boolean {
        if (categoryId == null) {
            _messages.emit("Odaberi kategoriju.")
            return false
        }

        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            _messages.emit("Upiši naziv artikla.")
            return false
        }

        val priceCents = parsePriceToCents(priceInput)
        if (priceCents == null || priceCents <= 0) {
            _messages.emit("Upiši ispravnu cijenu, npr. 2,50.")
            return false
        }

        repository.addProduct(
            categoryId = categoryId,
            name = cleanName,
            priceCents = priceCents,
        )
        _messages.emit("Artikl $cleanName je dodan.")
        return true
    }

    suspend fun createOnlineCafe(
        cafeName: String,
        adminName: String,
    ): Boolean {
        if (cafeName.isBlank() || adminName.isBlank()) {
            _messages.emit("Upiši naziv kafića i ime admina.")
            return false
        }

        return runCatching {
            repository.createOnlineCafe(cafeName, adminName)
            _messages.emit("Online kafić je kreiran. QR je spreman za waitere.")
            true
        }.getOrElse {
            _messages.emit(it.message ?: "Kreiranje online kafića nije uspjelo.")
            false
        }
    }

    suspend fun refreshWaiterInvite(): Boolean = runCatching {
        repository.refreshWaiterInvite()
        _messages.emit("Generiran je novi waiter QR.")
        true
    }.getOrElse {
        _messages.emit(it.message ?: "Ne mogu generirati novi waiter QR.")
        false
    }

    suspend fun joinCafeAsWaiter(
        invitePayload: String,
        waiterName: String,
    ): Boolean {
        if (invitePayload.isBlank()) {
            _messages.emit("Skeniraj ili zalijepi QR payload.")
            return false
        }
        if (waiterName.isBlank()) {
            _messages.emit("Upiši ime konobara.")
            return false
        }

        return runCatching {
            repository.joinCafeAsWaiter(invitePayload, waiterName)
            _messages.emit("Konobar je spojen na online kafić.")
            true
        }.getOrElse {
            _messages.emit(it.message ?: "Spajanje waitera nije uspjelo.")
            false
        }
    }

    suspend fun buildExportPayload(): ExportPayload? {
        val payload = repository.buildSalesExport()
        if (payload == null) {
            _messages.emit("Nema prodaje za export.")
        }
        return payload
    }

    suspend fun loadLastReceiptInfo(): LastReceiptInfo? {
        val info = repository.peekLastReceiptInfo()
        if (info == null) {
            _messages.emit("Nema spremljenog računa.")
        }
        return info
    }

    fun deleteLastReceipt() {
        viewModelScope.launch {
            val deleted = repository.deleteLastReceipt()
            if (deleted == null) {
                _messages.emit("Nema spremljenog računa.")
            } else {
                _messages.emit("Obrisan je račun ${deleted.receiptNumber}.")
            }
        }
    }

    fun resetDailyStats() {
        viewModelScope.launch {
            repository.resetDailyStats()
            _messages.emit("Dnevna statistika je resetirana.")
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            repository.setDarkMode(!uiState.value.darkMode)
        }
    }

    fun notifyExportSaved(success: Boolean, filename: String) {
        viewModelScope.launch {
            if (success) {
                _messages.emit("CSV je spremljen kao $filename.")
            } else {
                _messages.emit("Spremanje CSV-a nije uspjelo.")
            }
        }
    }

    private fun buildUiState(
        rows: List<CatalogRow>,
        state: AppStateEntity,
        stats: DailyStatsRow,
        history: List<ReceiptHistoryItem>,
        categorySales: List<DashboardSalesItem>,
        productSales: List<DashboardSalesItem>,
        cloudHistory: List<CloudReceiptHistoryItem>,
        selectedId: Long?,
        cart: Map<Long, Int>,
    ): PosUiState {
        val categories = rows
            .map { CategoryUi(it.categoryId, it.categoryName, it.categorySortOrder) }
            .distinctBy { it.id }
            .sortedBy { it.sortOrder }

        val activeCategoryId = selectedId
            ?.takeIf { chosen -> categories.any { it.id == chosen } }
            ?: categories.firstOrNull()?.id

        val todayKey = currentDayKey()
        val nextSequence = if (state.lastReceiptDayKey == todayKey) {
            state.lastReceiptSequence + 1
        } else {
            1
        }

        val orderMap = rows.withIndex().associate { indexed ->
            indexed.value.productId to indexed.index
        }
        val rowsById = rows.associateBy { it.productId }

        val products = rows
            .filter { it.categoryId == activeCategoryId }
            .map { row ->
                ProductUi(
                    id = row.productId,
                    name = row.productName,
                    priceLabel = formatCurrency(row.priceCents),
                    emoji = row.emoji,
                    accentColor = row.accentColor,
                    quantityInCart = cart[row.productId] ?: 0,
                )
            }

        val cartItems = cart.entries
            .sortedBy { entry -> orderMap[entry.key] ?: Int.MAX_VALUE }
            .mapNotNull { entry ->
                rowsById[entry.key]?.let { row ->
                    CartLineUi(
                        productId = row.productId,
                        name = row.productName,
                        emoji = row.emoji,
                        unitPriceLabel = formatCurrency(row.priceCents),
                        quantity = entry.value,
                        lineTotalLabel = formatCurrency(row.priceCents * entry.value),
                        accentColor = row.accentColor,
                    )
                }
            }

        val subtotal = cart.entries.sumOf { entry ->
            val price = rowsById[entry.key]?.priceCents ?: 0
            price * entry.value
        }

        val waiterAggregates = linkedMapOf<String, WaiterAggregate>()
        val staffProductAggregates = linkedMapOf<String, ProductAggregate>()

        cloudHistory.forEach { receipt ->
            val waiterName = receipt.waiterName.ifBlank { "Nepoznati konobar" }
            val waiterAggregate = waiterAggregates.getOrPut(waiterName) {
                WaiterAggregate(name = waiterName)
            }
            waiterAggregate.receiptsCount += 1
            waiterAggregate.totalCents += receipt.totalCents

            receipt.items.forEach { item ->
                waiterAggregate.itemsCount += item.quantity

                val waiterProduct = waiterAggregate.products.getOrPut(item.name) { ProductAggregate() }
                waiterProduct.quantity += item.quantity
                waiterProduct.totalCents += item.lineTotalCents

                val staffProduct = staffProductAggregates.getOrPut(item.name) { ProductAggregate() }
                staffProduct.quantity += item.quantity
                staffProduct.totalCents += item.lineTotalCents
            }
        }

        val sortedWaiters = waiterAggregates.values.sortedWith(
            compareByDescending<WaiterAggregate> { it.totalCents }
                .thenByDescending { it.itemsCount }
                .thenBy { it.name },
        )

        val staffTotalCents = sortedWaiters.sumOf { it.totalCents }
        val staffReceiptsCount = sortedWaiters.sumOf { it.receiptsCount }
        val staffItemsCount = sortedWaiters.sumOf { it.itemsCount }
        val localTotalCents = stats.totalCents.toInt()
        val localReceiptsCount = stats.receiptsCount.toInt()

        return PosUiState(
            loading = rows.isEmpty(),
            darkMode = state.darkMode,
            nextReceiptNumber = formatReceiptNumber(todayKey, nextSequence),
            categories = categories,
            selectedCategoryId = activeCategoryId,
            products = products,
            cartItems = cartItems,
            subtotalLabel = formatCurrency(subtotal),
            cartItemsCount = cart.values.sum(),
            dailyStats = DailyStatsUi(
                totalLabel = formatCurrency(stats.totalCents.toInt()),
                receiptsCountLabel = stats.receiptsCount.toString(),
                resetLabel = formatResetLabel(state.statsAnchorEpochMs),
            ),
            staffOverview = StaffOverviewUi(
                waiterTotalLabel = formatCurrency(staffTotalCents),
                waiterReceiptsLabel = staffReceiptsCount.toString(),
                waiterItemsLabel = "$staffItemsCount kom",
                combinedTotalLabel = formatCurrency(localTotalCents + staffTotalCents),
                combinedReceiptsLabel = (localReceiptsCount + staffReceiptsCount).toString(),
            ),
            categorySales = categorySales.map { item ->
                DashboardSalesUi(
                    name = item.name,
                    quantityLabel = item.quantityLabel,
                    totalLabel = item.totalLabel,
                )
            },
            productSales = productSales.map { item ->
                DashboardSalesUi(
                    name = item.name,
                    quantityLabel = item.quantityLabel,
                    totalLabel = item.totalLabel,
                )
            },
            staffProductSales = staffProductAggregates.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, ProductAggregate>> { it.value.totalCents }
                        .thenByDescending { it.value.quantity }
                        .thenBy { it.key },
                )
                .take(8)
                .map { entry ->
                    DashboardSalesUi(
                        name = entry.key,
                        quantityLabel = "${entry.value.quantity} kom",
                        totalLabel = formatCurrency(entry.value.totalCents),
                    )
                },
            waiterRevenueComparison = buildComparisonBars(
                waiters = sortedWaiters,
                metric = { aggregate -> aggregate.totalCents },
                valueLabel = { aggregate -> formatCurrency(aggregate.totalCents) },
                supportingLabel = { aggregate -> "${aggregate.receiptsCount} računa" },
            ),
            waiterItemsComparison = buildComparisonBars(
                waiters = sortedWaiters,
                metric = { aggregate -> aggregate.itemsCount },
                valueLabel = { aggregate -> "${aggregate.itemsCount} kom" },
                supportingLabel = { aggregate -> formatCurrency(aggregate.totalCents) },
            ),
            waiterAnalytics = sortedWaiters.map { aggregate ->
                WaiterAnalyticsUi(
                    name = aggregate.name,
                    totalLabel = formatCurrency(aggregate.totalCents),
                    receiptsCountLabel = aggregate.receiptsCount.toString(),
                    itemsCountLabel = "${aggregate.itemsCount} kom",
                    averageTicketLabel = formatCurrency(
                        if (aggregate.receiptsCount == 0) 0 else aggregate.totalCents / aggregate.receiptsCount,
                    ),
                    topProducts = aggregate.products.entries
                        .sortedWith(
                            compareByDescending<Map.Entry<String, ProductAggregate>> { it.value.totalCents }
                                .thenByDescending { it.value.quantity }
                                .thenBy { it.key },
                        )
                        .take(4)
                        .map { productEntry ->
                            WaiterProductUi(
                                name = productEntry.key,
                                quantityLabel = "${productEntry.value.quantity} kom",
                                totalLabel = formatCurrency(productEntry.value.totalCents),
                            )
                        },
                )
            },
            receiptHistory = history.map { item ->
                ReceiptHistoryUi(
                    id = item.id,
                    receiptNumber = item.receiptNumber,
                    createdAtLabel = item.createdAtLabel,
                    totalLabel = item.totalLabel,
                    itemsCountLabel = item.itemsCountLabel,
                )
            },
            cloudReceiptHistory = cloudHistory.map { item ->
                CloudReceiptUi(
                    id = item.id,
                    receiptNumber = item.receiptNumber,
                    waiterName = item.waiterName,
                    createdAtLabel = item.createdAtLabel,
                    totalLabel = item.totalLabel,
                    itemsSummary = item.itemsSummary,
                )
            },
            cloudCafeName = state.cloudCafeName,
            cloudUserName = state.cloudUserName,
            cloudUserRole = state.cloudUserRole,
            waiterInvitePayload = repository.buildInvitePayload(state).orEmpty(),
        )
    }

    private fun buildDraftLines(): List<ReceiptDraftLine> {
        val productsById = catalogRows.value.associateBy { it.productId }
        return cartQuantities.value.entries.mapNotNull { entry ->
            productsById[entry.key]?.let { row ->
                ReceiptDraftLine(
                    productId = row.productId,
                    productName = row.productName,
                    unitPriceCents = row.priceCents,
                    quantity = entry.value,
                )
            }
        }
    }

    private fun parsePriceToCents(input: String): Int? {
        val normalized = input.trim().replace(',', '.')
        if (normalized.isEmpty()) {
            return null
        }

        val value = normalized.toBigDecimalOrNull() ?: return null
        if (value <= BigDecimal.ZERO) {
            return null
        }

        return value
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toInt()
    }

    private fun buildComparisonBars(
        waiters: List<WaiterAggregate>,
        metric: (WaiterAggregate) -> Int,
        valueLabel: (WaiterAggregate) -> String,
        supportingLabel: (WaiterAggregate) -> String,
    ): List<ComparisonBarUi> {
        if (waiters.isEmpty()) {
            return emptyList()
        }

        val maxValue = waiters.maxOf { aggregate -> metric(aggregate) }.coerceAtLeast(1)
        return waiters.map { aggregate ->
            val value = metric(aggregate).coerceAtLeast(0)
            ComparisonBarUi(
                label = aggregate.name,
                valueLabel = valueLabel(aggregate),
                supportingLabel = supportingLabel(aggregate),
                progress = value.toFloat() / maxValue.toFloat(),
            )
        }
    }

    companion object {
        fun factory(repository: PosRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                        return MainViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Nepoznat ViewModel: ${modelClass.name}")
                }
            }
    }
}
