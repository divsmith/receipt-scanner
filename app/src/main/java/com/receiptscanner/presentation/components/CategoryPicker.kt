package com.receiptscanner.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.receiptscanner.domain.model.Category
import com.receiptscanner.domain.model.CategoryGroup

@Composable
fun CategoryPickerDialog(
    categoryGroups: List<CategoryGroup>,
    selectedCategory: Category?,
    onCategorySelected: (Category) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    val filteredGroups = remember(categoryGroups, searchQuery) {
        if (searchQuery.isBlank()) {
            categoryGroups.filter { !it.hidden }
        } else {
            categoryGroups.filter { !it.hidden }.mapNotNull { group ->
                val filtered = group.categories.filter { cat ->
                    !cat.hidden && cat.name.contains(searchQuery, ignoreCase = true)
                }
                if (filtered.isNotEmpty()) group.copy(categories = filtered) else null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Category") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search categories…") },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    filteredGroups.forEach { group ->
                        val isExpanded = expandedGroups[group.id]
                            ?: (searchQuery.isNotBlank())
                        item(key = "group_${group.id}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedGroups[group.id] = !isExpanded
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess
                                    else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                        if (isExpanded) {
                            items(
                                items = group.categories.filter { !it.hidden },
                                key = { "cat_${it.id}" },
                            ) { category ->
                                val isSelected = category.id == selectedCategory?.id
                                Text(
                                    text = category.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onCategorySelected(category) }
                                        .padding(start = 40.dp, top = 10.dp, bottom = 10.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        item(key = "div_${group.id}") {
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
