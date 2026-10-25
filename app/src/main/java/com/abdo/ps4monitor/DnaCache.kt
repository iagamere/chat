package com.abdo.ps4monitor

/**
 * Bounded, thread-safe cache of generated identities. An entry is only valid for the artwork it was generated from: it carries the
 * cover's fingerprint, so when a game's cover changes the old identity is never reused (and is evicted on the next lookup).
 */
class DnaCache(private val capacity: Int) {
    private class Entry(val fingerprint: String, val dna: DesignDna)
    private val map = object : LinkedHashMap<String, Entry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > capacity
    }
    init { require(capacity > 0) }

    @Synchronized fun get(key: String, fingerprint: String): DesignDna? {
        val e = map[key] ?: return null
        if (e.fingerprint != fingerprint) { map.remove(key); return null }
        return e.dna
    }
    @Synchronized fun put(key: String, fingerprint: String, dna: DesignDna) { map[key] = Entry(fingerprint, dna) }
    @Synchronized fun invalidate(key: String) { map.remove(key) }
    @Synchronized fun clear() { map.clear() }
    @Synchronized fun size() = map.size
    /** The cached identity for this artwork, or [compute] (called outside the lock) and stored. */
    fun getOrCompute(key: String, fingerprint: String, compute: () -> DesignDna): DesignDna =
        get(key, fingerprint) ?: compute().also { put(key, fingerprint, it) }
}
