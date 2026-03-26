package com.receiptscanner.domain.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FuzzyMatcherTest {

    @Test
    fun `identical strings have distance 0`() {
        assertEquals(0, FuzzyMatcher.levenshteinDistance("hello", "hello"))
    }

    @Test
    fun `similar strings have high similarity`() {
        val score = FuzzyMatcher.similarity("Walmart", "WALMART")
        assertTrue(score > 0.9)
    }

    @Test
    fun `token similarity matches partial words`() {
        val score = FuzzyMatcher.tokenSimilarity("Walmart Supercenter", "walmart")
        assertTrue(score > 0.3)
    }

    @Test
    fun `combined score works`() {
        val score = FuzzyMatcher.combinedScore("Target Store", "TARGET")
        assertTrue(score > 0.4)
    }

    @Test
    fun `empty strings have similarity 1`() {
        assertEquals(1.0, FuzzyMatcher.similarity("", ""))
    }
}
