package com.receiptscanner.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receiptscanner.domain.model.CloudOcrProviderType
import com.receiptscanner.domain.model.OcrMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showToken by remember { mutableStateOf(false) }
    var showCopilotToken by remember { mutableStateOf(false) }
    var showOpenRouterKey by remember { mutableStateOf(false) }
    var showNvidiaKey by remember { mutableStateOf(false) }
    var budgetExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // YNAB API Token section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "YNAB API Token",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.token,
                        onValueChange = viewModel::updateToken,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Personal Access Token") },
                        singleLine = true,
                        visualTransformation = if (showToken)
                            VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    if (showToken) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = if (showToken) "Hide" else "Show",
                                )
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::saveToken,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save Token")
                    }
                }
            }

            // OCR Settings
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "OCR Settings",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Local option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateOcrMode(OcrMode.LOCAL) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = uiState.ocrMode == OcrMode.LOCAL,
                            onClick = { viewModel.updateOcrMode(OcrMode.LOCAL) },
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text("Local (ML Kit)", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "On-device OCR — works offline",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Cloud option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateOcrMode(OcrMode.CLOUD) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = uiState.ocrMode == OcrMode.CLOUD,
                            onClick = { viewModel.updateOcrMode(OcrMode.CLOUD) },
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text("Cloud OCR", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Vision AI — more accurate, requires internet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Cloud provider sub-options
                    if (uiState.ocrMode == OcrMode.CLOUD) {
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                        Spacer(modifier = Modifier.height(8.dp))

                        // GitHub Copilot — coming soon, greyed out
                        Box(modifier = Modifier.alpha(0.38f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                            ) {
                                RadioButton(
                                    selected = uiState.cloudOcrProviderType == CloudOcrProviderType.COPILOT,
                                    onClick = null,
                                    enabled = false,
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "GitHub Copilot",
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Coming Soon",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    RoundedCornerShape(4.dp),
                                                )
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                        )
                                    }
                                    Text(
                                        "Requires GitHub Copilot subscription",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }

                        // OpenRouter
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateCloudOcrProvider(CloudOcrProviderType.OPENROUTER) }
                                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            RadioButton(
                                selected = uiState.cloudOcrProviderType == CloudOcrProviderType.OPENROUTER,
                                onClick = { viewModel.updateCloudOcrProvider(CloudOcrProviderType.OPENROUTER) },
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text("OpenRouter", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "100+ free vision models via openrouter.ai",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // OpenRouter configuration
                        if (uiState.cloudOcrProviderType == CloudOcrProviderType.OPENROUTER) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(modifier = Modifier.padding(start = 16.dp)) {
                                val needsSetup = !uiState.isOpenRouterApiKeySaved ||
                                    uiState.openRouterModelId == null
                                if (needsSetup) {
                                    Text(
                                        "Complete the setup below to start using Cloud OCR.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                OutlinedTextField(
                                    value = uiState.openRouterApiKey,
                                    onValueChange = viewModel::updateOpenRouterApiKey,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("OpenRouter API Key") },
                                    singleLine = true,
                                    visualTransformation = if (showOpenRouterKey)
                                        VisualTransformation.None
                                    else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showOpenRouterKey = !showOpenRouterKey }) {
                                            Icon(
                                                if (showOpenRouterKey) Icons.Default.VisibilityOff
                                                else Icons.Default.Visibility,
                                                contentDescription = if (showOpenRouterKey) "Hide" else "Show",
                                            )
                                        }
                                    },
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Get a free API key at openrouter.ai",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = viewModel::saveOpenRouterApiKey,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Save API Key")
                                }

                                if (uiState.isOpenRouterApiKeySaved) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Vision Model",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))

                                    val selectedModelName = uiState.availableOpenRouterModels
                                        .find { it.id == uiState.openRouterModelId }?.name
                                        ?: uiState.openRouterModelId
                                        ?: "Select a model"

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        ExposedDropdownMenuBox(
                                            expanded = modelExpanded,
                                            onExpandedChange = {
                                                if (!uiState.isLoadingModels) modelExpanded = it
                                            },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            OutlinedTextField(
                                                value = if (uiState.isLoadingModels) "Loading models…"
                                                else selectedModelName,
                                                onValueChange = {},
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                                readOnly = true,
                                                enabled = !uiState.isLoadingModels,
                                                trailingIcon = {
                                                    if (uiState.isLoadingModels) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(20.dp),
                                                            strokeWidth = 2.dp,
                                                        )
                                                    } else {
                                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                                            expanded = modelExpanded,
                                                        )
                                                    }
                                                },
                                            )
                                            ExposedDropdownMenu(
                                                expanded = modelExpanded,
                                                onDismissRequest = { modelExpanded = false },
                                            ) {
                                                uiState.availableOpenRouterModels.forEach { model ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Column {
                                                                Text(model.name)
                                                                Text(
                                                                    model.id,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            viewModel.selectOpenRouterModel(model)
                                                            modelExpanded = false
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(
                                            onClick = viewModel::refreshOpenRouterModels,
                                            enabled = !uiState.isLoadingModels,
                                        ) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = "Refresh models",
                                            )
                                        }
                                    }

                                    if (uiState.availableOpenRouterModels.isEmpty() && !uiState.isLoadingModels) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "No free vision models found. Tap ↻ to retry.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }

                        // NVIDIA NIM
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateCloudOcrProvider(CloudOcrProviderType.NVIDIA) }
                                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            RadioButton(
                                selected = uiState.cloudOcrProviderType == CloudOcrProviderType.NVIDIA,
                                onClick = { viewModel.updateCloudOcrProvider(CloudOcrProviderType.NVIDIA) },
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text("NVIDIA NIM", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Phi-4-multimodal-instruct via NVIDIA NIM",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // NVIDIA configuration
                        if (uiState.cloudOcrProviderType == CloudOcrProviderType.NVIDIA) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(modifier = Modifier.padding(start = 16.dp)) {
                                if (!uiState.isNvidiaApiKeySaved) {
                                    Text(
                                        "Complete the setup below to start using Cloud OCR.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                OutlinedTextField(
                                    value = uiState.nvidiaApiKey,
                                    onValueChange = viewModel::updateNvidiaApiKey,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("NVIDIA API Key") },
                                    singleLine = true,
                                    visualTransformation = if (showNvidiaKey)
                                        VisualTransformation.None
                                    else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showNvidiaKey = !showNvidiaKey }) {
                                            Icon(
                                                if (showNvidiaKey) Icons.Default.VisibilityOff
                                                else Icons.Default.Visibility,
                                                contentDescription = if (showNvidiaKey) "Hide" else "Show",
                                            )
                                        }
                                    },
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Get a free API key at build.nvidia.com",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = viewModel::saveNvidiaApiKey,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Save API Key")
                                }
                            }
                        }
                    }
                }
            }

            // Budget selection
            if (uiState.isTokenSaved) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Budget",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ExposedDropdownMenuBox(
                            expanded = budgetExpanded,
                            onExpandedChange = { budgetExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = uiState.budgets.find { it.id == uiState.selectedBudgetId }?.name
                                    ?: "Select a budget",
                                onValueChange = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = budgetExpanded)
                                },
                            )
                            ExposedDropdownMenu(
                                expanded = budgetExpanded,
                                onDismissRequest = { budgetExpanded = false },
                            ) {
                                uiState.budgets.forEach { budget ->
                                    DropdownMenuItem(
                                        text = { Text(budget.name) },
                                        onClick = {
                                            viewModel.selectBudget(budget)
                                            budgetExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Default Account selection
            if (uiState.selectedBudgetId != null && uiState.accounts.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Default Account",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ExposedDropdownMenuBox(
                            expanded = accountExpanded,
                            onExpandedChange = { accountExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = uiState.accounts.find { it.id == uiState.defaultAccountId }?.name
                                    ?: "Select default account",
                                onValueChange = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded)
                                },
                            )
                            ExposedDropdownMenu(
                                expanded = accountExpanded,
                                onDismissRequest = { accountExpanded = false },
                            ) {
                                uiState.accounts.forEach { account ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(account.name)
                                                Text(
                                                    account.type.replaceFirstChar { it.uppercase() },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectDefaultAccount(account)
                                            accountExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Pending transactions
            if (uiState.isTokenSaved && uiState.pendingCount > 0) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Pending Transactions",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${uiState.pendingCount} transaction(s) waiting to be submitted",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = viewModel::retryPending,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Retry Pending")
                        }
                    }
                }
            }

            // Sync & Clear
            if (uiState.isTokenSaved) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "YNAB Data",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = viewModel::syncCache,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isSyncing && uiState.selectedBudgetId != null,
                        ) {
                            if (uiState.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text("Import YNAB Data")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = viewModel::clearCache,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("Clear All Data")
                        }
                    }
                }
            }

            // App info
            HorizontalDivider()

            // Developer section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Developer",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleDebugMode() }
                            .padding(vertical = 4.dp),
                    ) {
                        Switch(
                            checked = uiState.debugModeEnabled,
                            onCheckedChange = { viewModel.toggleDebugMode() },
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text("Debug OCR Overlay", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Shows detected text regions and field matches on captured receipts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Text(
                text = "Receipt Scanner v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
