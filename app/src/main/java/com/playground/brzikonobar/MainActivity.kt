package com.playground.siply

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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

class MainActivity : ComponentActivity() {
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
                        onSelectCategory = viewModel::selectCategory,
                        onAddProduct = viewModel::addProduct,
                        onAdjustQuantity = viewModel::adjustQuantity,
                        onSaveReceipt = viewModel::saveReceipt,
                        onDeleteLastReceipt = viewModel::deleteLastReceipt,
                        onResetDailyStats = viewModel::resetDailyStats,
                        onToggleDarkMode = viewModel::toggleDarkMode,
                        onAddCatalogProduct = viewModel::addCatalogProduct,
                        onCreateOnlineCafe = viewModel::createOnlineCafe,
                        onRefreshWaiterInvite = viewModel::refreshWaiterInvite,
                        onJoinCafeAsWaiter = viewModel::joinCafeAsWaiter,
                        onExportResult = viewModel::notifyExportSaved,
                        loadLastReceiptInfo = viewModel::loadLastReceiptInfo,
                        buildExportPayload = viewModel::buildExportPayload,
                    )
                }
            }
        }
    }
}
