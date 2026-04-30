package com.playground.siply.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.playground.siply.currentDayKey
import com.playground.siply.data.AppStateEntity
import com.playground.siply.data.CatalogRow
import com.playground.siply.data.CloudReceiptHistoryItem
import com.playground.siply.data.DashboardSalesItem
import com.playground.siply.data.ExportPayload
import com.playground.siply.data.InventoryItem
import com.playground.siply.data.LastReceiptInfo
import com.playground.siply.data.LocalSalesLineItem
import com.playground.siply.data.PosRepository
import com.playground.siply.data.PriceListVersionItem
import com.playground.siply.data.ProcurementHistoryItem
import com.playground.siply.data.ReceiptDraftLine
import com.playground.siply.data.ReceiptHistoryItem
import com.playground.siply.formatCurrency
import com.playground.siply.formatDateTime
import com.playground.siply.formatReceiptNumber
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
    val imageDataUrl: String,
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
    val createdAtMillis: Long,
    val createdAtLabel: String,
    val totalCents: Int,
    val totalLabel: String,
    val itemsCountLabel: String,
    val note: String,
)

data class DashboardSalesUi(
    val name: String,
    val quantityLabel: String,
    val totalLabel: String,
)

data class PriceListVersionUi(
    val id: Long,
    val name: String,
    val effectiveDateLabel: String,
    val createdAtLabel: String,
    val itemsCountLabel: String,
    val isActive: Boolean,
)

data class InventoryUi(
    val productId: Long,
    val categoryId: Long,
    val name: String,
    val emoji: String,
    val unitPriceLabel: String,
    val quantityUnits: Int,
    val quantityLabel: String,
    val stockValueLabel: String,
    val updatedAtLabel: String,
    val isLowStock: Boolean,
    val isOutOfStock: Boolean,
)

data class ProcurementHistoryUi(
    val id: Long,
    val productName: String,
    val quantityLabel: String,
    val createdAtLabel: String,
    val note: String,
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
    val createdAtMillis: Long,
    val createdAtLabel: String,
    val totalCents: Int,
    val totalLabel: String,
    val note: String,
    val itemsSummary: String,
)

data class NotedReceiptUi(
    val receiptNumber: String,
    val waiterName: String,
    val createdAtMillis: Long,
    val createdAtLabel: String,
    val totalCents: Int,
    val totalLabel: String,
    val note: String,
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
    val dashboardStartDateKey: String = LocalDate.now().toString(),
    val dashboardEndDateKey: String = LocalDate.now().toString(),
    val dashboardSelectedDateLabel: String = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale("hr", "HR"))),
    val dashboardWaiterOptions: List<String> = emptyList(),
    val selectedDashboardWaiterName: String = "",
    val dashboardHourlyRevenue: List<ComparisonBarUi> = emptyList(),
    val dashboardIsSingleDay: Boolean = true,
    val staffOverview: StaffOverviewUi = StaffOverviewUi(),
    val categorySales: List<DashboardSalesUi> = emptyList(),
    val productSales: List<DashboardSalesUi> = emptyList(),
    val staffProductSales: List<DashboardSalesUi> = emptyList(),
    val waiterRevenueComparison: List<ComparisonBarUi> = emptyList(),
    val waiterItemsComparison: List<ComparisonBarUi> = emptyList(),
    val waiterAnalytics: List<WaiterAnalyticsUi> = emptyList(),
    val notedReceipts: List<NotedReceiptUi> = emptyList(),
    val notedReceiptsTotalLabel: String = formatCurrency(0),
    val receiptHistory: List<ReceiptHistoryUi> = emptyList(),
    val cloudReceiptHistory: List<CloudReceiptUi> = emptyList(),
    val priceListVersions: List<PriceListVersionUi> = emptyList(),
    val inventoryItems: List<InventoryUi> = emptyList(),
    val inventoryPriceListLabel: String = "Trenutne cijene artikala",
    val inventoryTotalValueLabel: String = formatCurrency(0),
    val procurementHistory: List<ProcurementHistoryUi> = emptyList(),
    val cloudCafeName: String = "",
    val cloudUserName: String = "",
    val cloudUserRole: String = "",
    val canUseHouseAccount: Boolean = false,
    val canUseMusic: Boolean = false,
    val waiterInvitePayload: String = "",
)

