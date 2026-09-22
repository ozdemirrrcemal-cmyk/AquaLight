package com.aqua.aqualight.ui.tabs.aquarium.detail.livestock

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.LivestockCatalogItem
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentTankLivestockPickerBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderSearchField
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TankLivestockPickerFragment : Fragment(R.layout.fragment_tank_livestock_picker) {

    private val args: TankLivestockPickerFragmentArgs by navArgs()

    private var _binding: FragmentTankLivestockPickerBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: TankLivestockPickerAdapter
    private lateinit var renderer: TankLivestockPickerRenderer

    private val livestockCatalogOperations by lazy(LazyThreadSafetyMode.NONE) {
        requireContext().requireAppContainer().livestockCatalogOperations
    }

    private var allEntries: List<LivestockCatalogItem> = emptyList()
    private var selectedCategory: String = LivestockCategories.FISH
    private var selectedEntryId: String? = null
    private var searchQuery: String = ""
    private var isNavigating: Boolean = false
    private var catalogLoadFailed: Boolean = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        selectedCategory = savedInstanceState?.getString(STATE_CATEGORY)
            ?.takeIf { category -> category in LivestockCategories.all }
            ?: LivestockCategories.FISH
        selectedEntryId = savedInstanceState?.getString(STATE_SELECTED_ENTRY_ID)
        searchQuery = savedInstanceState?.getString(STATE_SEARCH_QUERY).orEmpty()
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentTankLivestockPickerBinding.bind(view)

        setupHeader()
        setupRecycler()
        setupClickListeners()
        renderer.renderCategories(selectedCategory)
        renderer.renderSelection(selectedEntryId != null)
        loadCatalog()
    }

    override fun onResume() {
        super.onResume()
        isNavigating = false
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        outState.putString(STATE_CATEGORY, selectedCategory)
        outState.putString(STATE_SELECTED_ENTRY_ID, selectedEntryId)
        outState.putString(STATE_SEARCH_QUERY, searchQuery)
        super.onSaveInstanceState(outState)
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                onBackClick = {
                    findNavController().navigateUp()
                },
                searchField = AquaHeaderSearchField(
                    hint = getString(R.string.catalog_search_hint),
                    text = searchQuery,
                    onTextChanged = { query ->
                        searchQuery = query.trim()
                        renderList()
                    },
                    onClearClick = {
                        searchQuery = ""
                        renderList()
                    }
                )
            )
        )
    }

    private fun setupRecycler() {
        adapter = TankLivestockPickerAdapter { entry ->
            selectedEntryId = if (selectedEntryId == entry.id) null else entry.id
            renderList()
            renderer.renderSelection(selectedEntryId != null)
        }
        renderer = TankLivestockPickerRenderer(
            context = requireContext(),
            binding = binding,
            adapter = adapter,
            onCategorySelected = { category ->
                selectedCategory = category
                selectedEntryId = null
                renderer.renderCategories(selectedCategory)
                renderList()
                renderer.renderSelection(false)
            }
        )

        binding.rvLivestock.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLivestock.adapter = adapter
        binding.rvLivestock.setHasFixedSize(false)
    }

    private fun setupClickListeners() {
        binding.btnNewLivestock.setOnClickListener {
            openLivestockForm(
                catalogEntryId = "",
                category = selectedCategory,
                presetName = searchQuery
            )
        }

        binding.btnContinue.setOnClickListener {
            val selectedEntry = allEntries.firstOrNull { entry ->
                entry.id == selectedEntryId
            } ?: return@setOnClickListener

            openLivestockForm(
                catalogEntryId = selectedEntry.id,
                category = selectedEntry.category,
                presetName = ""
            )
        }
    }

    private fun loadCatalog() {
        binding.loadingIndicator.isVisible = true
        binding.rvLivestock.isVisible = false
        binding.tvEmptyState.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    livestockCatalogOperations.entries()
                }
            }

            binding.loadingIndicator.isVisible = false

            result.onSuccess { entries ->
                catalogLoadFailed = false
                allEntries = entries
                renderList()
            }.onFailure {
                catalogLoadFailed = true
                allEntries = emptyList()
                renderList()
            }
        }
    }

    private fun renderList() {
        renderer.renderList(
            entries = allEntries,
            selectedCategory = selectedCategory,
            selectedEntryId = selectedEntryId,
            searchQuery = searchQuery,
            catalogLoadFailed = catalogLoadFailed
        )
    }

    private fun openLivestockForm(
        catalogEntryId: String,
        category: String,
        presetName: String
    ) {
        if (isNavigating) {
            return
        }

        isNavigating = true

        val directions = TankLivestockPickerFragmentDirections
            .actionTankLivestockPickerFragmentToTankDetailLivestockFormFragment(
                tankId = args.tankId,
                livestockId = 0L,
                catalogEntryId = catalogEntryId,
                presetCategory = category,
                openedFromPicker = true,
                presetName = presetName.trim()
            )

        findNavController().navigate(directions)
    }

    override fun onDestroyView() {
        binding.rvLivestock.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val STATE_CATEGORY = "tank_livestock_picker_category"
        private const val STATE_SELECTED_ENTRY_ID = "tank_livestock_picker_selected_entry_id"
        private const val STATE_SEARCH_QUERY = "tank_livestock_picker_search_query"
    }
}