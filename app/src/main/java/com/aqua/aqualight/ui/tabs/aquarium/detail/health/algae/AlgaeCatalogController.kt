package com.aqua.aqualight.ui.tabs.aquarium.detail.health.algae

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeTypeId
import com.aqua.aqualight.databinding.FragmentTankAlgaeControlBinding

internal class AlgaeCatalogController(
    private val context: Context,
    private val binding: FragmentTankAlgaeControlBinding,
    private val onCommonSelected: (AlgaeTypeId) -> Unit,
    private val onTypeSelected: (AlgaeTypeId) -> Unit
) {

    private val commonAdapter = AlgaeCatalogAdapter(onCommonSelected)
    private val typeAdapter = AlgaeCatalogAdapter(::handleTypeSelected)

    var selectedType: AlgaeTypeId? = null
        private set

    fun bind() {
        binding.commonAlgaeRecycler.apply {
            layoutManager = GridLayoutManager(context, COMMON_GRID_COLUMNS)
            adapter = commonAdapter
            itemAnimator = null
        }
        commonAdapter.submitItems(AlgaeUiCatalog.commonDefinitions)

        binding.algaeTypeRecycler.apply {
            layoutManager = GridLayoutManager(context, TYPE_GRID_COLUMNS)
            adapter = typeAdapter
            itemAnimator = null
        }
        typeAdapter.submitItems(AlgaeUiCatalog.definitions)

    }

    fun select(type: AlgaeTypeId?) {
        selectedType = type
        typeAdapter.setSelected(type)
    }

    private fun handleTypeSelected(type: AlgaeTypeId) {
        selectedType = type
        typeAdapter.setSelected(type)
        onTypeSelected(type)
    }

    fun reset() {
        selectedType = null
        typeAdapter.submitItems(AlgaeUiCatalog.definitions)
        typeAdapter.setSelected(null)
    }

    companion object {
        private const val COMMON_GRID_COLUMNS = 4
        private const val TYPE_GRID_COLUMNS = 3
    }
}
