package com.aqua.aqualight.data.aquarium.catalog.livestock

import java.security.MessageDigest

internal object LivestockCatalogRevision {
    const val REVISION = "livestock-care-2026-09-27.1"
    const val CONTENT_SHA256 = "6a400683d2b188320d083076a854c2e75e1f37c89d9d5958d47bb2651d9153cc"

    fun verify(content: ByteArray) {
        val digest = MessageDigest.getInstance("SHA-256").digest(content)
            .joinToString("") { "%02x".format(it) }
        check(digest == CONTENT_SHA256) { "Livestock content changed without a reviewed content revision." }
    }
}
