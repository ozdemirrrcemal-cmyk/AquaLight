package com.aqua.aqualight.ui.common.header

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import com.aqua.aqualight.databinding.LayoutAquaHeaderBinding

internal fun LayoutAquaHeaderBinding.renderSearchField(
    searchField: AquaHeaderSearchField?
) {
    removeCurrentSearchWatcher()
    searchContainer.visibility = if (searchField == null) View.GONE else View.VISIBLE
    titleStatusContainer.visibility = if (searchField == null) View.VISIBLE else View.GONE
    headerRightContainer.visibility = if (searchField == null) View.VISIBLE else View.GONE

    if (searchField == null) {
        etHeaderSearch.setText("")
        btnClearHeaderSearch.visibility = View.GONE
        btnClearHeaderSearch.setOnClickListener(null)
        return
    }

    etHeaderSearch.hint = searchField.hint
    if (etHeaderSearch.text.toString() != searchField.text) {
        etHeaderSearch.setText(searchField.text)
        etHeaderSearch.setSelection(etHeaderSearch.text.length)
    }
    renderClearSearchButton(etHeaderSearch.text.isNullOrBlank())

    val watcher = HeaderSearchTextWatcher { value ->
        renderClearSearchButton(value.isBlank())
        searchField.onTextChanged(value)
    }
    etHeaderSearch.addTextChangedListener(watcher)
    etHeaderSearch.tag = watcher
    btnClearHeaderSearch.setOnClickListener {
        etHeaderSearch.setText("")
        searchField.onClearClick?.invoke()
    }
}

private fun LayoutAquaHeaderBinding.removeCurrentSearchWatcher() {
    val watcher = etHeaderSearch.tag as? TextWatcher ?: return
    etHeaderSearch.removeTextChangedListener(watcher)
    etHeaderSearch.tag = null
}

private fun LayoutAquaHeaderBinding.renderClearSearchButton(isQueryEmpty: Boolean) {
    btnClearHeaderSearch.visibility = if (isQueryEmpty) View.GONE else View.VISIBLE
}

private class HeaderSearchTextWatcher(
    private val onValueChanged: (String) -> Unit
) : TextWatcher {
    override fun beforeTextChanged(
        s: CharSequence?,
        start: Int,
        count: Int,
        after: Int
    ) = Unit

    override fun onTextChanged(
        s: CharSequence?,
        start: Int,
        before: Int,
        count: Int
    ) {
        onValueChanged(s?.toString().orEmpty())
    }

    override fun afterTextChanged(s: Editable?) = Unit
}
