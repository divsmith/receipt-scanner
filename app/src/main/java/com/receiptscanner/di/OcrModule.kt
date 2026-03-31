package com.receiptscanner.di

import com.receiptscanner.data.ocr.ImagePreprocessor
import com.receiptscanner.data.ocr.MlKitTextRecognizer
import com.receiptscanner.data.ocr.ReceiptParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OcrModule {

    @Provides
    @Singleton
    fun provideTextRecognizer(): MlKitTextRecognizer {
        return MlKitTextRecognizer()
    }

    @Provides
    @Singleton
    fun provideReceiptParser(): ReceiptParser {
        return ReceiptParser()
    }

    @Provides
    @Singleton
    fun provideImagePreprocessor(): ImagePreprocessor {
        return ImagePreprocessor()
    }
}
