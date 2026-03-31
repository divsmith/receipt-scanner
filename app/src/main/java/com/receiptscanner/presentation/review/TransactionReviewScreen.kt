package com.receiptscanner.presentation.review

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receiptscanner.presentation.components.AccountPickerDialog
import com.receiptscanner.presentation.components.AmountInput
import com.receiptscanner.presentation.components.CategoryPickerDialog
import com.receiptscanner.presentation.components.PayeeField
import com.receiptscanner.presentation.components.ReceiptThumbnail
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReviewScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransactionReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val hapticFeedback = LocalHapticFeedback.current

    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showFullImage by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Success — auto-navigate back after a delay
    LaunchedEffect(uiState.isSubmitted) {
        if (uiState.isSubmitted) {
            kotlinx.coroutines.delay(2000)
            onNavigateBack()
        }
    }

    // Haptic feedback on successful submission
    LaunchedEffect(Unit) {
        viewModel.submissionSuccess.collect {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Transaction") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading receipt data…")
                    }
                }
            }

            uiState.isSubmitted -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Transaction submitted!",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Receipt thumbnail
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ReceiptThumbnail(
                            imagePath = uiState.receiptImagePath,
                            modifier = Modifier.clickable { showFullImage = !showFullImage },
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Tap image to preview",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    AnimatedVisibility(visible = showFullImage) {
                        ReceiptThumbnail(
                            imagePath = uiState.receiptImagePath,
                            size = 300.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showFullImage = false },
                        )
                    }

                    // Payee field with autocomplete
                    PayeeField(
                        value = uiState.payeeName,
                        onValueChange = viewModel::updatePayeeName,
                        payeeMatches = uiState.payeeMatches,
                        onPayeeSelected = viewModel::selectPayee,
                    )

                    // Amount confidence indicator
                    if (uiState.amount.isNotBlank() && uiState.totalConfidence > 0f) {
                        if (uiState.totalConfidence < 0.5f) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.errorContainer,
                                        shape = MaterialTheme.shapes.small,
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = "Amount may need correction — please verify",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }

                    // Amount
                    AmountInput(
                        value = uiState.amount,
                        onValueChange = viewModel::updateAmount,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Date
                    OutlinedTextField(
                        value = uiState.date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                        onValueChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true },
                        label = { Text("Date") },
                        readOnly = true,
                        enabled = false,
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = "Pick date")
                            }
                        },
                    )

                    // Category selector
                    OutlinedTextField(
                        value = uiState.selectedCategory?.name ?: "",
                        onValueChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCategoryPicker = true },
                        label = { Text("Category") },
                        readOnly = true,
                        enabled = false,
                        trailingIcon = {
                            IconButton(onClick = { showCategoryPicker = true }) {
                                Icon(Icons.Default.Category, contentDescription = "Pick category")
                            }
                        },
                    )

                    // Category suggestions chips
                    if (uiState.categorySuggestions.isNotEmpty()) {
                        Text(
                            text = "Suggested categories:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            uiState.categorySuggestions.take(3).forEach { suggestion ->
                                androidx.compose.material3.SuggestionChip(
                                    onClick = { viewModel.selectCategory(suggestion.category) },
                                    label = { Text(suggestion.category.name) },
                                )
                            }
                        }
                    }

                    // Account selector
                    OutlinedTextField(
                        value = uiState.selectedAccount?.name ?: "",
                        onValueChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAccountPicker = true },
                        label = { Text("Account") },
                        readOnly = true,
                        enabled = false,
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (uiState.accountMatch?.matchedByCardNumber == true &&
                                    uiState.accountMatch?.account?.id == uiState.selectedAccount?.id
                                ) {
                                    Icon(
                                        Icons.Default.CreditCard,
                                        contentDescription = "Card match",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                IconButton(onClick = { showAccountPicker = true }) {
                                    Icon(Icons.Default.CreditCard, contentDescription = "Pick account")
                                }
                            }
                        },
                    )

                    // Memo
                    OutlinedTextField(
                        value = uiState.memo,
                        onValueChange = viewModel::updateMemo,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Memo") },
                        singleLine = true,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Submit button
                    Button(
                        onClick = viewModel::submitTransaction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !uiState.isSubmitting,
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Submit to YNAB", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    if (uiState.isSubmitting) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.date
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        viewModel.updateDate(date)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Category picker dialog
    if (showCategoryPicker) {
        CategoryPickerDialog(
            categoryGroups = uiState.categoryGroups,
            selectedCategory = uiState.selectedCategory,
            onCategorySelected = {
                viewModel.selectCategory(it)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
        )
    }

    // Account picker dialog
    if (showAccountPicker) {
        AccountPickerDialog(
            accounts = uiState.accounts,
            selectedAccount = uiState.selectedAccount,
            accountMatch = uiState.accountMatch,
            onAccountSelected = {
                viewModel.selectAccount(it)
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false },
        )
    }
}
