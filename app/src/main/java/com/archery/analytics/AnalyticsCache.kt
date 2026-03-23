package com.archery.analytics

/**
 * Process-lifetime in-memory cache for parsed session analytics.
 *
 * AnalyticsParser.parse() is CPU-intensive (rolling-median detrend over every
 * sensor row). Once parsed, the result is stable until the user explicitly
 * requests a refresh — so caching here avoids re-parsing on every navigation
 * back to the analytics screen.
 *
 * Keyed by the absolute CSV file path (same key used in SessionDetailViewModel).
 */
object AnalyticsCache {

    private val cache = mutableMapOf<String, SessionAnalytics>()

    fun get(filePath: String): SessionAnalytics? = cache[filePath]

    fun put(filePath: String, analytics: SessionAnalytics) {
        cache[filePath] = analytics
    }

    /** Remove a single entry so the next open will re-parse from disk. */
    fun invalidate(filePath: String) {
        cache.remove(filePath)
    }

    fun clear() = cache.clear()
}
