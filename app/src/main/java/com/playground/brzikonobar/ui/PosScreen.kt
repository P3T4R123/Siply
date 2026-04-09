package com.playground.siply.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.playground.siply.data.ExportPayload
import com.playground.siply.data.LastReceiptInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private enum class MainTab {
    Dashboard,
    Products,
    Receipt,
    History,
    Settings,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosApp(
    uiState: PosUiState,
    messages: Flow<String>,
    onSelectCategory: (Long) -> Unit,
    onAddProduct: (Long) -> Unit,
    onAdjustQuantity: (Long, Int) -> Unit,
    onSaveReceipt: () -> Unit,
    onDeleteLastReceipt: () -> Unit,
    onResetDailyStats: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onAddCatalogProduct: suspend (Long?, String, String) -> Boolean,
    onCreateOnlineCafe: suspend (String, String) -> Boolean,
    onRefreshWaiterInvite: suspend () -> Boolean,
    onJoinCafeAsWaiter: suspend (String, String) -> Boolean,
    onExportResult: (Boolean, String) -> Unit,
    loadLastReceiptInfo: suspend () -> LastReceiptInfo?,
    buildExportPayload: suspend () -> ExportPayload?,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Products) }
    var pendingExport by remember { mutableStateOf<ExportPayload?>(null) }
    var pendingDelete by remember { mutableStateOf<LastReceiptInfo?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    val isWaiter = uiState.cloudUserRole == "waiter"

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val export = pendingExport
        pendingExport = null
        if (uri == null || export == null) return@rememberLauncherForActivityResult
        onExportResult(writeCsv(context, uri, export), export.filename)
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
                onExport = {
                    scope.launch {
                        val export = buildExportPayload()
                        if (export != null) {
                            pendingExport = export
                            exportLauncher.launch(export.filename)
                        }
                    }
                },
                onDelete = {
                    scope.launch { pendingDelete = loadLastReceiptInfo() }
                },
                onReset = { showResetDialog = true },
                modifier = Modifier.then(Modifier).padding(padding),
            )
            MainTab.Settings -> SettingsTab(
                uiState = uiState,
                onSelectCategory = onSelectCategory,
                onAddCatalogProduct = onAddCatalogProduct,
                onCreateOnlineCafe = onCreateOnlineCafe,
                onRefreshWaiterInvite = onRefreshWaiterInvite,
                onJoinCafeAsWaiter = onJoinCafeAsWaiter,
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
    modifier: Modifier = Modifier,
) {
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
                        text = "Dnevni dashboard",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = uiState.dailyStats.resetLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
            SalesBreakdownSection(
                title = "Najprodavaniji artikli osoblja",
                items = uiState.staffProductSales,
                emptyText = "Još nema online prodaje po artiklima.",
            )
        }

        item {
            SalesBreakdownSection(
                title = "Moja lokalna prodaja po kategorijama",
                items = uiState.categorySales,
                emptyText = "Još nema prodaje po kategorijama.",
            )
        }

        item {
            SalesBreakdownSection(
                title = "Moja lokalna prodaja po artiklima",
                items = uiState.productSales,
                emptyText = "Još nema prodaje po artiklima.",
            )
        }
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
    onCreateOnlineCafe: suspend (String, String) -> Boolean,
    onRefreshWaiterInvite: suspend () -> Boolean,
    onJoinCafeAsWaiter: suspend (String, String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var cafeName by rememberSaveable(uiState.cloudCafeName) { mutableStateOf(uiState.cloudCafeName) }
    var adminName by rememberSaveable(uiState.cloudUserName) { mutableStateOf(uiState.cloudUserName) }
    var waiterName by rememberSaveable { mutableStateOf("") }
    var invitePayloadInput by rememberSaveable { mutableStateOf("") }
    var onlineActionLoading by rememberSaveable { mutableStateOf(false) }
    var onlineStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var onlineStatusSuccess by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
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

        if (!isWaiter) {
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

        if (!isWaiter) {
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
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
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
                                    Text(text = product.emoji, fontSize = 24.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptTab(
    uiState: PosUiState,
    onAdjustQuantity: (Long, Int) -> Unit,
    onSaveReceipt: () -> Unit,
    onBackToProducts: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                Button(
                    onClick = onSaveReceipt,
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
private fun HistoryTab(
    uiState: PosUiState,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            FilledTonalButton(onClick = onExport) {
                                Icon(Icons.Rounded.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CSV")
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
                                Text("Reset")
                            }
                        }
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
            items(uiState.cloudReceiptHistory, key = { it.id }) { receipt ->
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

        if (uiState.receiptHistory.isEmpty()) {
            item { EmptyCard("Još nema spremljenih računa.") }
        } else {
            items(uiState.receiptHistory, key = { it.id }) { receipt ->
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
                    }
                }
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
                text = "Ekipa online",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "Prodaja svih spojenih konobara i zbirno stanje s tvojom blagajnom.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard("Osoblje", uiState.staffOverview.waiterTotalLabel, Modifier.weight(1f))
                StatCard("Računa", uiState.staffOverview.waiterReceiptsLabel, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard("Prodano", uiState.staffOverview.waiterItemsLabel, Modifier.weight(1f))
                StatCard("Sve skupa", uiState.staffOverview.combinedTotalLabel, Modifier.weight(1f))
            }
            Text(
                text = "Ukupno ${uiState.staffOverview.combinedReceiptsLabel} računa kad zbrojiš svoj uređaj i ekipu.",
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
                    Text(text = product.emoji, fontSize = 42.sp)
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
    MainTab.Dashboard -> if (uiState.waiterAnalytics.isEmpty()) {
        "${uiState.dailyStats.totalLabel} • ${uiState.dailyStats.receiptsCountLabel} računa"
    } else {
        "${uiState.staffOverview.combinedTotalLabel} • ${uiState.waiterAnalytics.size} konobara"
    }
    MainTab.Products -> "${uiState.products.size} artikala u kategoriji"
    MainTab.Receipt -> "${uiState.cartItemsCount} stavki • ${uiState.subtotalLabel}"
    MainTab.History -> "${uiState.receiptHistory.size} lokalnih računa"
    MainTab.Settings -> when (uiState.cloudUserRole) {
        "admin" -> "Admin za ${uiState.cloudCafeName}"
        "waiter" -> "Waiter: ${uiState.cloudUserName}"
        else -> "Online setup i katalog"
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

private fun writeCsv(
    context: Context,
    uri: Uri,
    export: ExportPayload,
): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { output ->
        output.writer(Charsets.UTF_8).use { writer ->
            writer.write(export.content)
        }
    } ?: error("Nije moguće otvoriti datoteku.")
}.isSuccess
