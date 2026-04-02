package com.receiptscanner.di

import com.receiptscanner.data.ocr.ImagePreprocessor
import com.receiptscanner.data.ocr.MlKitTextRecognizer
import com.receiptscanner.data.ocr.ReceiptParser
import com.receiptscanner.data.remote.copilot.CopilotTokenProvider
import com.receiptscanner.data.remote.copilot.CopilotTokenProviderImpl
import com.receiptscanner.data.remote.nvidia.NvidiaTokenProvider
import com.receiptscanner.data.remote.nvidia.NvidiaTokenProviderImpl
import com.receiptscanner.data.remote.openrouter.OpenRouterTokenProvider
import com.receiptscanner.data.remote.openrouter.OpenRouterTokenProviderImpl
import dagger.Binds
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

@Module
@InstallIn(SingletonComponent::class)
abstract class OcrBindingsModule {

    @Binds
    @Singleton
    abstract fun bindCopilotTokenProvider(impl: CopilotTokenProviderImpl): CopilotTokenProvider

    @Binds
    @Singleton
    abstract fun bindOpenRouterTokenProvider(impl: OpenRouterTokenProviderImpl): OpenRouterTokenProvider

    @Binds
    @Singleton
    abstract fun bindNvidiaTokenProvider(impl: NvidiaTokenProviderImpl): NvidiaTokenProvider
}
