package com.aqua.aqualight.ui.tabs.aquarium.detail.health.algae

import android.content.Context
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeTypeId
import com.aqua.aqualight.databinding.FragmentTankAlgaeControlBinding
import java.util.Locale

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

        binding.etAlgaeSearch.doAfterTextChanged { editable ->
            filter(editable?.toString().orEmpty())
        }
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
        binding.etAlgaeSearch.setText("")
        typeAdapter.submitItems(AlgaeUiCatalog.definitions)
        typeAdapter.setSelected(null)
    }

    private fun filter(query: String) {
        val normalized = query.trim().lowercase(Locale.getDefault())
        val definitions = if (normalized.isBlank()) {
            AlgaeUiCatalog.definitions
        } else {
            AlgaeUiCatalog.definitions.filter { definition ->
                val name = context.getString(definition.nameRes)
                    .lowercase(Locale.getDefault())
                val description = context.getString(definition.shortDescriptionRes)
                    .lowercase(Locale.getDefault())

                name.contains(normalized) || description.contains(normalized)
            }
        }

        typeAdapter.submitItems(
            definitions = definitions,
            selected = selectedType
        )
    }

    companion object {
        private const val COMMON_GRID_COLUMNS = 4
        private const val TYPE_GRID_COLUMNS = 3
    }
}
