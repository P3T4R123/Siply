package com.playground.siply.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.playground.siply.data.ExportPayload
import com.playground.siply.data.LastReceiptInfo
import com.playground.siply.formatCurrency
import java.text.DecimalFormat
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private enum class MainTab {
    Dashboard,
    Products,
    Receipt,
    History,
    Settings,
}

private enum class SettingsSection(
    val label: String,
) {
    Online("Online"),
    PriceLists("Cjenici"),
    Products("Artikli"),
    Inventory("Skladište"),
    Procurement("Nabava"),
    Backup("Backup"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosApp(
    uiState: PosUiState,
    messages: Flow<String>,
    onSelectDashboardRange: (LocalDate, LocalDate) -> Unit,
    onSelectDashboardWaiter: (String?) -> Unit,
    onSelectCategory: (Long) -> Unit,
    onAddProduct: (Long) -> Unit,
    onAdjustQuantity: (Long, Int) -> Unit,
    onSaveReceipt: (String) -> Unit,
    onDeleteLastReceipt: () -> Unit,
    onClearAllSalesData: () -> Unit,
    onResetDailyStats: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onAddCatalogProduct: suspend (Long?, String, String) -> Boolean,
    onUpdateProductImage: suspend (Long, String) -> Boolean,
    onSetInventoryQuantities: suspend (Map<Long, String>) -> Boolean,
    onAddProcurementEntries: suspend (Map<Long, String>, String) -> Boolean,
    onSaveCurrentPriceList: suspend (String) -> Boolean,
    onImportPriceList: suspend (String, String) -> Boolean,
    onActivatePriceList: suspend (Long) -> Boolean,
    onCreateOnlineCafe: suspend (String, String) -> Boolean,
    onRefreshWaiterInvite: suspend () -> Boolean,
    onRefreshWebAdminInvite: suspend () -> String?,
    onRefreshCloudCatalog: () -> Unit,
    onJoinCafeAsWaiter: suspend (String, String) -> Boolean,
    onRestoreBackup: suspend (String) -> Boolean,
    onExportResult: (Boolean, String) -> Unit,
    loadLastReceiptInfo: suspend () -> LastReceiptInfo?,
    buildExportPayload: suspend () -> ExportPayload?,
    buildCompleteSalesExportPayload: suspend () -> ExportPayload?,
    buildPriceListExportPayload: suspend () -> ExportPayload?,
    buildBackupPayload: suspend () -> ExportPayload?,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Products) }
    var pendingExport by remember { mutableStateOf<ExportPayload?>(null) }
    var pendingDelete by remember { mutableStateOf<LastReceiptInfo?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    val isWaiter = uiState.cloudUserRole == "waiter"

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val export = pendingExport
        pendingExport = null
        if (uri == null || export == null) return@rememberLauncherForActivityResult
        onExportResult(writeTextDocument(context, uri, export.content), export.filename)
    }

    LaunchedEffect(messages) {
        messages.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(uiState.cartItemsCount) {
        if (uiState.cartItemsCount == 0 && selectedTab == MainTab.Receipt) {
            selectedTab = MainTab.Products
        }
    }

    LaunchedEffect(isWaiter) {
        if (isWaiter && (selectedTab == MainTab.Dashboard || selectedTab == MainTab.History)) {
            selectedTab = MainTab.Products
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text(tabTitle(selectedTab))
                        Text(
                            text = tabSubtitle(selectedTab, uiState),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(onClick = onToggleDarkMode) {
                        Icon(
                            imageVector = if (uiState.darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = "Promijeni temu",
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                if (!isWaiter) {
                    NavigationBarItem(
                        selected = selectedTab == MainTab.Dashboard,
                        onClick = { selectedTab = MainTab.Dashboard },
                        icon = { Icon(Icons.Rounded.BarChart, contentDescription = "Dashboard") },
                        label = null,
                        alwaysShowLabel = false,
                    )
                }
                NavigationBarItem(
                    selected = selectedTab == MainTab.Products,
                    onClick = { selectedTab = MainTab.Products },
                    icon = { Icon(Icons.Rounded.GridView, contentDescription = "Artikli") },
                    label = null,
                    alwaysShowLabel = false,
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Receipt,
                    onClick = { selectedTab = MainTab.Receipt },
                    icon = { ReceiptTabIcon(uiState.cartItemsCount) },
                    label = null,
                    alwaysShowLabel = false,
                )
                if (!isWaiter) {
                    NavigationBarItem(
                        selected = selectedTab == MainTab.History,
                        onClick = { selectedTab = MainTab.History },
                        icon = { Icon(Icons.Rounded.History, contentDescription = "Povijest") },
                        label = null,
                        alwaysShowLabel = false,
                    )
                }
                NavigationBarItem(
                    selected = selectedTab == MainTab.Settings,
                    onClick = { selectedTab = MainTab.Settings },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                    label = null,
                    alwaysShowLabel = false,
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        when (selectedTab) {
            MainTab.Dashboard -> DashboardTab(
                uiState = uiState,
                onSelectRange = onSelectDashboardRange,
                onSelectWaiter = onSelectDashboardWaiter,
                modifier = Modifier.padding(padding),
            )
            MainTab.Products -> ProductsTab(
                uiState = uiState,
                onSelectCategory = onSelectCategory,
                onAddProduct = onAddProduct,
                modifier = Modifier.then(Modifier).padding(padding),
            )
            MainTab.Receipt -> ReceiptTab(
                uiState = uiState,
                onAdjustQuantity = onAdjustQuantity,
                onSaveReceipt = onSaveReceipt,
                onBackToProducts = { selectedTab = MainTab.Products },
                modifier = Modifier.then(Modifier).padding(padding),
            )
            MainTab.History -> HistoryTab(
                uiState = uiState,
                onExportSummary = {
                    scope.launch {
                        val export = buildExportPayload()
                        if (export != null) {
                            pendingExport = export
                            exportLauncher.launch(export.filename)
                        }
                    }
                },
                onExportAll = {
                    scope.launch {
                        val export = buildCompleteSalesExportPayload()
                        if (export != null) {
                            pendingExport = export
                            exportLauncher.launch(export.filename)
                        }
                    }
                },
                onExportResult = onExportResult,
                onDelete = {
                    scope.launch { pendingDelete = loadLastReceiptInfo() }
                },
                onReset = { showResetDialog = true },
                onClearAll = { showClearAllDialog = true },
                modifier = Modifier.then(Modifier).padding(padding),
            )
            MainTab.Settings -> SettingsTab(
                uiState = uiState,
                onSelectCategory = onSelectCategory,
                onAddCatalogProduct = onAddCatalogProduct,
                onUpdateProductImage = onUpdateProductImage,
                onSetInventoryQuantities = onSetInventoryQuantities,
                onAddProcurementEntries = onAddProcurementEntries,
                onSaveCurrentPriceList = onSaveCurrentPriceList,
                onImportPriceList = onImportPriceList,
                onActivatePriceList = onActivatePriceList,
                onCreateOnlineCafe = onCreateOnlineCafe,
                onRefreshWaiterInvite = onRefreshWaiterInvite,
                onRefreshWebAdminInvite = onRefreshWebAdminInvite,
                onRefreshCloudCatalog = onRefreshCloudCatalog,
                onJoinCafeAsWaiter = onJoinCafeAsWaiter,
                onRestoreBackup = onRestoreBackup,
                buildPriceListExportPayload = buildPriceListExportPayload,
                buildBackupPayload = buildBackupPayload,
                onExportResult = onExportResult,
                modifier = Modifier.padding(padding),
            )
        }
    }

    pendingDelete?.let { receipt ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Obrisati zadnji račun?") },
            text = { Text("Račun ${receipt.receiptNumber} (${receipt.totalLabel}) će biti obrisan.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDeleteLastReceipt()
                }) { Text("Obriši") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Odustani") }
            },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Dnevni reset") },
            text = { Text("Reset vraća dnevnu statistiku na nulu, ali ne briše spremljene račune.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    onResetDailyStats()
                }) { Text("Resetiraj") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Odustani") }
            },
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Obrisati svu prodaju?") },
            text = { Text("Ovo briše sve lokalne račune i sve online račune konobara. Katalog i cjenici ostaju sačuvani.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearAllDialog = false
                    onClearAllSalesData()
                }) { Text("Obriši sve") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Odustani") }
            },
        )
    }
}

@Composable
private fun ReceiptTabIcon(cartItemsCount: Int) {
    if (cartItemsCount > 0) {
        BadgedBox(badge = { Badge { Text(cartItemsCount.toString()) } }) {
            Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = "Račun")
        }
    } else {
        Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = "Račun")
    }
}

@Composable
private fun DashboardTab(
    uiState: PosUiState,
    onSelectRange: (LocalDate, LocalDate) -> Unit,
    onSelectWaiter: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedStartDate = runCatching { LocalDate.parse(uiState.dashboardStartDateKey) }.getOrElse { LocalDate.now() }
    val selectedEndDate = runCatching { LocalDate.parse(uiState.dashboardEndDateKey) }.getOrElse { selectedStartDate }
    val normalizedDashboardRange = remember(selectedStartDate, selectedEndDate) {
        normalizeDateRange(selectedStartDate, selectedEndDate)
    }
    var visibleMonthKey by rememberSaveable(uiState.dashboardStartDateKey, uiState.dashboardEndDateKey) {
        mutableStateOf(YearMonth.from(normalizedDashboardRange.second).toString())
    }
    var showCalendarDialog by rememberSaveable { mutableStateOf(false) }
    val visibleMonth = runCatching { YearMonth.parse(visibleMonthKey) }
        .getOrElse { YearMonth.from(normalizedDashboardRange.second) }
    val dayTotals = remember(uiState.receiptHistory, uiState.cloudReceiptHistory) {
        buildDayTotals(uiState)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Dashboard po periodu",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = uiState.dailyStats.resetLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { showCalendarDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Kalendar • ${uiState.dashboardSelectedDateLabel}")
                    }
                    if (uiState.dashboardWaiterOptions.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Konobar",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(uiState.dashboardWaiterOptions, key = { it }) { waiterName ->
                                    val isAll = waiterName == "Svi"
                                    FilterChip(
                                        selected = if (isAll) {
                                            uiState.selectedDashboardWaiterName.isBlank()
                                        } else {
                                            uiState.selectedDashboardWaiterName == waiterName
                                        },
                                        onClick = {
                                            onSelectWaiter(if (isAll) null else waiterName)
                                        },
                                        label = { Text(waiterName) },
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatCard("Promet", uiState.dailyStats.totalLabel, Modifier.weight(1f))
                        StatCard("Računa", uiState.dailyStats.receiptsCountLabel, Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            StaffOverviewSection(uiState)
        }

        item {
            ComparisonChartSection(
                title = "Usporedba prometa po konobaru",
                items = uiState.waiterRevenueComparison,
                emptyText = "Konobari još nemaju online računa.",
            )
        }

        item {
            ComparisonChartSection(
                title = "Usporedba količine po konobaru",
                items = uiState.waiterItemsComparison,
                emptyText = "Nema prodanih stavki od osoblja.",
            )
        }

        item {
            WaiterAnalyticsSection(
                items = uiState.waiterAnalytics,
                emptyText = "Kad konobari krenu spremati račune, ovdje ćeš vidjeti tko je što prodao.",
            )
        }

        item {
            NotedReceiptsSection(
                items = uiState.notedReceipts,
                totalLabel = uiState.notedReceiptsTotalLabel,
                emptyText = "Nema računa s bilješkama za odabrani period.",
            )
        }

        item {
            SalesBreakdownSection(
                title = if (uiState.selectedDashboardWaiterName.isBlank()) {
                    "Top artikli ekipe"
                } else {
                    "Top artikli • ${uiState.selectedDashboardWaiterName}"
                },
                items = uiState.staffProductSales,
                emptyText = "Nema prodaje osoblja za odabrani period.",
            )
        }

        item {
            ComparisonChartSection(
                title = if (uiState.selectedDashboardWaiterName.isBlank()) {
                    "Promet po satu"
                } else {
                    "Promet po satu • ${uiState.selectedDashboardWaiterName}"
                },
                items = uiState.dashboardHourlyRevenue,
                emptyText = if (uiState.dashboardIsSingleDay) {
                    "Nema prometa po satu za odabrani dan."
                } else {
                    "Promet po satu je dostupan kad odabereš jedan dan."
                },
            )
        }

        item {
            SalesBreakdownSection(
                title = "Prodaja po kategorijama",
                items = uiState.categorySales,
                emptyText = "Nema prodaje po kategorijama za odabrani period.",
            )
        }

        item {
            SalesBreakdownSection(
                title = "Prodaja po artiklima",
                items = uiState.productSales,
                emptyText = "Nema prodaje po artiklima za odabrani period.",
            )
        }
    }

    if (showCalendarDialog) {
        CalendarDialog(
            title = "Odaberi period za dashboard",
            visibleMonth = visibleMonth,
            selectedStartDate = normalizedDashboardRange.first,
            selectedEndDate = normalizedDashboardRange.second,
            dayTotals = dayTotals,
            onDismiss = { showCalendarDialog = false },
            onPreviousMonth = {
                visibleMonthKey = visibleMonth.minusMonths(1).toString()
            },
            onNextMonth = {
                visibleMonthKey = visibleMonth.plusMonths(1).toString()
            },
            onApplyDates = { start, end ->
                visibleMonthKey = YearMonth.from(end).toString()
                onSelectRange(start, end)
                showCalendarDialog = false
            },
        )
    }
}

@Composable
private fun ProductsTab(
    uiState: PosUiState,
    onSelectCategory: (Long) -> Unit,
    onAddProduct: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val productRows = uiState.products.chunked(2)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Kategorije",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = uiState.selectedCategoryId == category.id,
                            onClick = { onSelectCategory(category.id) },
                            label = {
                                Text(
                                    text = category.name,
                                    fontWeight = if (uiState.selectedCategoryId == category.id) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }

        if (uiState.loading) {
            item { EmptyCard("Punim artikle...") }
        } else if (uiState.products.isEmpty()) {
            item { EmptyCard("Nema artikala u ovoj kategoriji.") }
        } else {
            items(productRows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    row.forEach { product ->
                        ProductCard(
                            product = product,
                            onClick = { onAddProduct(product.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    uiState: PosUiState,
    onSelectCategory: (Long) -> Unit,
    onAddCatalogProduct: suspend (Long?, String, String) -> Boolean,
    onUpdateProductImage: suspend (Long, String) -> Boolean,
    onSetInventoryQuantities: suspend (Map<Long, String>) -> Boolean,
    onAddProcurementEntries: suspend (Map<Long, String>, String) -> Boolean,
    onSaveCurrentPriceList: suspend (String) -> Boolean,
    onImportPriceList: suspend (String, String) -> Boolean,
    onActivatePriceList: suspend (Long) -> Boolean,
    onCreateOnlineCafe: suspend (String, String) -> Boolean,
    onRefreshWaiterInvite: suspend () -> Boolean,
    onRefreshWebAdminInvite: suspend () -> String?,
    onRefreshCloudCatalog: () -> Unit,
    onJoinCafeAsWaiter: suspend (String, String) -> Boolean,
    onRestoreBackup: suspend (String) -> Boolean,
    buildPriceListExportPayload: suspend () -> ExportPayload?,
    buildBackupPayload: suspend () -> ExportPayload?,
    onExportResult: (Boolean, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var priceListLabel by rememberSaveable { mutableStateOf(defaultPriceListLabel()) }
    var priceListLoading by rememberSaveable { mutableStateOf(false) }
    var priceListStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var priceListStatusSuccess by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var cafeName by rememberSaveable(uiState.cloudCafeName) { mutableStateOf(uiState.cloudCafeName) }
    var adminName by rememberSaveable(uiState.cloudUserName) { mutableStateOf(uiState.cloudUserName) }
    var waiterName by rememberSaveable { mutableStateOf("") }
    var invitePayloadInput by rememberSaveable { mutableStateOf("") }
    var onlineActionLoading by rememberSaveable { mutableStateOf(false) }
    var onlineStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var onlineStatusSuccess by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var webAdminPayload by rememberSaveable { mutableStateOf("") }
    var pendingPriceListExport by remember { mutableStateOf<ExportPayload?>(null) }
    var pendingBackupExport by remember { mutableStateOf<ExportPayload?>(null) }
    var pendingImageProductId by remember { mutableStateOf<Long?>(null) }
    var backupLoading by rememberSaveable { mutableStateOf(false) }
    var backupStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var backupStatusSuccess by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    val priceListExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val export = pendingPriceListExport
        pendingPriceListExport = null
        if (uri == null || export == null) return@rememberLauncherForActivityResult
        onExportResult(writeTextDocument(context, uri, export.content), export.filename)
    }
    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val export = pendingBackupExport
        pendingBackupExport = null
        if (uri == null || export == null) return@rememberLauncherForActivityResult
        onExportResult(writeTextDocument(context, uri, export.content), export.filename)
    }
    val priceListImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            priceListLoading = true
            priceListStatusSuccess = null
            priceListStatusMessage = "Učitavam CSV cjenik..."
            val content = readText(context, uri)
            if (content == null) {
                priceListLoading = false
                priceListStatusSuccess = false
                priceListStatusMessage = "Ne mogu pročitati odabrani CSV."
                return@launch
            }

            val success = onImportPriceList(content, priceListLabel)
            priceListLoading = false
            priceListStatusSuccess = success
            priceListStatusMessage = if (success) {
                "Cjenik je importan i odmah aktiviran."
            } else {
                "Import cjenika nije uspio. Provjeri format CSV-a."
            }
        }
    }
    val productImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        val productId = pendingImageProductId
        pendingImageProductId = null
        if (uri == null || productId == null) return@rememberLauncherForActivityResult
        scope.launch {
            val imageDataUrl = buildProductImageDataUrl(context, uri)
            if (imageDataUrl != null) {
                onUpdateProductImage(productId, imageDataUrl)
            } else {
                onUpdateProductImage(productId, "")
            }
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            backupLoading = true
            backupStatusSuccess = null
            backupStatusMessage = "Učitavam backup..."
            val content = readText(context, uri)
            if (content == null) {
                backupLoading = false
                backupStatusSuccess = false
                backupStatusMessage = "Ne mogu pročitati backup datoteku."
                return@launch
            }

            val success = onRestoreBackup(content)
            backupLoading = false
            backupStatusSuccess = success
            backupStatusMessage = if (success) {
                "Backup je vraćen u lokalnu bazu."
            } else {
                "Restore backupa nije uspio."
            }
        }
    }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val scannedPayload = result.contents
        when {
            scannedPayload.isNullOrBlank() -> {
                onlineStatusSuccess = false
                onlineStatusMessage = "QR nije očitan. Pokušaj ponovno ili zalijepi payload ručno."
            }

            waiterName.isBlank() -> {
                invitePayloadInput = scannedPayload
                onlineStatusSuccess = false
                onlineStatusMessage = "QR je očitan. Upiši ime konobara pa klikni Spoji waitera."
            }

            else -> {
                invitePayloadInput = scannedPayload
                scope.launch {
                    onlineActionLoading = true
                    onlineStatusSuccess = null
                    onlineStatusMessage = "QR je očitan. Spajam konobara..."
                    val success = onJoinCafeAsWaiter(scannedPayload, waiterName)
                    onlineActionLoading = false
                    onlineStatusSuccess = success
                    onlineStatusMessage = if (success) {
                        "Konobar je uspješno spojen na kafić."
                    } else {
                        "QR je očitan, ali spajanje nije uspjelo. Provjeri Firebase pravila i internet."
                    }
                }
            }
        }
    }
    val isAdmin = uiState.cloudUserRole == "admin"
    val isWaiter = uiState.cloudUserRole == "waiter"
    val canManageInventory = !isWaiter
    val availableSections = remember(isWaiter, canManageInventory) {
        buildList {
            add(SettingsSection.Online)
            if (!isWaiter) {
                add(SettingsSection.PriceLists)
                add(SettingsSection.Products)
                add(SettingsSection.Procurement)
                add(SettingsSection.Backup)
            }
            if (canManageInventory) {
                add(SettingsSection.Inventory)
            }
        }
    }
    var selectedSectionKey by rememberSaveable(isWaiter) {
        mutableStateOf(availableSections.first().name)
    }
    var showProcurementDialog by rememberSaveable { mutableStateOf(false) }
    val selectedSection = availableSections.firstOrNull { section ->
        section.name == selectedSectionKey
    } ?: availableSections.first()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "Odaberi sekciju koju želiš uređivati.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(availableSections, key = { it.name }) { section ->
                            FilterChip(
                                selected = selectedSection == section,
                                onClick = { selectedSectionKey = section.name },
                                label = { Text(section.label) },
                            )
                        }
                    }
                    if (uiState.cloudUserRole.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        FilledTonalButton(
                            onClick = onRefreshCloudCatalog,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Osvježi cjenik iz clouda")
                        }
                        Text(
                            text = "Koristi ovo nakon izmjene ili importa cjenika na web adminu ako se mobitel ne osvježi odmah.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (selectedSection == SettingsSection.Online) item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Online povezivanje",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    when {
                        isAdmin -> {
                            Text(
                                text = "Admin za ${uiState.cloudCafeName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (uiState.waiterInvitePayload.isNotBlank()) {
                                val qrBitmap = remember(uiState.waiterInvitePayload) {
                                    createQrBitmap(uiState.waiterInvitePayload, 720)
                                }
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Waiter QR",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp),
                                )
                            }
                            FilledTonalButton(
                                enabled = !onlineActionLoading,
                                onClick = {
                                    scope.launch {
                                        onlineActionLoading = true
                                        onlineStatusMessage = null
                                        val success = onRefreshWaiterInvite()
                                        onlineActionLoading = false
                                        onlineStatusSuccess = success
                                        onlineStatusMessage = if (success) {
                                            "Novi QR je generiran."
                                        } else {
                                            "Ne mogu generirati novi QR. Provjeri internet i Firebase pravila."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (onlineActionLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text("Generiraj novi waiter QR")
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                text = "Web admin za kompjuter",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                text = "Generiraj kod i zalijepi ga u Siply web admin na kompjuteru.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                enabled = !onlineActionLoading,
                                onClick = {
                                    scope.launch {
                                        onlineActionLoading = true
                                        onlineStatusMessage = null
                                        val payload = onRefreshWebAdminInvite()
                                        onlineActionLoading = false
                                        onlineStatusSuccess = payload != null
                                        onlineStatusMessage = if (payload != null) {
                                            webAdminPayload = payload
                                            "Web admin kod je generiran."
                                        } else {
                                            "Ne mogu generirati web admin kod. Provjeri internet i Firebase pravila."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                if (onlineActionLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text("Generiraj web admin kod")
                            }
                            if (webAdminPayload.isNotBlank()) {
                                OutlinedTextField(
                                    value = webAdminPayload,
                                    onValueChange = { webAdminPayload = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Web admin kod") },
                                    minLines = 4,
                                )
                            }
                            onlineStatusMessage?.let { message ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (onlineStatusSuccess == true) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.errorContainer
                                        },
                                    ),
                                ) {
                                    Text(
                                        text = message,
                                        modifier = Modifier.padding(14.dp),
                                        color = if (onlineStatusSuccess == true) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        },
                                    )
                                }
                            }
                        }

                        isWaiter -> {
                            Text(
                                text = "Spojen kao ${uiState.cloudUserName} na ${uiState.cloudCafeName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            EmptyCard("Waiter uređaj vidi samo artikle i račun.")
                        }

                        else -> {
                            Text(
                                text = "Firebase je već spojen u aplikaciju. Admin kreira kafić, waiter se spaja QR kodom.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Ako klik ne prođe, najčešće treba uključiti Anonymous Auth i napraviti Firestore bazu u Firebaseu.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = cafeName,
                                onValueChange = { cafeName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Naziv kafića") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = adminName,
                                onValueChange = { adminName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Ime admina") },
                                singleLine = true,
                            )
                            Button(
                                enabled = !onlineActionLoading,
                                onClick = {
                                    scope.launch {
                                        onlineActionLoading = true
                                        onlineStatusMessage = null
                                        val success = onCreateOnlineCafe(
                                            cafeName,
                                            adminName,
                                        )
                                        onlineActionLoading = false
                                        onlineStatusSuccess = success
                                        onlineStatusMessage = if (success) {
                                            "Online kafić je kreiran. QR je spreman za waitere."
                                        } else {
                                            "Kreiranje nije uspjelo. U Firebaseu uključi Anonymous Auth, napravi Firestore bazu i provjeri pravila."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                if (onlineActionLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text("Kreiraj online kafić")
                            }

                            onlineStatusMessage?.let { message ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (onlineStatusSuccess == true) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.errorContainer
                                        },
                                    ),
                                ) {
                                    Text(
                                        text = message,
                                        modifier = Modifier.padding(14.dp),
                                        color = if (onlineStatusSuccess == true) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        },
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            OutlinedTextField(
                                value = waiterName,
                                onValueChange = { waiterName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Ime konobara") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = invitePayloadInput,
                                onValueChange = { invitePayloadInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("QR payload") },
                                minLines = 3,
                            )
                            FilledTonalButton(
                                enabled = !onlineActionLoading,
                                onClick = {
                                    scanLauncher.launch(
                                        ScanOptions()
                                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                            .setPrompt("Skeniraj waiter QR")
                                            .setBeepEnabled(false)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Skeniraj waiter QR")
                            }
                            Button(
                                enabled = !onlineActionLoading,
                                onClick = {
                                    scope.launch {
                                        onlineActionLoading = true
                                        onlineStatusMessage = null
                                        val success = onJoinCafeAsWaiter(invitePayloadInput, waiterName)
                                        onlineActionLoading = false
                                        onlineStatusSuccess = success
                                        onlineStatusMessage = if (success) {
                                            "Konobar je uspješno spojen na kafić."
                                        } else {
                                            "Spajanje nije uspjelo. Provjeri QR kod, internet i Firebase postavke."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                if (onlineActionLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text("Spoji waitera")
                            }
                        }
                    }
                }
            }
        }

        if (selectedSection == SettingsSection.PriceLists && !isWaiter) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "Cjenici",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "Exportaj aktivni cjenik u Excel-friendly CSV, importaj novi i aktiviraj stare verzije po datumu.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = priceListLabel,
                            onValueChange = { priceListLabel = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Naziv ili datum cjenika") },
                            singleLine = true,
                            placeholder = { Text("npr. 14.04.2026") },
                        )
                        Button(
                            enabled = !priceListLoading,
                            onClick = {
                                scope.launch {
                                    priceListLoading = true
                                    priceListStatusSuccess = null
                                    priceListStatusMessage = "Spremam trenutni cjenik..."
                                    val success = onSaveCurrentPriceList(priceListLabel)
                                    priceListLoading = false
                                    priceListStatusSuccess = success
                                    priceListStatusMessage = if (success) {
                                        "Trenutni cjenik je spremljen kao verzija."
                                    } else {
                                        "Spremanje trenutnog cjenika nije uspjelo."
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            if (priceListLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Text("Spremi trenutni cjenik")
                        }
                        FilledTonalButton(
                            enabled = !priceListLoading,
                            onClick = {
                                scope.launch {
                                    val export = buildPriceListExportPayload()
                                    if (export != null) {
                                        pendingPriceListExport = export
                                        priceListExportLauncher.launch(export.filename)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Export aktivnog cjenika")
                        }
                        FilledTonalButton(
                            enabled = !priceListLoading,
                            onClick = {
                                priceListImportLauncher.launch(
                                    arrayOf(
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "text/plain",
                                        "*/*",
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Import novog CSV cjenika")
                        }
                        Text(
                            text = "Format: Kategorija;Artikl;Cijena EUR;Emoji;Boja HEX;Redoslijed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        priceListStatusMessage?.let { message ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (priceListStatusSuccess == false) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                ),
                            ) {
                                Text(
                                    text = message,
                                    modifier = Modifier.padding(14.dp),
                                    color = if (priceListStatusSuccess == false) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Text(
                            text = "Spremljene verzije",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        if (uiState.priceListVersions.isEmpty()) {
                            EmptyCard("Još nema spremljenih cjenika. Prvo spremi trenutni ili importaj CSV.")
                        } else {
                            uiState.priceListVersions.forEach { version ->
                                PriceListVersionCard(
                                    version = version,
                                    loading = priceListLoading,
                                    onActivate = {
                                        scope.launch {
                                            priceListLoading = true
                                            priceListStatusSuccess = null
                                            priceListStatusMessage = "Aktiviram cjenik ${version.effectiveDateLabel}..."
                                            val success = onActivatePriceList(version.id)
                                            priceListLoading = false
                                            priceListStatusSuccess = success
                                            priceListStatusMessage = if (success) {
                                                "Cjenik ${version.effectiveDateLabel} je sada aktivan."
                                            } else {
                                                "Aktivacija odabranog cjenika nije uspjela."
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectedSection == SettingsSection.Products && !isWaiter) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "Novi artikl",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "Dodaj artikl u postojeću kategoriju.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Kategorija",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(uiState.categories, key = { it.id }) { category ->
                                    FilterChip(
                                        selected = uiState.selectedCategoryId == category.id,
                                        onClick = { onSelectCategory(category.id) },
                                        label = { Text(category.name) },
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Naziv artikla") },
                            singleLine = true,
                        )

                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Cijena u €") },
                            placeholder = { Text("npr. 2,50") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )

                        Button(
                            onClick = {
                                scope.launch {
                                    val success = onAddCatalogProduct(
                                        uiState.selectedCategoryId,
                                        name,
                                        price,
                                    )
                                    if (success) {
                                        name = ""
                                        price = ""
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text(
                                text = "Dodaj artikl",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
        }

        if (selectedSection == SettingsSection.Inventory && canManageInventory) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "Skladište",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = if (isAdmin) {
                                "Pregled stanja robe. Nabavu vodiš kroz poseban ulaz robe, a računi admina i konobara automatski skidaju stanje."
                            } else {
                                "Pregled lokalnog stanja. Lokalni računi automatski skidaju količinu sa skladišta."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StatCard("Artikala", uiState.inventoryItems.size.toString(), Modifier.weight(1f))
                            StatCard("Vrijednost", uiState.inventoryTotalValueLabel, Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StatCard(
                                "Nisko",
                                uiState.inventoryItems.count { item -> item.isLowStock || item.isOutOfStock }.toString(),
                                Modifier.weight(1f),
                            )
                            StatCard("Cjenik", uiState.inventoryPriceListLabel, Modifier.weight(1f))
                        }
                        Text(
                            text = "Vrijednost robe računa se po cjeniku ${uiState.inventoryPriceListLabel}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Kategorija",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(uiState.categories, key = { it.id }) { category ->
                                    FilterChip(
                                        selected = uiState.selectedCategoryId == category.id,
                                        onClick = { onSelectCategory(category.id) },
                                        label = { Text(category.name) },
                                    )
                                }
                            }
                        }

                        if (uiState.inventoryItems.isEmpty()) {
                            EmptyCard("Nema artikala u odabranoj kategoriji.")
                        } else {
                            uiState.inventoryItems.forEach { item ->
                                InventoryOverviewCard(item = item)
                            }
                        }
                    }
                }
            }
        }

        if (selectedSection == SettingsSection.Procurement && !isWaiter) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "Nabava",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "Dodaj robu ulazom, bez ručnog prepisivanja stanja. Nakon spremanja stanje skladišta se automatski poveća.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StatCard("Artikala", uiState.inventoryItems.size.toString(), Modifier.weight(1f))
                            StatCard("Vrijednost", uiState.inventoryTotalValueLabel, Modifier.weight(1f))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Kategorija",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(uiState.categories, key = { it.id }) { category ->
                                    FilterChip(
                                        selected = uiState.selectedCategoryId == category.id,
                                        onClick = { onSelectCategory(category.id) },
                                        label = { Text(category.name) },
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = { showProcurementDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text(
                                text = "Ulaz robe",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Zadnji ulazi robe",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    if (uiState.procurementHistory.isEmpty()) {
                        EmptyCard("Još nema spremljene nabave.")
                    } else {
                        uiState.procurementHistory.forEach { entry ->
                            ProcurementHistoryCard(entry = entry)
                        }
                    }
                }
            }
        }

        if (selectedSection == SettingsSection.Backup && !isWaiter) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "Backup i restore",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "Exportaj cijelu lokalnu bazu u JSON ili vrati stanje iz spremljenog backupa.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilledTonalButton(
                                enabled = !backupLoading,
                                onClick = {
                                    scope.launch {
                                        backupLoading = true
                                        backupStatusSuccess = null
                                        backupStatusMessage = "Pripremam backup..."
                                        val export = buildBackupPayload()
                                        backupLoading = false
                                        if (export != null) {
                                            pendingBackupExport = export
                                            backupStatusSuccess = true
                                            backupStatusMessage = "Backup je spreman za spremanje."
                                            backupExportLauncher.launch(export.filename)
                                        } else {
                                            backupStatusSuccess = false
                                            backupStatusMessage = "Backup nije dostupan."
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Export backup")
                            }
                            FilledTonalButton(
                                enabled = !backupLoading,
                                onClick = {
                                    backupImportLauncher.launch(arrayOf("application/json", "text/plain"))
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Restore backup")
                            }
                        }
                        backupStatusMessage?.let { message ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (backupStatusSuccess == false) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                ),
                            ) {
                                Text(
                                    text = message,
                                    modifier = Modifier.padding(14.dp),
                                    color = if (backupStatusSuccess == false) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectedSection == SettingsSection.Products && !isWaiter) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Artikli u odabranoj kategoriji",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    if (uiState.products.isEmpty()) {
                        EmptyCard("Nema artikala u odabranoj kategoriji.")
                    } else {
                        uiState.products.forEach { product ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ProductThumbnail(
                                        imageDataUrl = product.imageDataUrl,
                                        emoji = product.emoji,
                                        accentColor = product.accentColor,
                                        modifier = Modifier.size(58.dp),
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Text(
                                            text = product.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = product.priceLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            pendingImageProductId = product.id
                                            productImageLauncher.launch("image/*")
                                        },
                                    ) {
                                        Text("Promijeni sliku")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProcurementDialog) {
        ProcurementInputDialog(
            inventoryItems = uiState.inventoryItems,
            categories = uiState.categories,
            selectedCategoryId = uiState.selectedCategoryId,
            inventoryTotalValueLabel = uiState.inventoryTotalValueLabel,
            inventoryPriceListLabel = uiState.inventoryPriceListLabel,
            onDismiss = { showProcurementDialog = false },
            onSelectCategory = onSelectCategory,
            onSaveEntries = onAddProcurementEntries,
        )
    }
}

@Composable
private fun ReceiptTab(
    uiState: PosUiState,
    onAdjustQuantity: (Long, Int) -> Unit,
    onSaveReceipt: (String) -> Unit,
    onBackToProducts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var markHouseAccount by rememberSaveable { mutableStateOf(false) }
    var markMusic by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.cartItemsCount) {
        if (uiState.cartItemsCount == 0) {
            markHouseAccount = false
            markMusic = false
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Aktivni račun",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = uiState.nextReceiptNumber,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("Ukupno", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = uiState.subtotalLabel,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
        }

        if (uiState.cartItems.isEmpty()) {
            item {
                EmptyCard("Račun je prazan. Dodaj pića u tabu s artiklima.") {
                    FilledTonalButton(onClick = onBackToProducts) {
                        Text("Idi na artikle")
                    }
                }
            }
        } else {
            items(uiState.cartItems, key = { it.productId }) { item ->
                ReceiptLineRow(item = item, onAdjustQuantity = onAdjustQuantity)
            }
            item {
                ReceiptFlagsCard(
                    markHouseAccount = markHouseAccount,
                    markMusic = markMusic,
                    onHouseAccountChange = { markHouseAccount = it },
                    onMusicChange = { markMusic = it },
                )
            }
            item {
                Button(
                    onClick = {
                        onSaveReceipt(
                            buildReceiptFlagNote(
                                markHouseAccount = markHouseAccount,
                                markMusic = markMusic,
                            ),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Spremi račun",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptFlagsCard(
    markHouseAccount: Boolean,
    markMusic: Boolean,
    onHouseAccountChange: (Boolean) -> Unit,
    onMusicChange: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Oznaka računa",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            ReceiptFlagRow(
                checked = markHouseAccount,
                label = "Na račun kuće",
                onCheckedChange = onHouseAccountChange,
            )
            ReceiptFlagRow(
                checked = markMusic,
                label = "Muzika",
                onCheckedChange = onMusicChange,
            )
        }
    }
}

@Composable
private fun ReceiptFlagRow(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        onClick = { onCheckedChange(!checked) },
        colors = CardDefaults.cardColors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun buildReceiptFlagNote(
    markHouseAccount: Boolean,
    markMusic: Boolean,
): String = buildList {
    if (markHouseAccount) add("Na račun kuće")
    if (markMusic) add("Muzika")
}.joinToString(" • ")

@Composable
private fun HistoryTab(
    uiState: PosUiState,
    onExportSummary: () -> Unit,
    onExportAll: () -> Unit,
    onExportResult: (Boolean, String) -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val fallbackDate = latestHistoryDate(uiState)
    var selectedStartDateKey by rememberSaveable { mutableStateOf(fallbackDate.toString()) }
    var selectedEndDateKey by rememberSaveable { mutableStateOf(fallbackDate.toString()) }
    var visibleMonthKey by rememberSaveable { mutableStateOf(YearMonth.from(fallbackDate).toString()) }
    var showCalendarDialog by rememberSaveable { mutableStateOf(false) }
    var pendingPdf by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val export = pendingPdf
        pendingPdf = null
        if (uri == null || export == null) return@rememberLauncherForActivityResult
        onExportResult(writeBinaryDocument(context, uri, export.second), export.first)
    }

    val selectedStartDate = runCatching { LocalDate.parse(selectedStartDateKey) }.getOrElse { fallbackDate }
    val selectedEndDate = runCatching { LocalDate.parse(selectedEndDateKey) }.getOrElse { fallbackDate }
    val normalizedHistoryRange = remember(selectedStartDate, selectedEndDate) {
        normalizeDateRange(selectedStartDate, selectedEndDate)
    }
    val visibleMonth = runCatching { YearMonth.parse(visibleMonthKey) }
        .getOrElse { YearMonth.from(normalizedHistoryRange.second) }
    val allDayTotals = remember(uiState.receiptHistory, uiState.cloudReceiptHistory) {
        buildDayTotals(uiState)
    }
    val filteredCloudHistory = remember(uiState.cloudReceiptHistory, normalizedHistoryRange) {
        uiState.cloudReceiptHistory.filter { receipt ->
            isDateInRange(
                date = historyDate(receipt.createdAtMillis),
                start = normalizedHistoryRange.first,
                end = normalizedHistoryRange.second,
            )
        }
    }
    val filteredLocalHistory = remember(uiState.receiptHistory, normalizedHistoryRange) {
        uiState.receiptHistory.filter { receipt ->
            isDateInRange(
                date = historyDate(receipt.createdAtMillis),
                start = normalizedHistoryRange.first,
                end = normalizedHistoryRange.second,
            )
        }
    }
    val selectedTotalCents = filteredLocalHistory.sumOf { it.totalCents } + filteredCloudHistory.sumOf { it.totalCents }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Povijest računa",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "Pregledaj jedan dan ili cijeli raspon, exportaj svu prodaju i po potrebi obriši sve račune.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { showCalendarDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Kalendar • ${formatDateRangeLabel(normalizedHistoryRange.first, normalizedHistoryRange.second)}")
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            FilledTonalButton(onClick = onExportAll) {
                                Icon(Icons.Rounded.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sve CSV")
                            }
                        }
                        item {
                            FilledTonalButton(onClick = onExportSummary) {
                                Icon(Icons.Rounded.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sažetak CSV")
                            }
                        }
                        item {
                            FilledTonalButton(
                                onClick = {
                                    val filename = buildPdfFilename(
                                        normalizedHistoryRange.first,
                                        normalizedHistoryRange.second,
                                    )
                                    pendingPdf = filename to buildDailyReportPdfBytes(
                                        uiState = uiState,
                                        rangeStart = normalizedHistoryRange.first,
                                        rangeEnd = normalizedHistoryRange.second,
                                        localHistory = filteredLocalHistory,
                                        cloudHistory = filteredCloudHistory,
                                        totalCents = selectedTotalCents,
                                    )
                                    pdfLauncher.launch(filename)
                                },
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("PDF obračun")
                            }
                        }
                        item {
                            FilledTonalButton(onClick = onDelete) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Obriši zadnji")
                            }
                        }
                        item {
                            FilledTonalButton(onClick = onReset) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reset dan")
                            }
                        }
                        item {
                            FilledTonalButton(onClick = onClearAll) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Obriši sve")
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Odabrani period ${formatDateRangeLabel(normalizedHistoryRange.first, normalizedHistoryRange.second)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatCard("Ukupno", formatCurrencyCompact(selectedTotalCents), Modifier.weight(1f))
                        StatCard("Online", filteredCloudHistory.size.toString(), Modifier.weight(1f))
                        StatCard("Lokalno", filteredLocalHistory.size.toString(), Modifier.weight(1f))
                    }
                }
            }
        }

        if (uiState.cloudReceiptHistory.isNotEmpty()) {
            item {
                Text(
                    text = "Online računi od osoblja",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            if (filteredCloudHistory.isEmpty()) {
                item { EmptyCard("Nema online računa za odabrani period.") }
            } else {
                items(filteredCloudHistory, key = { it.id }) { receipt ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = receipt.receiptNumber,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Text(
                                    text = "${receipt.waiterName} • ${receipt.createdAtLabel}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = receipt.totalLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = receipt.itemsSummary.ifBlank { "Bez stavki" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (receipt.note.isNotBlank()) {
                            NoteCard(text = receipt.note)
                        }
                    }
                }
            }
            }
        }

        item {
            Text(
                text = "Lokalni računi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        if (filteredLocalHistory.isEmpty()) {
            item { EmptyCard("Nema lokalnih računa za odabrani period.") }
        } else {
            items(filteredLocalHistory, key = { it.id }) { receipt ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = receipt.receiptNumber,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Text(
                                    text = receipt.createdAtLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = receipt.totalLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = receipt.itemsCountLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (receipt.note.isNotBlank()) {
                            NoteCard(text = receipt.note)
                        }
                    }
                }
            }
        }
    }

    if (showCalendarDialog) {
        CalendarDialog(
            title = "Odaberi period za povijest",
            visibleMonth = visibleMonth,
            selectedStartDate = normalizedHistoryRange.first,
            selectedEndDate = normalizedHistoryRange.second,
            dayTotals = allDayTotals,
            onDismiss = { showCalendarDialog = false },
            onPreviousMonth = {
                visibleMonthKey = visibleMonth.minusMonths(1).toString()
            },
            onNextMonth = {
                visibleMonthKey = visibleMonth.plusMonths(1).toString()
            },
            onApplyDates = { start, end ->
                selectedStartDateKey = start.toString()
                selectedEndDateKey = end.toString()
                visibleMonthKey = YearMonth.from(end).toString()
                showCalendarDialog = false
            },
        )
    }
}

@Composable
private fun CalendarSection(
    visibleMonth: YearMonth,
    selectedStartDate: LocalDate,
    selectedEndDate: LocalDate,
    dayTotals: Map<LocalDate, Int>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    showContainer: Boolean = true,
    compactMode: Boolean = false,
) {
    val normalizedRange = remember(selectedStartDate, selectedEndDate) {
        normalizeDateRange(selectedStartDate, selectedEndDate)
    }
    val cells = remember(visibleMonth) { buildCalendarCells(visibleMonth) }
    val contentPadding = if (compactMode) 0.dp else 18.dp
    val weekSpacing = if (compactMode) 6.dp else 8.dp
    val dayLabelStyle = if (compactMode) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.labelMedium
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(if (compactMode) 10.dp else 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onPreviousMonth,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text("<", fontWeight = FontWeight.ExtraBold)
                }
                Text(
                    text = visibleMonth.format(calendarMonthFormatter()),
                    style = if (compactMode) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FilledTonalButton(
                    onClick = onNextMonth,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text(">", fontWeight = FontWeight.ExtraBold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(weekSpacing),
            ) {
                calendarDayLabels().forEach { label ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = dayLabelStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            cells.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(weekSpacing),
                ) {
                    week.forEach { date ->
                        CalendarDayCell(
                            date = date,
                            totalCents = date?.let { dayTotals[it] } ?: 0,
                            isBoundary = date != null && (date == normalizedRange.first || date == normalizedRange.second),
                            isInRange = date != null && isDateInRange(
                                date = date,
                                start = normalizedRange.first,
                                end = normalizedRange.second,
                            ),
                            onClick = {
                                if (date != null) {
                                    onSelectDate(date)
                                }
                            },
                            compactMode = compactMode,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    if (showContainer) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
private fun CalendarDialog(
    title: String,
    visibleMonth: YearMonth,
    selectedStartDate: LocalDate,
    selectedEndDate: LocalDate,
    dayTotals: Map<LocalDate, Int>,
    onDismiss: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onApplyDates: (LocalDate, LocalDate) -> Unit,
) {
    val initialRange = remember(selectedStartDate, selectedEndDate) {
        normalizeDateRange(selectedStartDate, selectedEndDate)
    }
    var tempStartDate by remember(selectedStartDate, selectedEndDate) {
        mutableStateOf(initialRange.first)
    }
    var tempEndDate by remember(selectedStartDate, selectedEndDate) {
        mutableStateOf(initialRange.second)
    }
    var rangeMode by remember(selectedStartDate, selectedEndDate) {
        mutableStateOf(selectedStartDate != selectedEndDate)
    }
    var selectingStart by remember(selectedStartDate, selectedEndDate) {
        mutableStateOf(selectedStartDate == selectedEndDate)
    }
    val selectedRange = remember(tempStartDate, tempEndDate) {
        normalizeDateRange(tempStartDate, tempEndDate)
    }
    val selectedTotal = remember(dayTotals, selectedRange) {
        dayTotals.entries.sumOf { entry ->
            if (isDateInRange(entry.key, selectedRange.first, selectedRange.second)) entry.value else 0
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilterChip(
                        selected = !rangeMode,
                        onClick = {
                            rangeMode = false
                            tempEndDate = tempStartDate
                            selectingStart = true
                        },
                        label = { Text("Jedan dan") },
                    )
                    FilterChip(
                        selected = rangeMode,
                        onClick = {
                            rangeMode = true
                            tempEndDate = selectedRange.second
                            selectingStart = false
                        },
                        label = { Text("Raspon") },
                    )
                }
                if (rangeMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilterChip(
                            selected = selectingStart,
                            onClick = { selectingStart = true },
                            label = { Text("Početak") },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = !selectingStart,
                            onClick = { selectingStart = false },
                            label = { Text("Kraj") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = if (selectingStart) {
                            "Dodirni dan koji želiš postaviti kao početak perioda."
                        } else {
                            "Dodirni dan koji želiš postaviti kao kraj perioda."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "Odabrano ${formatDateRangeLabel(selectedRange.first, selectedRange.second)} • ${formatCurrencyCompact(selectedTotal)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CalendarSection(
                    visibleMonth = visibleMonth,
                    selectedStartDate = selectedRange.first,
                    selectedEndDate = selectedRange.second,
                    dayTotals = dayTotals,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onSelectDate = { day ->
                        if (!rangeMode) {
                            tempStartDate = day
                            tempEndDate = day
                        } else if (selectingStart) {
                            tempStartDate = day
                            if (day > tempEndDate) {
                                tempEndDate = day
                            }
                            selectingStart = false
                        } else {
                            tempEndDate = day
                            if (day < tempStartDate) {
                                tempStartDate = day
                            }
                        }
                    },
                    showContainer = false,
                    compactMode = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Odustani")
                    }
                    Button(
                        onClick = {
                            onApplyDates(selectedRange.first, selectedRange.second)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Primijeni")
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate?,
    totalCents: Int,
    isBoundary: Boolean,
    isInRange: Boolean,
    onClick: () -> Unit,
    compactMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val cellHeight = if (compactMode) 64.dp else 84.dp
    if (date == null) {
        Box(
            modifier = modifier.height(cellHeight),
        )
        return
    }

    val containerColor = when {
        isBoundary -> MaterialTheme.colorScheme.primaryContainer
        isInRange -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        totalCents > 0 -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isBoundary -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        onClick = onClick,
        modifier = modifier.height(cellHeight),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (compactMode) 6.dp else 8.dp,
                    vertical = if (compactMode) 8.dp else 10.dp,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = if (compactMode) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor,
            )
            if (totalCents > 0) {
                Text(
                    text = formatCalendarCellTotal(totalCents),
                    style = if (compactMode) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isBoundary) {
                        contentColor.copy(alpha = 0.92f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = if (compactMode) "0" else "0 €",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isBoundary) {
                        contentColor.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StaffOverviewSection(
    uiState: PosUiState,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Ekipa i admin",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "Prodaja svih konobara i admina za odabrani period.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard("Promet", uiState.staffOverview.waiterTotalLabel, Modifier.weight(1f))
                StatCard("Računa", uiState.staffOverview.waiterReceiptsLabel, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard("Prodano", uiState.staffOverview.waiterItemsLabel, Modifier.weight(1f))
                StatCard("Ukupno", uiState.staffOverview.combinedTotalLabel, Modifier.weight(1f))
            }
            Text(
                text = "Ukupno ${uiState.staffOverview.combinedReceiptsLabel} računa za ${uiState.dashboardSelectedDateLabel}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ComparisonChartSection(
    title: String,
    items: List<ComparisonBarUi>,
    emptyText: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            if (items.isEmpty()) {
                EmptyCard(text = emptyText)
            } else {
                items.forEach { item ->
                    ComparisonBarRow(item)
                }
            }
        }
    }
}

@Composable
private fun ComparisonBarRow(
    item: ComparisonBarUi,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = item.supportingLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.valueLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (item.progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(item.progress.coerceIn(0.08f, 1f))
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun WaiterAnalyticsSection(
    items: List<WaiterAnalyticsUi>,
    emptyText: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Konobari detaljno",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            if (items.isEmpty()) {
                EmptyCard(text = emptyText)
            } else {
                items.forEach { item ->
                    WaiterAnalyticsCard(item)
                }
            }
        }
    }
}

@Composable
private fun WaiterAnalyticsCard(
    item: WaiterAnalyticsUi,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "${item.receiptsCountLabel} računa • ${item.itemsCountLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = item.totalLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Text(
                text = "Prosjek računa ${item.averageTicketLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (item.topProducts.isEmpty()) {
                Text(
                    text = "Još nema prodanih artikala.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                item.topProducts.forEach { product ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = product.quantityLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = product.totalLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotedReceiptsSection(
    items: List<NotedReceiptUi>,
    totalLabel: String,
    emptyText: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "Računi s bilješkama",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "${items.size} računa",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = totalLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            if (items.isEmpty()) {
                EmptyCard(text = emptyText)
            } else {
                items.forEach { item ->
                    NotedReceiptCard(item = item)
                }
            }
        }
    }
}

@Composable
private fun NotedReceiptCard(
    item: NotedReceiptUi,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = item.receiptNumber,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "${item.waiterName} • ${item.createdAtLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = item.totalLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            NoteCard(text = item.note)
        }
    }
}

@Composable
private fun NoteCard(
    text: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PriceListVersionCard(
    version: PriceListVersionUi,
    loading: Boolean,
    onActivate: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = version.effectiveDateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = version.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (version.isActive) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text("Aktivan")
                    }
                }
            }
            Text(
                text = "${version.itemsCountLabel} • spremljeno ${version.createdAtLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!version.isActive) {
                FilledTonalButton(
                    enabled = !loading,
                    onClick = onActivate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Aktiviraj ovaj cjenik")
                }
            }
        }
    }
}

@Composable
private fun InventoryOverviewCard(
    item: InventoryUi,
) {
    val containerColor = when {
        item.isOutOfStock -> MaterialTheme.colorScheme.errorContainer
        item.isLowStock -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        item.isOutOfStock -> MaterialTheme.colorScheme.onErrorContainer
        item.isLowStock -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = item.emoji, fontSize = 24.sp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = contentColor,
                    )
                    Text(
                        text = "${item.quantityLabel} • ${item.unitPriceLabel} po kom",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.85f),
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.stockValueLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor,
                )
                Text(
                    text = item.updatedAtLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun ProcurementHistoryCard(
    entry: ProcurementHistoryUi,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = entry.productName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = entry.createdAtLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = entry.quantityLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (entry.note.isNotBlank()) {
                Text(
                    text = entry.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProcurementInputDialog(
    inventoryItems: List<InventoryUi>,
    categories: List<CategoryUi>,
    selectedCategoryId: Long?,
    inventoryTotalValueLabel: String,
    inventoryPriceListLabel: String,
    onDismiss: () -> Unit,
    onSelectCategory: (Long) -> Unit,
    onSaveEntries: suspend (Map<Long, String>, String) -> Boolean,
) {
    var draftQuantities by remember(inventoryItems) {
        mutableStateOf(
            inventoryItems.associate { item -> item.productId to "" },
        )
    }
    var note by rememberSaveable { mutableStateOf("") }
    var savingAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val changedQuantities = remember(draftQuantities) {
        draftQuantities.filterValues { value -> value.trim().isNotEmpty() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Ulaz robe",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "Upiši koliko robe ulazi u skladište. Vrijednost robe i dalje se računa po cjeniku $inventoryPriceListLabel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatCard("Artikala", inventoryItems.size.toString(), Modifier.weight(1f))
                    StatCard("Vrijednost", inventoryTotalValueLabel, Modifier.weight(1f))
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = { onSelectCategory(category.id) },
                            label = { Text(category.name) },
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Napomena") },
                    placeholder = { Text("npr. jutarnja dostava") },
                    singleLine = true,
                )

                if (inventoryItems.isEmpty()) {
                    EmptyCard("Nema artikala u ovoj kategoriji.")
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(inventoryItems, key = { it.productId }) { item ->
                            ProcurementInputRow(
                                item = item,
                                quantityInput = draftQuantities[item.productId].orEmpty(),
                                onQuantityChange = { value ->
                                    draftQuantities = draftQuantities.toMutableMap().apply {
                                        put(item.productId, value)
                                    }
                                },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Zatvori")
                    }
                    Button(
                        enabled = !savingAll && changedQuantities.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                savingAll = true
                                val success = onSaveEntries(changedQuantities, note)
                                savingAll = false
                                if (success) {
                                    note = ""
                                    draftQuantities = inventoryItems.associate { item -> item.productId to "" }
                                    onDismiss()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        if (savingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text("Spremi ulaz")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcurementInputRow(
    item: InventoryUi,
    quantityInput: String,
    onQuantityChange: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1.2f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = item.emoji, fontSize = 22.sp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "Stanje ${item.quantityLabel} • ${item.unitPriceLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = quantityInput,
                onValueChange = onQuantityChange,
                modifier = Modifier.weight(0.8f),
                label = { Text("Ulaz") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}

@Composable
private fun InventoryInputDialog(
    inventoryItems: List<InventoryUi>,
    categories: List<CategoryUi>,
    selectedCategoryId: Long?,
    inventoryTotalValueLabel: String,
    inventoryPriceListLabel: String,
    onDismiss: () -> Unit,
    onSelectCategory: (Long) -> Unit,
    onSaveQuantities: suspend (Map<Long, String>) -> Boolean,
) {
    var draftQuantities by remember(inventoryItems) {
        mutableStateOf(
            inventoryItems.associate { item ->
                item.productId to item.quantityUnits.toString()
            },
        )
    }
    var savingAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val changedQuantities = remember(draftQuantities, inventoryItems) {
        inventoryItems.mapNotNull { item ->
            val draft = draftQuantities[item.productId]?.trim() ?: item.quantityUnits.toString()
            if (draft != item.quantityUnits.toString()) {
                item.productId to draft
            } else {
                null
            }
        }.toMap()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Unos u skladište",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "Upiši količine za odabranu kategoriju. Vrijednost robe računa se po cjeniku $inventoryPriceListLabel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatCard("Artikala", inventoryItems.size.toString(), Modifier.weight(1f))
                    StatCard("Vrijednost", inventoryTotalValueLabel, Modifier.weight(1f))
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = { onSelectCategory(category.id) },
                            label = { Text(category.name) },
                        )
                    }
                }

                if (inventoryItems.isEmpty()) {
                    EmptyCard("Nema artikala u ovoj kategoriji.")
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Artikl",
                            modifier = Modifier.weight(1.25f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "Trenutno",
                            modifier = Modifier.weight(0.55f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "Vrijednost",
                            modifier = Modifier.weight(0.7f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "Novi unos",
                            modifier = Modifier.weight(0.9f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(inventoryItems, key = { it.productId }) { item ->
                            InventoryInputRow(
                                item = item,
                                quantityInput = draftQuantities[item.productId] ?: item.quantityUnits.toString(),
                                onQuantityChange = { value ->
                                    draftQuantities = draftQuantities.toMutableMap().apply {
                                        put(item.productId, value)
                                    }
                                },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Zatvori")
                    }
                    Button(
                        enabled = !savingAll && changedQuantities.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                savingAll = true
                                val success = onSaveQuantities(changedQuantities)
                                savingAll = false
                                if (success) {
                                    // state refresh from Room will repopulate current quantities
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        if (savingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            if (changedQuantities.isEmpty()) {
                                "Spremi sve"
                            } else {
                                "Spremi sve (${changedQuantities.size})"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryInputRow(
    item: InventoryUi,
    quantityInput: String,
    onQuantityChange: (String) -> Unit,
) {
    val containerColor = when {
        item.isOutOfStock -> MaterialTheme.colorScheme.errorContainer
        item.isLowStock -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        item.isOutOfStock -> MaterialTheme.colorScheme.onErrorContainer
        item.isLowStock -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1.25f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(text = item.emoji, fontSize = 22.sp)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = contentColor,
                        )
                        Text(
                            text = "${item.unitPriceLabel} po kom",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.8f),
                        )
                    }
                }
                Text(
                    text = item.quantityLabel,
                    modifier = Modifier.weight(0.55f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor,
                )
                Text(
                    text = item.stockValueLabel,
                    modifier = Modifier.weight(0.7f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor,
                    textAlign = TextAlign.End,
                )
                OutlinedTextField(
                    value = quantityInput,
                    onValueChange = onQuantityChange,
                    modifier = Modifier.weight(0.9f),
                    label = { Text("kom") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            Text(
                text = "Zadnje ažuriranje ${item.updatedAtLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun SalesBreakdownSection(
    title: String,
    items: List<DashboardSalesUi>,
    emptyText: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            if (items.isEmpty()) {
                EmptyCard(text = emptyText)
            } else {
                items.forEach { item ->
                    DashboardSalesRow(item)
                }
            }
        }
    }
}

@Composable
private fun DashboardSalesRow(
    item: DashboardSalesUi,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = item.quantityLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = item.totalLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun EmptyCard(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = text,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.invoke()
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun ProductCard(
    product: ProductUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(118.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(product.accentColor),
                                    Color(product.accentColor).copy(alpha = 0.58f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    ProductThumbnail(
                        imageDataUrl = product.imageDataUrl,
                        emoji = product.emoji,
                        accentColor = product.accentColor,
                        modifier = Modifier.fillMaxSize(),
                        imageShape = RoundedCornerShape(22.dp),
                        emojiSize = 42,
                    )
                }

                if (product.quantityInCart > 0) {
                    Badge(
                        modifier = Modifier.align(Alignment.TopEnd),
                        containerColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(product.quantityInCart.toString())
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = product.priceLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProductThumbnail(
    imageDataUrl: String,
    emoji: String,
    accentColor: Long,
    modifier: Modifier = Modifier,
    imageShape: RoundedCornerShape = RoundedCornerShape(16.dp),
    emojiSize: Int = 24,
) {
    val bitmap = remember(imageDataUrl) { decodeImageDataUrl(imageDataUrl) }
    Box(
        modifier = modifier
            .clip(imageShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(accentColor),
                        Color(accentColor).copy(alpha = 0.58f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(text = emoji, fontSize = emojiSize.sp)
        }
    }
}

@Composable
private fun ReceiptLineRow(
    item: CartLineUi,
    onAdjustQuantity: (Long, Int) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(item.accentColor).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = item.emoji, fontSize = 24.sp)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = item.unitPriceLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilledTonalIconButton(
                    onClick = { onAdjustQuantity(item.productId, -1) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Makni")
                }
                Text(
                    text = item.quantity.toString(),
                    modifier = Modifier.width(22.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )
                FilledTonalIconButton(
                    onClick = { onAdjustQuantity(item.productId, 1) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Dodaj")
                }
            }

            Text(
                text = item.lineTotalLabel,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

private fun tabTitle(tab: MainTab): String = when (tab) {
    MainTab.Dashboard -> "Dashboard"
    MainTab.Products -> "Artikli"
    MainTab.Receipt -> "Račun"
    MainTab.History -> "Povijest"
    MainTab.Settings -> "Settings"
}

private fun tabSubtitle(tab: MainTab, uiState: PosUiState): String = when (tab) {
    MainTab.Dashboard -> "${uiState.dashboardSelectedDateLabel} • ${uiState.dailyStats.totalLabel}"
    MainTab.Products -> "${uiState.products.size} artikala u kategoriji"
    MainTab.Receipt -> "${uiState.cartItemsCount} stavki • ${uiState.subtotalLabel}"
    MainTab.History -> "${uiState.receiptHistory.size} lokalnih računa"
    MainTab.Settings -> when (uiState.cloudUserRole) {
        "admin" -> "Admin za ${uiState.cloudCafeName}"
        "waiter" -> "Waiter: ${uiState.cloudUserName}"
        else -> "Online setup i katalog"
    }
}

private fun normalizeDateRange(
    start: LocalDate,
    end: LocalDate,
): Pair<LocalDate, LocalDate> = if (start <= end) {
    start to end
} else {
    end to start
}

private fun isDateInRange(
    date: LocalDate,
    start: LocalDate,
    end: LocalDate,
): Boolean {
    val normalized = normalizeDateRange(start, end)
    return !date.isBefore(normalized.first) && !date.isAfter(normalized.second)
}

private fun formatDateRangeLabel(
    start: LocalDate,
    end: LocalDate,
): String {
    val normalized = normalizeDateRange(start, end)
    val formatter = historyHeaderFormatter()
    return if (normalized.first == normalized.second) {
        normalized.first.format(formatter)
    } else {
        "${normalized.first.format(formatter)} - ${normalized.second.format(formatter)}"
    }
}

private fun latestHistoryDate(uiState: PosUiState): LocalDate {
    val allDates = buildList {
        addAll(uiState.receiptHistory.map { receipt -> historyDate(receipt.createdAtMillis) })
        addAll(uiState.cloudReceiptHistory.map { receipt -> historyDate(receipt.createdAtMillis) })
    }
    return allDates.maxOrNull() ?: LocalDate.now()
}

private fun buildDayTotals(uiState: PosUiState): Map<LocalDate, Int> {
    val totals = linkedMapOf<LocalDate, Int>()
    uiState.receiptHistory.forEach { receipt ->
        val day = historyDate(receipt.createdAtMillis)
        totals[day] = (totals[day] ?: 0) + receipt.totalCents
    }
    uiState.cloudReceiptHistory.forEach { receipt ->
        val day = historyDate(receipt.createdAtMillis)
        totals[day] = (totals[day] ?: 0) + receipt.totalCents
    }
    return totals
}

private fun buildCalendarCells(month: YearMonth): List<LocalDate?> {
    val firstDay = month.atDay(1)
    val leading = firstDay.dayOfWeek.value - 1
    val days = MutableList<LocalDate?>(leading) { null }
    repeat(month.lengthOfMonth()) { index ->
        days += month.atDay(index + 1)
    }
    val trailing = (7 - (days.size % 7)).takeIf { it < 7 } ?: 0
    repeat(trailing) {
        days += null
    }
    return days
}

private fun calendarDayLabels(): List<String> = listOf("Pon", "Uto", "Sri", "Čet", "Pet", "Sub", "Ned")

private fun calendarMonthFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("LLLL yyyy", Locale("hr", "HR"))

private fun historyHeaderFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale("hr", "HR"))

private fun historyDate(epochMs: Long): LocalDate =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()

private fun formatCalendarCellTotal(cents: Int): String {
    val euros = cents / 100.0
    val decimal = DecimalFormat("0.#")
    return when {
        euros >= 1000 -> "${decimal.format(euros / 1000.0)}k€"
        euros >= 100 -> "${DecimalFormat("0").format(euros)}€"
        else -> "${decimal.format(euros)}€"
    }
}

private fun formatCurrencyCompact(cents: Int): String = formatCalendarCellTotal(cents).replace("k€", "k €")

private fun buildPdfFilename(
    start: LocalDate,
    end: LocalDate,
): String {
    val normalized = normalizeDateRange(start, end)
    return if (normalized.first == normalized.second) {
        "siply_obracun_${normalized.first}.pdf"
    } else {
        "siply_obracun_${normalized.first}_${normalized.second}.pdf"
    }
}

private fun buildDailyReportPdfBytes(
    uiState: PosUiState,
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
    localHistory: List<ReceiptHistoryUi>,
    cloudHistory: List<CloudReceiptUi>,
    totalCents: Int,
): ByteArray {
    val document = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 40f
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = AndroidColor.BLACK
    }
    val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = AndroidColor.BLACK
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f
        color = AndroidColor.BLACK
    }
    val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f
        color = AndroidColor.DKGRAY
    }

    val waiterTotals = linkedMapOf<String, Pair<Int, Int>>()
    val adminName = if (uiState.cloudUserRole == "admin" && uiState.cloudUserName.isNotBlank()) {
        uiState.cloudUserName
    } else {
        "Admin"
    }
    if (localHistory.isNotEmpty()) {
        waiterTotals[adminName] = localHistory.size to localHistory.sumOf { receipt -> receipt.totalCents }
    }
    cloudHistory
        .groupBy { receipt -> receipt.waiterName.ifBlank { "Nepoznati konobar" } }
        .forEach { (waiterName, receipts) ->
            waiterTotals[waiterName] = receipts.size to receipts.sumOf { receipt -> receipt.totalCents }
        }

    var pageNumber = 1
    var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
    var canvas = page.canvas
    var y = margin

    fun newPage() {
        document.finishPage(page)
        pageNumber += 1
        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = page.canvas
        y = margin
    }

    fun ensureSpace(lines: Int = 1) {
        val needed = lines * 18f + 8f
        if (y + needed > pageHeight - margin) {
            newPage()
        }
    }

    fun drawLine(text: String, paint: Paint = bodyPaint, extraSpacing: Float = 18f) {
        ensureSpace()
        canvas.drawText(text, margin, y, paint)
        y += extraSpacing
    }

    drawLine("Siply dnevni obračun", titlePaint, 28f)
    drawLine("Period: ${formatDateRangeLabel(rangeStart, rangeEnd)}", bodyPaint)
    drawLine("Ukupno: ${formatCurrencyCompact(totalCents)}", bodyPaint)
    drawLine("Lokalni računi: ${localHistory.size}  |  Online računi: ${cloudHistory.size}", smallPaint, 24f)

    drawLine("Promet po osobi", sectionPaint, 22f)
    if (waiterTotals.isEmpty()) {
        drawLine("Nema prodaje za odabrani period.", smallPaint, 20f)
    } else {
        waiterTotals.entries
            .sortedByDescending { entry -> entry.value.second }
            .forEach { entry ->
                drawLine(
                    "${entry.key}: ${entry.value.first} računa • ${formatCurrency(entry.value.second)}",
                    bodyPaint,
                )
            }
        y += 6f
    }

    drawLine("Lokalni računi", sectionPaint, 22f)
    if (localHistory.isEmpty()) {
        drawLine("Nema lokalnih računa.", smallPaint, 20f)
    } else {
        localHistory.forEach { receipt ->
            drawLine(
                "${receipt.receiptNumber} • ${receipt.createdAtLabel} • ${receipt.totalLabel}",
                bodyPaint,
            )
            if (receipt.note.isNotBlank()) {
                drawLine("Bilješka: ${receipt.note}", smallPaint, 16f)
            }
        }
        y += 6f
    }

    drawLine("Online računi", sectionPaint, 22f)
    if (cloudHistory.isEmpty()) {
        drawLine("Nema online računa.", smallPaint, 20f)
    } else {
        cloudHistory.forEach { receipt ->
            drawLine(
                "${receipt.receiptNumber} • ${receipt.waiterName} • ${receipt.createdAtLabel} • ${receipt.totalLabel}",
                bodyPaint,
            )
            if (receipt.itemsSummary.isNotBlank()) {
                drawLine(receipt.itemsSummary, smallPaint, 16f)
            }
            if (receipt.note.isNotBlank()) {
                drawLine("Bilješka: ${receipt.note}", smallPaint, 16f)
            }
        }
    }

    document.finishPage(page)
    return java.io.ByteArrayOutputStream().use { output ->
        document.writeTo(output)
        document.close()
        output.toByteArray()
    }
}

private fun createQrBitmap(
    payload: String,
    size: Int,
): Bitmap {
    val bitMatrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    return bitmap
}

private fun writeTextDocument(
    context: Context,
    uri: Uri,
    content: String,
): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { output ->
        output.writer(Charsets.UTF_8).use { writer ->
            writer.write(content)
        }
    } ?: error("Nije moguće otvoriti datoteku.")
}.isSuccess

private fun buildProductImageDataUrl(
    context: Context,
    uri: Uri,
): String? = runCatching {
    val original = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input)
    } ?: return null
    val bitmap = scaleBitmapForProductImage(original)
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 76, output)
    val encoded = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    "data:image/jpeg;base64,$encoded"
}.getOrNull()

private fun scaleBitmapForProductImage(bitmap: Bitmap): Bitmap {
    val maxSide = 420
    val width = bitmap.width
    val height = bitmap.height
    val largestSide = maxOf(width, height)
    if (largestSide <= maxSide) {
        return bitmap
    }

    val ratio = maxSide.toFloat() / largestSide.toFloat()
    val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
    val targetHeight = (height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}

private fun decodeImageDataUrl(imageDataUrl: String): Bitmap? {
    if (imageDataUrl.isBlank()) {
        return null
    }
    val base64 = imageDataUrl.substringAfter("base64,", missingDelimiterValue = "")
    if (base64.isBlank()) {
        return null
    }
    return runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

private fun writeBinaryDocument(
    context: Context,
    uri: Uri,
    bytes: ByteArray,
): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { output ->
        output.write(bytes)
        output.flush()
    } ?: error("Nije moguće otvoriti datoteku.")
}.isSuccess

private fun readText(
    context: Context,
    uri: Uri,
): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input ->
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText()
        }
    } ?: error("Nije moguće otvoriti datoteku.")
}.getOrNull()

private fun defaultPriceListLabel(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale("hr", "HR")))
