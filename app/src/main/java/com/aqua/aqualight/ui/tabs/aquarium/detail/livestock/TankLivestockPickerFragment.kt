package com.aqua.aqualight.ui.tabs.aquarium.detail.livestock

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.LivestockCatalogItem
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.application.aquarium.LivestockCatalogItem
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentTankLivestockPickerBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderSearchField
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.text.setTextSizeResource
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TankLivestockPickerFragment : Fragment(R.layout.fragment_tank_livestock_picker) {

    private val args: TankLivestockPickerFragmentArgs by navArgs()

    private var _binding: FragmentTankLivestockPickerBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: TankLivestockPickerAdapter

    private val livestockCatalogOperations by lazy(LazyThreadSafetyMode.NONE) {
        requireContext().requireAppContainer().livestockCatalogOperations
    }

    private val catalogOperations
        get() = requireContext().requireAppContainer().livestockCatalogOperations

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
        renderCategoryChips()
        updateSelectionUi()
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
            selectedEntryId = if (selectedEntryId == entry.id) {
                null
            } else {
                entry.id
            }

            renderList()
            updateSelectionUi()
        }

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
                    catalogOperations.entries()
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

    private fun renderCategoryChips() {
        binding.categoryContainer.removeAllViews()

        LivestockCategories.all.forEach { category ->
            binding.categoryContainer.addView(
                createCategoryChip(
                    category = category,
                    selected = category == selectedCategory
                )
            )
        }

        updateCustomButtonText()
    }

    private fun createCategoryChip(
        category: String,
        selected: Boolean
    ): View {
        val card = MaterialCardView(requireContext()).apply {
            radius = resources.getDimensionPixelOffset(R.dimen.aqua_size_13).toFloat()
            strokeWidth = resources.getDimensionPixelOffset(R.dimen.aqua_size_1)
            strokeColor = ContextCompat.getColor(
                requireContext(),
                if (selected) {
                    R.color.aqua_card_accent
                } else {
                    R.color.aqua_card_outline
                }
            )
            setCardBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) {
                        R.color.aqua_card_surface_pressed
                    } else {
                        R.color.aqua_card_surface
                    }
                )
            )
            cardElevation = 0f
            useCompatPadding = false
            isClickable = true
            isFocusable = true

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                resources.getDimensionPixelOffset(R.dimen.aqua_size_42)
            ).apply {
                marginEnd = resources.getDimensionPixelOffset(R.dimen.aqua_size_8)
            }

            setOnClickListener {
                if (selectedCategory == category) {
                    return@setOnClickListener
                }

                selectedCategory = category
                selectedEntryId = null
                renderCategoryChips()
                renderList()
                updateSelectionUi()
            }
        }

        val text = TextView(requireContext()).apply {
            text = getString(LivestockCategories.labelRes(category))
            gravity = Gravity.CENTER
            setTextSizeResource(R.dimen.aqua_text_size_caption_plus)
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) {
                        R.color.aqua_card_text_primary
                    } else {
                        R.color.aqua_card_text_secondary
                    }
                )
            )
            setTypeface(
                null,
                if (selected) Typeface.BOLD else Typeface.NORMAL
            )
            includeFontPadding = false
            setPadding(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_15),
                0,
                resources.getDimensionPixelOffset(R.dimen.aqua_size_15),
                0
            )
        }

        card.addView(
            text,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        return card
    }

    private fun renderList() {
        if (!::adapter.isInitialized) {
            return
        }

        if (catalogLoadFailed) {
            adapter.submitList(emptyList())
            binding.rvLivestock.isVisible = false
            binding.tvEmptyState.isVisible = true
            binding.tvEmptyState.text = getString(
                R.string.livestock_picker_catalog_unavailable
            )
            binding.tvResultCount.text = getString(
                R.string.livestock_picker_result_zero
            )
            return
        }

        val filteredEntries = allEntries.asSequence()
            .filter { entry -> entry.category == selectedCategory }
            .filter { entry -> entry.matches(searchQuery) }
            .toList()

        val items = filteredEntries.map { entry ->
            TankLivestockPickerItem(
                entry = entry,
                selected = entry.id == selectedEntryId
            )
        }

        adapter.submitList(items)

        binding.rvLivestock.isVisible = filteredEntries.isNotEmpty()
        binding.tvEmptyState.isVisible = filteredEntries.isEmpty()
        binding.tvEmptyState.text = getString(R.string.livestock_picker_empty)
        binding.tvResultCount.text = resources.getQuantityString(
            R.plurals.livestock_picker_result_count,
            filteredEntries.size,
            filteredEntries.size
        )
    }

    private fun updateSelectionUi() {
        val hasSelection = selectedEntryId != null

        binding.tvSelectedCount.text = if (hasSelection) {
            getString(R.string.livestock_picker_selected_one)
        } else {
            getString(R.string.livestock_picker_selected_zero)
        }
        binding.btnContinue.isEnabled = hasSelection
    }

    private fun updateCustomButtonText() {
        binding.btnNewLivestock.text = getString(
            R.string.livestock_picker_new_category,
            getString(LivestockCategories.labelRes(selectedCategory))
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