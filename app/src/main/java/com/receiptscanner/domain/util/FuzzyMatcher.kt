package com.receiptscanner.domain.util

object FuzzyMatcher {
    fun levenshteinDistance(s1: String, s2: String): Int {
        val s1Lower = s1.lowercase()
        val s2Lower = s2.lowercase()
        val dp = Array(s1Lower.length + 1) { IntArray(s2Lower.length + 1) }

        for (i in 0..s1Lower.length) dp[i][0] = i
        for (j in 0..s2Lower.length) dp[0][j] = j

        for (i in 1..s1Lower.length) {
            for (j in 1..s2Lower.length) {
                val cost = if (s1Lower[i - 1] == s2Lower[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1Lower.length][s2Lower.length]
    }

    fun similarity(s1: String, s2: String): Double {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        val distance = levenshteinDistance(s1, s2)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    fun tokenSimilarity(s1: String, s2: String): Double {
        val tokens1 = s1.lowercase().split(Regex("\\s+")).toSet()
        val tokens2 = s2.lowercase().split(Regex("\\s+")).toSet()
        if (tokens1.isEmpty() && tokens2.isEmpty()) return 1.0
        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    fun combinedScore(s1: String, s2: String): Double {
        return (similarity(s1, s2) * 0.6) + (tokenSimilarity(s1, s2) * 0.4)
    }
}
