package com.aqua.aqualight.ui.common.header

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

data class AquaHeaderConfig(
    val titleOverride: String? = null,
    val showBackButton: Boolean = true,
    val onBackClick: (() -> Unit)? = null,
    @DrawableRes val statusIconRes: Int? = null,
    val primaryAction: AquaHeaderPrimaryAction? = null,
    val filledIconAction: AquaHeaderFilledIconAction? = null,
    val cardIconAction: AquaHeaderCardIconAction? = null,
    val pillTextAction: AquaHeaderPillTextAction? = null,
    val scoreBadge: AquaHeaderScoreBadge? = null,
    val searchField: AquaHeaderSearchField? = null,
    val actions: List<AquaHeaderAction> = emptyList()
)

data class AquaHeaderPrimaryAction(
    val text: String,
    val contentDescription: String? = null,
    val onClick: () -> Unit
)

data class AquaHeaderFilledIconAction(
    @DrawableRes val iconRes: Int,
    val contentDescription: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

enum class AquaHeaderCardIconTone {
    SUCCESS,
    PRIMARY,
    NEUTRAL,
    DANGER
}

data class AquaHeaderCardIconAction(
    @DrawableRes val iconRes: Int,
    val contentDescription: String,
    val tone: AquaHeaderCardIconTone = AquaHeaderCardIconTone.NEUTRAL,
    @ColorInt val backgroundColor: Int? = null,
    @ColorInt val strokeColor: Int? = null,
    @ColorInt val iconTintColor: Int? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

data class AquaHeaderPillTextAction(
    val text: String,
    @DrawableRes val backgroundRes: Int,
    val contentDescription: String? = null,
    @ColorInt val textColor: Int? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

data class AquaHeaderScoreBadge(
    val text: String,
    @ColorInt val strokeColor: Int,
    @ColorInt val textColor: Int = strokeColor,
    val contentDescription: String? = null,
    val onClick: (() -> Unit)? = null
)

data class AquaHeaderSearchField(
    val hint: String,
    val text: String = "",
    val onTextChanged: (String) -> Unit,
    val onClearClick: (() -> Unit)? = null
)
