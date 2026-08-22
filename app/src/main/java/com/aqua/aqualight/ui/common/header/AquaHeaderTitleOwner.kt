package com.aqua.aqualight.ui.common.header

/**
 * Keeps a header view bound to the destination that created it.
 *
 * NavController advances `currentDestinationTitle` before the outgoing Fragment view finishes its
 * exit transition. Capturing the first non-blank destination label prevents a later state render on
 * that outgoing view from borrowing the incoming destination's title.
 */
internal class AquaHeaderTitleOwner {

    private var ownerDestinationTitle: String? = null

    fun resolve(
        titleOverride: String?,
        currentDestinationTitle: String
    ): String {
        if (ownerDestinationTitle == null && currentDestinationTitle.isNotBlank()) {
            ownerDestinationTitle = currentDestinationTitle
        }
        return titleOverride ?: ownerDestinationTitle.orEmpty()
    }
}
