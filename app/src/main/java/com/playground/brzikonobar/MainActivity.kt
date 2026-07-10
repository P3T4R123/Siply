package com.playground.siply

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playground.siply.data.AppDatabase
import com.playground.siply.data.PosRepository
import com.playground.siply.ui.MainViewModel
import com.playground.siply.ui.PosApp
import com.playground.siply.ui.theme.BrziKonobarTheme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class MainActivity : ComponentActivity() {
    private val credentialManager by lazy(LazyThreadSafetyMode.NONE) {
        CredentialManager.create(this)
    }
    private val repository by lazy(LazyThreadSafetyMode.NONE) {
        PosRepository(
            database = AppDatabase.getInstance(applicationContext),
            context = applicationContext,
        )
    }

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()

            BrziKonobarTheme(darkTheme = uiState.value.darkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PosApp(
                        uiState = uiState.value,
                        messages = viewModel.messages,
                        onSelectDashboardRange = viewModel::selectDashboardRange,
                        onSelectDashboardWaiter = viewModel::selectDashboardWaiter,
                        onSelectCategory = viewModel::selectCategory,
                        onAddProduct = viewModel::addProduct,
                        onAdjustQuantity = viewModel::adjustQuantity,
                        onSaveReceipt = viewModel::saveReceipt,
                        onDeleteLastReceipt = viewModel::deleteLastReceipt,
                        onClearAllSalesData = viewModel::clearAllSalesData,
                        onResetDailyStats = viewModel::resetDailyStats,
                        onToggleDarkMode = viewModel::toggleDarkMode,
                        onAddCatalogProduct = viewModel::addCatalogProduct,
                        onUpdateProductImage = viewModel::updateProductImage,
                        onSetInventoryQuantities = viewModel::setInventoryQuantities,
                        onAddProcurementEntries = viewModel::addProcurementEntries,
                        onSaveCurrentPriceList = viewModel::saveCurrentPriceListVersion,
                        onImportPriceList = viewModel::importPriceList,
                        onActivatePriceList = viewModel::activatePriceList,
                        onCreateOnlineCafe = viewModel::createOnlineCafe,
                        onRefreshWaiterInvite = viewModel::refreshWaiterInvite,
                        onRefreshWebAdminInvite = viewModel::refreshWebAdminInvitePayload,
                        onRefreshCloudCatalog = viewModel::refreshCloudCatalog,
                        onJoinCafeAsWaiter = viewModel::joinCafeAsWaiter,
                        onSignInWithGoogle = {
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(getString(R.string.default_web_client_id))
                                .setAutoSelectEnabled(false)
                                .build()
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()
                            val credential = credentialManager.getCredential(
                                context = this@MainActivity,
                                request = request,
                            ).credential
                            if (
                                credential !is CustomCredential ||
                                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                            ) {
                                error("Odabrana vjerodajnica nije Google račun.")
                            }
                            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            viewModel.signInWithGoogle(googleCredential.idToken)
                        },
                        onForgetCloudConnection = viewModel::forgetCloudConnection,
                        onRestoreBackup = viewModel::restoreBackup,
                        onExportResult = viewModel::notifyExportSaved,
                        loadLastReceiptInfo = viewModel::loadLastReceiptInfo,
                        buildExportPayload = viewModel::buildExportPayload,
                        buildCompleteSalesExportPayload = viewModel::buildCompleteSalesExportPayload,
                        buildPriceListExportPayload = viewModel::buildPriceListExportPayload,
                        buildBackupPayload = viewModel::buildBackupExportPayload,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshCloudCatalogSilently()
    }
}
