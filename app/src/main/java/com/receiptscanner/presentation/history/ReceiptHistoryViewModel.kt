package com.receiptscanner.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.receiptscanner.domain.model.Receipt
import com.receiptscanner.domain.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReceiptHistoryViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository,
) : ViewModel() {

    val receipts: StateFlow<List<Receipt>> = receiptRepository.getAllReceipts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteReceipt(id: String) {
        viewModelScope.launch {
            receiptRepository.deleteReceipt(id)
        }
    }
}