private data class PosCoreInputs(
    val rows: List<CatalogRow>,
    val state: AppStateEntity,
    val dashboardStartDate: LocalDate,
    val dashboardEndDate: LocalDate,
    val dashboardWaiterFilter: String?,
    val selectedId: Long?,
    val cart: Map<Long, Int>,
)

private data class DashboardDateRange(
    val start: LocalDate,
    val end: LocalDate,
)

private data class PosHistoryInputs(
    val history: List<ReceiptHistoryItem>,
    val cloudHistory: List<CloudReceiptHistoryItem>,
    val localSalesLines: List<LocalSalesLineItem>,
)

private data class PosInventoryInputs(
    val items: List<InventoryItem>,
)

private data class PosProcurementInputs(
    val items: List<ProcurementHistoryItem>,
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
    private val selectedDashboardStartDate = MutableStateFlow(LocalDate.now())
    private val selectedDashboardEndDate = MutableStateFlow(LocalDate.now())
    private val selectedDashboardWaiter = MutableStateFlow<String?>(null)
    private val cartQuantities = MutableStateFlow<Map<Long, Int>>(linkedMapOf())
    private val _messages = MutableSharedFlow<String>()
    private val dashboardDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale("hr", "HR"))
    private val dashboardDateRange = combine(
        selectedDashboardStartDate,
        selectedDashboardEndDate,
    ) { start, end ->
        normalizeDashboardRange(start, end)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardDateRange(LocalDate.now(), LocalDate.now()),
    )

    private val catalogRows = repository.observeCatalogRows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val appState = repository.observeAppState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppStateEntity())

    private val receiptHistory = repository.observeReceiptHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val cloudReceiptHistory = repository.observeCloudReceiptHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val localSalesLines = repository.observeLocalSalesLines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val priceListVersions = repository.observePriceListVersions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val inventoryItems = repository.observeInventoryItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val procurementHistory = repository.observeProcurementHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val messages = _messages.asSharedFlow()

    private val coreInputs = combine(
        catalogRows,
        appState,
        dashboardDateRange,
        selectedDashboardWaiter,
        selectedCategoryId,
        cartQuantities,
    ) { values ->
        val rows = values[0] as List<CatalogRow>
        val state = values[1] as AppStateEntity
        val dashboardRange = values[2] as DashboardDateRange
        val dashboardWaiterFilter = values[3] as String?
        val selectedId = values[4] as Long?
        val cart = values[5] as Map<Long, Int>
        PosCoreInputs(
            rows = rows,
            state = state,
            dashboardStartDate = dashboardRange.start,
            dashboardEndDate = dashboardRange.end,
            dashboardWaiterFilter = dashboardWaiterFilter,
            selectedId = selectedId,
            cart = cart,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PosCoreInputs(
            rows = emptyList(),
            state = AppStateEntity(),
            dashboardStartDate = LocalDate.now(),
            dashboardEndDate = LocalDate.now(),
            dashboardWaiterFilter = null,
            selectedId = null,
            cart = emptyMap(),
        ),
    )

    private val historyInputs = combine(
        receiptHistory,
        cloudReceiptHistory,
        localSalesLines,
    ) { history, cloudHistory, localLines ->
        PosHistoryInputs(
            history = history,
            cloudHistory = cloudHistory,
            localSalesLines = localLines,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PosHistoryInputs(
            history = emptyList(),
            cloudHistory = emptyList(),
            localSalesLines = emptyList(),
        ),
    )

    private val inventoryInputs = inventoryItems.map { items ->
        PosInventoryInputs(items = items)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PosInventoryInputs(items = emptyList()),
    )

    private val procurementInputs = procurementHistory.map { items ->
        PosProcurementInputs(items = items)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PosProcurementInputs(items = emptyList()),
    )

    val uiState = combine(
        coreInputs,
        historyInputs,
        priceListVersions,
        inventoryInputs,
        procurementInputs,
    ) { core, historyInputs, versions, inventoryInputs, procurementInputs ->
        buildUiState(
            rows = core.rows,
            state = core.state,
            dashboardStartDate = core.dashboardStartDate,
            dashboardEndDate = core.dashboardEndDate,
            dashboardWaiterFilter = core.dashboardWaiterFilter,
            history = historyInputs.history,
            cloudHistory = historyInputs.cloudHistory,
            localSalesLines = historyInputs.localSalesLines,
            priceListVersions = versions,
            inventoryItems = inventoryInputs.items,
            procurementHistory = procurementInputs.items,
            selectedId = core.selectedId,
            cart = core.cart,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PosUiState())

    init {
        viewModelScope.launch {
            repository.prepare()
            refreshCloudMemberSilently()
            refreshCloudCatalogSilently()
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
            repository.observeCloudMemberProfile()
                .catch {
                    _messages.emit("Podaci konobara trenutno nisu dostupni.")
                }
                .collect { profile ->
                    if (profile != null) {
                        repository.applyCloudMemberProfile(profile)
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

    fun refreshCloudCatalog() {
        viewModelScope.launch {
            val count = runCatching {
                repository.refreshCloudCatalogNow()
            }.getOrElse {
                _messages.emit("Ne mogu osvježiti cloud cjenik.")
                return@launch
            }
            if (count > 0) {
                _messages.emit("Cjenik je osvježen iz clouda ($count artikala).")
            } else {
                _messages.emit("Nema cloud cjenika za osvježiti.")
            }
        }
    }

    fun refreshCloudCatalogSilently() {
        viewModelScope.launch {
            runCatching {
                repository.refreshCloudCatalogNow()
            }
        }
    }

    private fun refreshCloudMemberSilently() {
        viewModelScope.launch {
            runCatching {
                repository.refreshCloudMemberNow()
            }
        }
    }

    fun selectCategory(categoryId: Long) {
        selectedCategoryId.value = categoryId
    }

    fun selectDashboardRange(start: LocalDate, end: LocalDate) {
        val normalized = normalizeDashboardRange(start, end)
        selectedDashboardStartDate.value = normalized.start
        selectedDashboardEndDate.value = normalized.end
    }

    fun selectDashboardWaiter(waiterName: String?) {
        selectedDashboardWaiter.value = waiterName?.takeIf { it.isNotBlank() }
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

    fun saveReceipt(note: String = "") {
        viewModelScope.launch {
            val draft = buildDraftLines()
            if (draft.isEmpty()) {
                _messages.emit("Račun je prazan.")
                return@launch
            }

            val cleanNote = sanitizeReceiptNote(note, appState.value)
            val result = repository.saveReceipt(draft, cleanNote)
            if (result != null) {
                cartQuantities.value = linkedMapOf()
                _messages.emit("Račun ${result.receiptNumber} je spremljen.")
                launch {
                    val synced = runCatching {
                        repository.syncSavedReceiptOnline(
                            receiptNumber = result.receiptNumber,
                            lines = draft,
                            note = cleanNote,
                        )
                    }.getOrDefault(false)
                    if (!synced && appState.value.cloudCafeId.isNotBlank()) {
                        _messages.emit("Račun je spremljen lokalno, ali online sync nije uspio.")
                    }
                }
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

    suspend fun updateProductImage(
        productId: Long,
        imageDataUrl: String,
    ): Boolean {
        if (imageDataUrl.isBlank()) {
            _messages.emit("Slika nije odabrana.")
            return false
        }

        return runCatching {
            repository.updateProductImage(productId, imageDataUrl)
            _messages.emit("Slika artikla je spremljena.")
            true
        }.getOrElse {
            _messages.emit(it.message ?: "Spremanje slike nije uspjelo.")
            false
        }
    }

    suspend fun setInventoryQuantity(
        productId: Long,
        quantityInput: String,
    ): Boolean {
        val quantity = quantityInput.trim().toIntOrNull()
        if (quantity == null) {
            _messages.emit("Upiši količinu kao cijeli broj.")
            return false
        }
        if (quantity < 0) {
            _messages.emit("Količina ne može biti manja od nule.")
            return false
        }

        return runCatching {
            repository.setInventoryQuantity(productId, quantity)
            _messages.emit("Stanje skladišta je spremljeno.")
            true
        }.getOrElse {
            _messages.emit(it.message ?: "Spremanje stanja skladišta nije uspjelo.")
            false
        }
    }

    suspend fun setInventoryQuantities(
        quantityInputs: Map<Long, String>,
    ): Boolean {
        if (quantityInputs.isEmpty()) {
            _messages.emit("Nema promjena za spremanje.")
            return false
        }

        val parsed = linkedMapOf<Long, Int>()
        quantityInputs.forEach { (productId, input) ->
            val quantity = input.trim().toIntOrNull()
            if (quantity == null) {
                _messages.emit("Sve količine moraju biti cijeli brojevi.")
                return false
            }
            if (quantity < 0) {
                _messages.emit("Količina ne može biti manja od nule.")
                return false
            }
            parsed[productId] = quantity
        }

        return runCatching {
            repository.setInventoryQuantities(parsed)
            _messages.emit("Skladište je spremljeno.")
            true
        }.getOrElse {
            _messages.emit(it.message ?: "Spremanje skladišta nije uspjelo.")
            false
        }
    }

    suspend fun addProcurementEntries(
        quantityInputs: Map<Long, String>,
        note: String,
    ): Boolean {
        val parsed = linkedMapOf<Long, Int>()
        quantityInputs.forEach { (productId, input) ->
            val quantity = input.trim().toIntOrNull()
            if (quantity == null) {
                _messages.emit("Sve ulazne količine moraju biti cijeli brojevi.")
                return false
            }
            if (quantity < 0) {
                _messages.emit("Ulazna količina ne može biti negativna.")
                return false
            }
            if (quantity > 0) {
                parsed[productId] = quantity
            }
        }

        if (parsed.isEmpty()) {
            _messages.emit("Upiši barem jedan ulaz robe.")
            return false
        }

        return runCatching {
            repository.addProcurementEntries(parsed, note)
            _messages.emit("Nabava je spremljena u skladište.")
            true
        }.getOrElse {
            _messages.emit(it.message ?: "Spremanje nabave nije uspjelo.")
            false
        }
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

    suspend fun refreshWebAdminInvitePayload(): String? = runCatching {
        val payload = repository.refreshWebAdminInvitePayload()
        _messages.emit("Generiran je web admin kod.")
        payload
    }.getOrElse {
        _messages.emit(it.message ?: "Ne mogu generirati web admin kod.")
        null
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

    suspend fun buildCompleteSalesExportPayload(): ExportPayload? {
        val payload = repository.buildCompleteSalesExport(cloudReceiptHistory.value)
        if (payload == null) {
            _messages.emit("Nema prodaje za potpuni export.")
        }
        return payload
    }

    suspend fun buildPriceListExportPayload(): ExportPayload? {
        val payload = repository.buildPriceListExport()
        if (payload == null) {
            _messages.emit("Nema aktivnog cjenika za export.")
        }
        return payload
    }

    suspend fun buildBackupExportPayload(): ExportPayload? = runCatching {
        repository.buildDatabaseBackup()
    }.getOrElse {
        _messages.emit(it.message ?: "Backup nije uspio.")
        null
    }

    suspend fun restoreBackup(content: String): Boolean = runCatching {
        repository.restoreDatabaseFromBackup(content)
        _messages.emit("Backup je uspješno vraćen.")
        true
    }.getOrElse {
        _messages.emit(it.message ?: "Restore backupa nije uspio.")
        false
    }

    suspend fun saveCurrentPriceListVersion(label: String): Boolean = runCatching {
        repository.saveCurrentPriceListVersion(label)
        _messages.emit("Cjenik je spremljen kao nova verzija.")
        true
    }.getOrElse {
        _messages.emit(it.message ?: "Spremanje cjenika nije uspjelo.")
        false
    }

    suspend fun importPriceList(
        csvContent: String,
        label: String,
    ): Boolean = runCatching {
        repository.importPriceList(csvContent, label)
        _messages.emit("Cjenik je importan i postavljen kao aktivan.")
        true
    }.getOrElse {
        _messages.emit(it.message ?: "Import cjenika nije uspio.")
        false
    }

    suspend fun activatePriceList(versionId: Long): Boolean = runCatching {
        repository.activatePriceListVersion(versionId)
        _messages.emit("Cjenik je aktiviran.")
        true
    }.getOrElse {
        _messages.emit(it.message ?: "Aktivacija cjenika nije uspjela.")
        false
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

    fun clearAllSalesData() {
        viewModelScope.launch {
            runCatching {
                repository.clearAllSalesData()
            }.onSuccess {
                _messages.emit("Sva prodaja i svi računi su obrisani.")
            }.onFailure {
                _messages.emit(it.message ?: "Brisanje svih prodaja nije uspjelo.")
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
                _messages.emit("Datoteka je spremljena kao $filename.")
            } else {
                _messages.emit("Spremanje datoteke nije uspjelo.")
            }
        }
    }

    fun notifyPriceListImportHandled(success: Boolean, filename: String) {
        viewModelScope.launch {
            if (!success) {
                _messages.emit("Import cjenika iz datoteke $filename nije uspio.")
            }
        }
    }

    private fun buildUiState(
        rows: List<CatalogRow>,
        state: AppStateEntity,
        dashboardStartDate: LocalDate,
        dashboardEndDate: LocalDate,
        dashboardWaiterFilter: String?,
        history: List<ReceiptHistoryItem>,
        cloudHistory: List<CloudReceiptHistoryItem>,
        localSalesLines: List<LocalSalesLineItem>,
        priceListVersions: List<PriceListVersionItem>,
        inventoryItems: List<InventoryItem>,
        procurementHistory: List<ProcurementHistoryItem>,
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
                    imageDataUrl = row.imageDataUrl,
                    accentColor = row.accentColor,
                    quantityInCart = cart[row.productId] ?: 0,
                )
            }

        val inventorySourceForCategory = inventoryItems
            .filter { item -> item.categoryId == activeCategoryId }
        val inventoryForCategory = inventorySourceForCategory
            .map { item ->
                val quantity = item.quantityUnits
                val stockValueCents = item.priceCents * quantity
                InventoryUi(
                    productId = item.productId,
                    categoryId = item.categoryId,
                    name = item.productName,
                    emoji = item.emoji,
                    unitPriceLabel = formatCurrency(item.priceCents),
                    quantityUnits = quantity,
                    quantityLabel = "$quantity kom",
                    stockValueLabel = formatCurrency(stockValueCents),
                    updatedAtLabel = if (item.updatedAtMillis > 0L) {
                        formatDateTime(item.updatedAtMillis, ZoneId.systemDefault())
                    } else {
                        "Nije postavljeno"
                    },
                    isLowStock = quantity in 1..5,
                    isOutOfStock = quantity <= 0,
                )
            }
        val referencePriceList = priceListVersions.firstOrNull { version -> version.isActive }
            ?: priceListVersions.firstOrNull()
        val inventoryPriceListLabel = referencePriceList?.effectiveDateLabel ?: "Trenutne cijene artikala"
        val inventoryTotalValueCents = inventorySourceForCategory.sumOf { item ->
            item.quantityUnits * item.priceCents
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

        val productCategoryByName = rows
            .groupBy { row -> row.productName.trim().lowercase() }
            .mapValues { entry -> entry.value.firstOrNull()?.categoryName.orEmpty() }
        val dashboardRangeLabel = formatDashboardRangeLabel(dashboardStartDate, dashboardEndDate)
        val localReceiptsForRange = history.filter { item ->
            isWithinRange(item.createdAtMillis, dashboardStartDate, dashboardEndDate)
        }
        val cloudReceiptsForRange = cloudHistory.filter { item ->
            isWithinRange(item.createdAtMillis, dashboardStartDate, dashboardEndDate)
        }
        val localLinesForRange = localSalesLines.filter { line ->
            isWithinRange(line.createdAtMillis, dashboardStartDate, dashboardEndDate)
        }

        val waiterAggregates = linkedMapOf<String, WaiterAggregate>()

        val adminName = when {
            state.cloudUserRole == "admin" && state.cloudUserName.isNotBlank() -> state.cloudUserName
            else -> "Admin"
        }
        val adminAggregate = if (localReceiptsForRange.isNotEmpty() || localLinesForRange.isNotEmpty()) {
            waiterAggregates.getOrPut(adminName) {
                WaiterAggregate(name = adminName)
            }.apply {
                receiptsCount = localReceiptsForRange.size
                totalCents = localReceiptsForRange.sumOf { receipt -> receipt.totalCents }
                itemsCount = 0
                this.products.clear()
            }
        } else {
            null
        }

        localLinesForRange.forEach { line ->
            adminAggregate?.let { aggregate ->
                aggregate.itemsCount += line.quantity
                val adminProduct = aggregate.products.getOrPut(line.productName) { ProductAggregate() }
                adminProduct.quantity += line.quantity
                adminProduct.totalCents += line.lineTotalCents
            }
        }

        cloudReceiptsForRange.forEach { receipt ->
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
            }
        }

        val sortedWaiters = waiterAggregates.values.sortedWith(
            compareByDescending<WaiterAggregate> { it.totalCents }
                .thenByDescending { it.itemsCount }
                .thenBy { it.name },
        )

        val dashboardWaiterOptions = buildList {
            add("Svi")
            addAll(sortedWaiters.map { aggregate -> aggregate.name })
        }
        val activeDashboardWaiter = dashboardWaiterFilter?.takeIf { filter ->
            sortedWaiters.any { aggregate -> aggregate.name == filter }
        }
        val filteredLocalReceiptsForRange = if (activeDashboardWaiter == null || activeDashboardWaiter == adminName) {
            localReceiptsForRange
        } else {
            emptyList()
        }
        val filteredLocalLinesForRange = if (activeDashboardWaiter == null || activeDashboardWaiter == adminName) {
            localLinesForRange
        } else {
            emptyList()
        }
        val filteredCloudReceiptsForRange = if (activeDashboardWaiter == null) {
            cloudReceiptsForRange
        } else {
            cloudReceiptsForRange.filter { receipt ->
                receipt.waiterName.ifBlank { "Nepoznati konobar" } == activeDashboardWaiter
            }
        }
        val filteredWaiters = if (activeDashboardWaiter == null) {
            sortedWaiters
        } else {
            sortedWaiters.filter { aggregate -> aggregate.name == activeDashboardWaiter }
        }

        val productAggregates = linkedMapOf<String, ProductAggregate>()
        val categoryAggregates = linkedMapOf<String, ProductAggregate>()
        filteredLocalLinesForRange.forEach { line ->
            val product = productAggregates.getOrPut(line.productName) { ProductAggregate() }
            product.quantity += line.quantity
            product.totalCents += line.lineTotalCents

            val category = categoryAggregates.getOrPut(line.categoryName) { ProductAggregate() }
            category.quantity += line.quantity
            category.totalCents += line.lineTotalCents
        }
        filteredCloudReceiptsForRange.forEach { receipt ->
            receipt.items.forEach { item ->
                val product = productAggregates.getOrPut(item.name) { ProductAggregate() }
                product.quantity += item.quantity
                product.totalCents += item.lineTotalCents

                val categoryName = productCategoryByName[item.name.trim().lowercase()]
                    ?.ifBlank { "Ostalo" }
                    ?: "Ostalo"
                val category = categoryAggregates.getOrPut(categoryName) { ProductAggregate() }
                category.quantity += item.quantity
                category.totalCents += item.lineTotalCents
            }
        }

        val hourlyTotals = IntArray(24)
        val hourlyReceipts = IntArray(24)
        if (dashboardStartDate == dashboardEndDate) {
            filteredLocalReceiptsForRange.forEach { receipt ->
                val hour = Instant.ofEpochMilli(receipt.createdAtMillis)
                    .atZone(ZoneId.systemDefault())
                    .hour
                hourlyTotals[hour] += receipt.totalCents
                hourlyReceipts[hour] += 1
            }
            filteredCloudReceiptsForRange.forEach { receipt ->
                val hour = Instant.ofEpochMilli(receipt.createdAtMillis)
                    .atZone(ZoneId.systemDefault())
                    .hour
                hourlyTotals[hour] += receipt.totalCents
                hourlyReceipts[hour] += 1
            }
        }

        val staffTotalCents = filteredWaiters.sumOf { it.totalCents }
        val staffReceiptsCount = filteredWaiters.sumOf { it.receiptsCount }
        val staffItemsCount = filteredWaiters.sumOf { it.itemsCount }
        val notedReceipts = buildList {
            filteredLocalReceiptsForRange
                .filter { receipt -> receipt.note.isNotBlank() }
                .forEach { receipt ->
                    add(
                        NotedReceiptUi(
                            receiptNumber = receipt.receiptNumber,
                            waiterName = adminName,
                            createdAtMillis = receipt.createdAtMillis,
                            createdAtLabel = receipt.createdAtLabel,
                            totalCents = receipt.totalCents,
                            totalLabel = receipt.totalLabel,
                            note = receipt.note,
                        ),
                    )
                }
            filteredCloudReceiptsForRange
                .filter { receipt -> receipt.note.isNotBlank() }
                .forEach { receipt ->
                    add(
                        NotedReceiptUi(
                            receiptNumber = receipt.receiptNumber,
                            waiterName = receipt.waiterName.ifBlank { "Nepoznati konobar" },
                            createdAtMillis = receipt.createdAtMillis,
                            createdAtLabel = receipt.createdAtLabel,
                            totalCents = receipt.totalCents,
                            totalLabel = receipt.totalLabel,
                            note = receipt.note,
                        ),
                    )
                }
        }.sortedByDescending { receipt -> receipt.createdAtMillis }
        val procurementHistoryUi = procurementHistory
            .take(20)
            .map { item ->
                ProcurementHistoryUi(
                    id = item.id,
                    productName = item.productName,
                    quantityLabel = "+${item.quantityUnits} kom",
                    createdAtLabel = formatDateTime(item.createdAtMillis, ZoneId.systemDefault()),
                    note = item.note,
                )
            }

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
                totalLabel = formatCurrency(staffTotalCents),
                receiptsCountLabel = staffReceiptsCount.toString(),
                resetLabel = "Za $dashboardRangeLabel",
            ),
            dashboardStartDateKey = dashboardStartDate.toString(),
            dashboardEndDateKey = dashboardEndDate.toString(),
            dashboardSelectedDateLabel = dashboardRangeLabel,
            dashboardWaiterOptions = dashboardWaiterOptions,
            selectedDashboardWaiterName = activeDashboardWaiter.orEmpty(),
            dashboardHourlyRevenue = buildHourlyComparisonBars(hourlyTotals, hourlyReceipts),
            dashboardIsSingleDay = dashboardStartDate == dashboardEndDate,
            staffOverview = StaffOverviewUi(
                waiterTotalLabel = formatCurrency(staffTotalCents),
                waiterReceiptsLabel = staffReceiptsCount.toString(),
                waiterItemsLabel = "$staffItemsCount kom",
                combinedTotalLabel = formatCurrency(staffTotalCents),
                combinedReceiptsLabel = staffReceiptsCount.toString(),
            ),
            categorySales = categoryAggregates.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, ProductAggregate>> { it.value.totalCents }
                        .thenByDescending { it.value.quantity }
                        .thenBy { it.key },
                )
                .map { entry ->
                    DashboardSalesUi(
                        name = entry.key,
                        quantityLabel = "${entry.value.quantity} kom",
                        totalLabel = formatCurrency(entry.value.totalCents),
                    )
                },
            productSales = productAggregates.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, ProductAggregate>> { it.value.totalCents }
                        .thenByDescending { it.value.quantity }
                        .thenBy { it.key },
                )
                .map { entry ->
                    DashboardSalesUi(
                        name = entry.key,
                        quantityLabel = "${entry.value.quantity} kom",
                        totalLabel = formatCurrency(entry.value.totalCents),
                    )
                },
            staffProductSales = productAggregates.entries
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
                waiters = filteredWaiters,
                metric = { aggregate -> aggregate.totalCents },
                valueLabel = { aggregate -> formatCurrency(aggregate.totalCents) },
                supportingLabel = { aggregate -> "${aggregate.receiptsCount} računa" },
            ),
            waiterItemsComparison = buildComparisonBars(
                waiters = filteredWaiters,
                metric = { aggregate -> aggregate.itemsCount },
                valueLabel = { aggregate -> "${aggregate.itemsCount} kom" },
                supportingLabel = { aggregate -> formatCurrency(aggregate.totalCents) },
            ),
            waiterAnalytics = filteredWaiters.map { aggregate ->
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
            notedReceipts = notedReceipts,
            notedReceiptsTotalLabel = formatCurrency(notedReceipts.sumOf { receipt -> receipt.totalCents }),
            receiptHistory = history.map { item ->
                ReceiptHistoryUi(
                    id = item.id,
                    receiptNumber = item.receiptNumber,
                    createdAtMillis = item.createdAtMillis,
                    createdAtLabel = item.createdAtLabel,
                    totalCents = item.totalCents,
                    totalLabel = item.totalLabel,
                    itemsCountLabel = item.itemsCountLabel,
                    note = item.note,
                )
            },
            cloudReceiptHistory = cloudHistory.map { item ->
                CloudReceiptUi(
                    id = item.id,
                    receiptNumber = item.receiptNumber,
                    waiterName = item.waiterName,
                    createdAtMillis = item.createdAtMillis,
                    createdAtLabel = item.createdAtLabel,
                    totalCents = item.totalCents,
                    totalLabel = item.totalLabel,
                    note = item.note,
                    itemsSummary = item.itemsSummary,
                )
            },
            priceListVersions = priceListVersions.map { version ->
                PriceListVersionUi(
                    id = version.id,
                    name = version.name,
                    effectiveDateLabel = version.effectiveDateLabel,
                    createdAtLabel = version.createdAtLabel,
                    itemsCountLabel = version.itemsCountLabel,
                    isActive = version.isActive,
                )
            },
            inventoryItems = inventoryForCategory,
            inventoryPriceListLabel = inventoryPriceListLabel,
            inventoryTotalValueLabel = formatCurrency(inventoryTotalValueCents),
            procurementHistory = procurementHistoryUi,
            cloudCafeName = state.cloudCafeName,
            cloudUserName = state.cloudUserName,
            cloudUserRole = state.cloudUserRole,
            canUseHouseAccount = state.cloudUserRole == "admin" || state.canUseHouseAccount,
            canUseMusic = state.cloudUserRole == "admin" || state.canUseMusic,
            waiterInvitePayload = repository.buildInvitePayload(state).orEmpty(),
        )
    }

    private fun sanitizeReceiptNote(
        note: String,
        state: AppStateEntity,
    ): String {
        val isAdmin = state.cloudUserRole == "admin"
        val canUseHouseAccount = isAdmin || state.canUseHouseAccount
        val canUseMusic = isAdmin || state.canUseMusic
        return note.split("•")
            .map { item -> item.trim() }
            .filter { item ->
                when (item) {
                    "Na račun kuće" -> canUseHouseAccount
                    "Muzika" -> canUseMusic
                    else -> item.isNotBlank()
                }
            }
            .joinToString(" • ")
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

    private fun buildHourlyComparisonBars(
        hourlyTotals: IntArray,
        hourlyReceipts: IntArray,
    ): List<ComparisonBarUi> {
        val hoursWithSales = (0 until 24).filter { hour -> hourlyTotals[hour] > 0 }
        if (hoursWithSales.isEmpty()) {
            return emptyList()
        }

        val maxValue = hoursWithSales.maxOf { hour -> hourlyTotals[hour] }.coerceAtLeast(1)
        return hoursWithSales.map { hour ->
            ComparisonBarUi(
                label = String.format("%02d:00", hour),
                valueLabel = formatCurrency(hourlyTotals[hour]),
                supportingLabel = "${hourlyReceipts[hour]} računa",
                progress = hourlyTotals[hour].toFloat() / maxValue.toFloat(),
            )
        }
    }

    private fun normalizeDashboardRange(
        start: LocalDate,
        end: LocalDate,
    ): DashboardDateRange = if (start <= end) {
        DashboardDateRange(start = start, end = end)
    } else {
        DashboardDateRange(start = end, end = start)
    }

    private fun formatDashboardRangeLabel(
        start: LocalDate,
        end: LocalDate,
    ): String = if (start == end) {
        start.format(dashboardDateFormatter)
    } else {
        "${start.format(dashboardDateFormatter)} - ${end.format(dashboardDateFormatter)}"
    }

    private fun isWithinRange(
        epochMillis: Long,
        start: LocalDate,
        end: LocalDate,
    ): Boolean {
        val date = Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return !date.isBefore(start) && !date.isAfter(end)
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
