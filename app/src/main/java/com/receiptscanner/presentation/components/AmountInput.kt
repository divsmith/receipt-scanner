package com.receiptscanner.presentation.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun AmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Amount",
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            // Allow only digits and a single decimal point, max 2 decimal places
            val filtered = newValue.filter { it.isDigit() || it == '.' }
            val parts = filtered.split(".")
            val sanitized = when {
                parts.size > 2 -> parts[0] + "." + parts[1]
                parts.size == 2 && parts[1].length > 2 -> parts[0] + "." + parts[1].take(2)
                else -> filtered
            }
            onValueChange(sanitized)
        },
        modifier = modifier,
        label = { Text(label) },
        prefix = { Text("$") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
