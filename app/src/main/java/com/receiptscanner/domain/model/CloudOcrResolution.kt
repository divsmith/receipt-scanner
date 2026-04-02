package com.receiptscanner.domain.model

enum class CloudOcrResolution(
    val maxDimension: Int,
    val label: String,
    val description: String,
) {
    LOW(512, "Low", "Fastest, less detail"),
    MEDIUM(1024, "Medium", "Balanced (recommended)"),
    HIGH(1536, "High", "Sharper, slower"),
    FULL(2048, "Full", "Highest quality"),
}
