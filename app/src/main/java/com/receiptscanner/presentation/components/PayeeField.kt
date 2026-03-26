package com.receiptscanner.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.receiptscanner.domain.model.PayeeMatchResult

@Composable
fun PayeeField(
    value: String,
    onValueChange: (String) -> Unit,
    payeeMatches: List<PayeeMatchResult>,
    onPayeeSelected: (PayeeMatchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDropdown by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                showDropdown = it.isNotBlank() && payeeMatches.isNotEmpty()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Payee") },
            singleLine = true,
        )

        if (showDropdown && payeeMatches.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(payeeMatches, key = { it.payee.id }) { match ->
                        Text(
                            text = match.payee.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPayeeSelected(match)
                                    showDropdown = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}
