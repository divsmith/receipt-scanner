package com.receiptscanner.data.ocr

import android.content.Context
import com.receiptscanner.testing.receiptfixtures.ReceiptFixture
import com.receiptscanner.testing.receiptfixtures.ReceiptFixtureLabelsParser
import java.io.File

object OcrFixtureImageLoader {

    fun loadFixturesFromAssets(context: Context): List<ReceiptFixture> {
        val labels = context.assets.open("images/labels.md")
            .bufferedReader()
            .use { it.readText() }
        val fixtures = ReceiptFixtureLabelsParser.parse(labels)
        val availableAssets = context.assets.list("images")?.toSet().orEmpty()

        fixtures.forEach { fixture ->
            require(availableAssets.contains(fixture.imageName)) {
                "Missing fixture image asset: ${fixture.imageName}"
            }
        }

        return fixtures
    }

    fun copyImageToCache(assetContext: Context, storageContext: Context, imageName: String): File {
        val fixtureDir = File(storageContext.cacheDir, "ocr-fixtures").apply { mkdirs() }
        val tempFile = File(fixtureDir, imageName)
        assetContext.assets.open("images/$imageName").use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        return tempFile
    }
}
