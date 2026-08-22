package com.aqua.aqualight.ui.tabs.aquarium.create.materials

import com.aqua.aqualight.ui.common.text.setTextSizeResource
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.aquarium.catalog.material.AquariumMaterial
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentMaterialPickerBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderSearchField
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.tabs.aquarium.materials.MaterialSelectionMapper
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel
import com.aqua.aqualight.data.aquarium.catalog.material.MaterialCatalog
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import androidx.navigation.fragment.navArgs

class MaterialPickerFragment : Fragment(R.layout.fragment_material_picker) {

    private val args: MaterialPickerFragmentArgs by navArgs()

    private var _binding: FragmentMaterialPickerBinding? = null
    private val binding get() = _binding!!

    private val createTankViewModel: CreateTankViewModel by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        val owner = runCatching {
            findNavController().getViewModelStoreOwner(
                R.id.nav_create_tank
            )
        }.getOrElse {
            requireParentFragment()
        }

        ViewModelProvider(owner)[CreateTankViewModel::class.java]
    }

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private lateinit var categoryKey: String
    private lateinit var categoryTitle: String

    private var pickerMode: String = MODE_CREATE
    private var tankId: Long = 0L
    private var currentTank: AquariumTankSnapshot? = null
    private var hasLoadedSettingsSelections: Boolean = false
    private var isSavingSelections: Boolean = false

    private var allProducts: List<AquariumMaterial> = emptyList()
    private val selectedProductIds = mutableSetOf<String>()

    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pickerMode = args.argMode

        tankId = args.argTankId

        categoryKey = args.argCategoryKey
        categoryTitle = args.argCategoryTitle
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentMaterialPickerBinding.bind(view)

        setupHeader()
        setupClickListeners()
        setupCustomMaterialResultListener()

        if (pickerMode == MODE_SETTINGS) {
            observeSettingsTank()
        } else {
            initializeCreateMode()
        }
    }


    private fun setupCustomMaterialResultListener() {
        childFragmentManager.setFragmentResultListener(
            CUSTOM_MATERIAL_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(TextInputBottomSheet.RESULT_KEY) !=
                TextInputBottomSheet.RESULT_SAVED
            ) return@setFragmentResultListener
            result.getString(TextInputBottomSheet.RESULT_VALUE)
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let(::addCustomMaterial)
        }
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.screen_title_material_picker),
                onBackClick = {
                    closePicker()
                },
                searchField = AquaHeaderSearchField(
                    hint = getString(R.string.catalog_search_hint),
                    text = searchQuery,
                    onTextChanged = { query ->
                        searchQuery = query.trim()

                        renderMaterialList(
                            getFilteredProducts(searchQuery)
                        )
                    },
                    onClearClick = {
                        searchQuery = ""
                    }
                )
            )
        )
    }

    private fun updateSearchQuery(
        query: String
    ) {
        searchQuery = query.trim()

        setupHeader()

        renderMaterialList(
            getFilteredProducts(searchQuery)
        )
    }

    private fun initializeCreateMode() {
        val currentSelections = createTankViewModel.getMaterialsByCategory(
            categoryKey
        )

        refreshPickerContent(currentSelections)
    }

    private fun observeSettingsTank() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val tank = tanks.firstOrNull { savedTank ->
                savedTank.id == tankId
            }

            if (tank == null) {
                findNavController().navigateUp()
                return@observe
            }

            currentTank = tank

            if (hasLoadedSettingsSelections) {
                return@observe
            }

            val currentSelections = tank.materials
                .filter { material ->
                    material.categoryKey == categoryKey
                }
                .map { material ->
                    AquariumMaterialSelection(
                        id = material.id,
                        productId = material.productId,
                        categoryKey = material.categoryKey,
                        categoryTitle = material.categoryTitle,
                        name = material.name,
                        brand = material.brand,
                        note = material.note
                    )
                }

            hasLoadedSettingsSelections = true

            refreshPickerContent(currentSelections)
        }
    }

    private fun refreshPickerContent(
        currentSelections: List<AquariumMaterialSelection>
    ) {
        allProducts = buildProductList(currentSelections)

        selectedProductIds.clear()
        selectedProductIds.addAll(
            currentSelections.map { selection ->
                selection.productId
            }
        )

        renderKeywords()

        renderMaterialList(
            getFilteredProducts(searchQuery)
        )

        updateSelectedCount()
    }

    private fun buildProductList(
        currentSelections: List<AquariumMaterialSelection>
    ): List<AquariumMaterial> {
        return MaterialSelectionMapper.productsForCategory(
            context = requireContext(),
            categoryKey = categoryKey,
            currentSelections = currentSelections
        )
    }

    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener {
            saveSelections()
        }
    }

    private fun renderKeywords() {
        binding.keywordContainer.removeAllViews()

        val keywords = MaterialCatalog.getPopularKeywords(requireContext(), categoryKey)

        if (keywords.isEmpty()) {
            binding.tvKeywordTitle.isVisible = false
            binding.keywordScrollView.isVisible = false
            return
        }

        binding.tvKeywordTitle.isVisible = true
        binding.keywordScrollView.isVisible = true

        keywords.forEach { keyword ->
            binding.keywordContainer.addView(
                createKeywordChip(keyword)
            )
        }
    }

    private fun createKeywordChip(
        keyword: String
    ): View {
        val chip = MaterialCardView(requireContext()).apply {
            radius = resources.getDimensionPixelOffset(R.dimen.aqua_size_13).toFloat()
            strokeWidth = resources.getDimensionPixelOffset(R.dimen.aqua_size_1)
            strokeColor = ContextCompat.getColor(
                requireContext(),
                R.color.aqua_card_outline
            )
            setCardBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_card_surface
                )
            )
            cardElevation = 0f
            isClickable = true
            isFocusable = true

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                resources.getDimensionPixelOffset(R.dimen.aqua_size_36)
            )
            params.marginEnd = resources.getDimensionPixelOffset(R.dimen.aqua_size_8)
            layoutParams = params

            setOnClickListener {
                updateSearchQuery(keyword)
            }
        }

        val text = TextView(requireContext()).apply {
            this.text = keyword.replaceFirstChar {
                if (it.isLowerCase()) {
                    it.titlecase()
                } else {
                    it.toString()
                }
            }

            gravity = Gravity.CENTER
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_card_text_secondary
                )
            )
            setTextSizeResource(R.dimen.aqua_text_size_caption_plus)
            includeFontPadding = false
            setPadding(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_15),
                0,
                resources.getDimensionPixelOffset(R.dimen.aqua_size_15),
                0
            )
        }

        chip.addView(
            text,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        return chip
    }

    private fun getFilteredProducts(
        query: String
    ): List<AquariumMaterial> {
        if (query.isBlank()) {
            return allProducts
        }

        return allProducts.filter { product ->
            product.name.contains(query, ignoreCase = true) ||
                product.brand.contains(query, ignoreCase = true) ||
                product.categoryTitle.contains(query, ignoreCase = true) ||
                product.keywords.any { keyword ->
                    keyword.contains(query, ignoreCase = true)
                }
        }
    }

    private fun renderMaterialList(
        products: List<AquariumMaterial>
    ) {
        binding.listContainer.removeAllViews()

        if (products.isEmpty()) {
            showEmptyState()
        } else {
            products.forEach { product ->
                binding.listContainer.addView(
                    createMaterialCard(product)
                )
            }
        }

        binding.listContainer.addView(
            createNewMaterialButton()
        )
    }

    private fun showEmptyState() {
        val emptyText = TextView(requireContext()).apply {
            text = getString(R.string.material_picker_no_materials_found)
            gravity = Gravity.CENTER
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_state_text_secondary
                )
            )
            setTextSize(
                android.util.TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.aqua_text_size_state_title_small)
            )
            includeFontPadding = false

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_34)
            params.bottomMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_18)
            layoutParams = params
        }

        binding.listContainer.addView(emptyText)
    }

    private fun createMaterialCard(
        product: AquariumMaterial
    ): View {
        val isSelected = selectedProductIds.contains(product.id)

        val card = MaterialCardView(requireContext()).apply {
            radius = resources.getDimensionPixelOffset(R.dimen.aqua_size_16).toFloat()
            strokeWidth = resources.getDimensionPixelOffset(R.dimen.aqua_size_1)
            strokeColor = ContextCompat.getColor(
                requireContext(),
                if (isSelected) {
                    R.color.aqua_card_accent
                } else {
                    R.color.aqua_card_outline
                }
            )
            setCardBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isSelected) {
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

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_9)
            layoutParams = params

            setOnClickListener {
                toggleSelection(product.id)

                renderMaterialList(
                    getFilteredProducts(searchQuery)
                )

                updateSelectedCount()
            }
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = resources.getDimensionPixelOffset(R.dimen.aqua_size_74)

            setPadding(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_14),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_10),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_12),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_10)
            )
        }

        val textBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.marginEnd = resources.getDimensionPixelOffset(R.dimen.aqua_size_12)
            layoutParams = params
        }

        val category = TextView(requireContext()).apply {
            text = product.categoryTitle
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_card_text_secondary
                )
            )
            setTextSizeResource(R.dimen.aqua_text_size_micro_plus)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val name = TextView(requireContext()).apply {
            text = product.name
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_card_text_primary
                )
            )
            setTextSizeResource(R.dimen.aqua_text_size_body_compact)
            setTypeface(null, Typeface.NORMAL)
            includeFontPadding = false
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_6)
            layoutParams = params
        }

        val check = TextView(requireContext()).apply {
            text = if (isSelected) getString(R.string.aqua_selected_symbol) else ""
            gravity = Gravity.CENTER
            setTextSizeResource(R.dimen.aqua_text_size_caption)
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_content_on_dark))
            includeFontPadding = false
            setBackgroundResource(
                if (isSelected) {
                    R.drawable.bg_material_check_selected
                } else {
                    R.drawable.bg_material_check_unselected
                }
            )

            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_24),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_24)
            )
        }

        textBox.addView(category)
        textBox.addView(name)

        row.addView(textBox)
        row.addView(check)

        card.addView(row)

        return card
    }

    private fun createNewMaterialButton(): View {
        return MaterialButton(requireContext()).apply {
            text = getString(R.string.material_picker_new_title, categoryTitle)
            setTextSizeResource(R.dimen.aqua_text_size_body)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.aqua_content_on_dark))
            setTypeface(null, Typeface.BOLD)
            setAllCaps(false)
            cornerRadius = resources.getDimensionPixelOffset(R.dimen.aqua_size_14)
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.aqua_accent_primary))

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelOffset(R.dimen.aqua_size_48)
            )
            params.topMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_8)
            params.bottomMargin = resources.getDimensionPixelOffset(R.dimen.aqua_size_16)
            layoutParams = params

            setOnClickListener {
                showNewMaterialSheet()
            }
        }
    }

    private fun showNewMaterialSheet() {
        TextInputBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.material_picker_new_material),
            label = getString(R.string.material_picker_material_name),
            hint = getString(R.string.material_picker_enter_material_name),
            initialValue = searchQuery.trim(),
            secondaryLabel = getString(R.string.material_picker_category),
            secondaryValue = categoryTitle,
            saveText = getString(R.string.material_picker_save),
            cancelText = getString(R.string.material_picker_cancel),
            required = true,
            requiredMessage = getString(R.string.material_picker_required),
            requestKey = CUSTOM_MATERIAL_REQUEST_KEY
        )
    }

    private fun addCustomMaterial(
        materialName: String
    ) {
        val existingProduct = allProducts.firstOrNull { product ->
            product.name.equals(materialName, ignoreCase = true)
        }

        val productToSelect = existingProduct ?: MaterialSelectionMapper.customMaterial(
            categoryKey = categoryKey,
            categoryTitle = categoryTitle,
            materialName = materialName
        )

        if (existingProduct == null) {
            allProducts = allProducts + productToSelect
        }

        selectedProductIds.add(productToSelect.id)

        updateSearchQuery("")
        updateSelectedCount()
    }

    private fun toggleSelection(
        productId: String
    ) {
        if (selectedProductIds.contains(productId)) {
            selectedProductIds.remove(productId)
        } else {
            selectedProductIds.add(productId)
        }
    }

    private fun updateSelectedCount() {
        val count = selectedProductIds.size

        binding.tvSelectedCount.text = if (count == 0) {
            getString(R.string.material_picker_no_materials_selected)
        } else {
            getString(R.string.material_picker_selected_count, count)
        }
    }

    private fun saveSelections() {
        if (isSavingSelections) {
            return
        }

        isSavingSelections = true
        binding.btnSave.isEnabled = false

        val currentSelections = getCurrentMaterialSelections()

        val selectedMaterials = MaterialSelectionMapper.selectedMaterials(
            products = allProducts,
            selectedProductIds = selectedProductIds,
            currentSelections = currentSelections
        )

        if (pickerMode == MODE_SETTINGS) {
            saveSettingsSelections(selectedMaterials)
        } else {
            saveCreateSelections(selectedMaterials)
        }
    }

    private fun getCurrentMaterialSelections(): List<AquariumMaterialSelection> {
        if (pickerMode == MODE_SETTINGS) {
            val tank = currentTank ?: return emptyList()

            return tank.materials
                .filter { material ->
                    material.categoryKey == categoryKey
                }
                .map { material ->
                    AquariumMaterialSelection(
                        id = material.id,
                        productId = material.productId,
                        categoryKey = material.categoryKey,
                        categoryTitle = material.categoryTitle,
                        name = material.name,
                        brand = material.brand,
                        note = material.note
                    )
                }
        }

        return createTankViewModel.getMaterialsByCategory(categoryKey)
    }

    private fun saveCreateSelections(
        selectedMaterials: List<AquariumMaterialSelection>
    ) {
        createTankViewModel.updateTankMaterialsForCategory(
            categoryKey = categoryKey,
            materials = selectedMaterials
        )

        findNavController()
            .previousBackStackEntry
            ?.savedStateHandle
            ?.set(
                RESULT_CATEGORY_KEY,
                categoryKey
            )

        closePicker()
    }

    private fun saveSettingsSelections(
        selectedMaterials: List<AquariumMaterialSelection>
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                aquariumTankViewModel.updateTankMaterialsForCategory(
                    tankId = tankId,
                    categoryKey = categoryKey,
                    materials = selectedMaterials
                )

                closePicker()
            } catch (exception: Exception) {
                exception.printStackTrace()

                isSavingSelections = false
                binding.btnSave.isEnabled = true

                showSnackBar(
                    message = getString(R.string.aquarium_error_components_save_failed),
                    type = BaseActivity.SnackType.ERROR
                )
            }
        }
    }

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType
    ) {
        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = type
        )
    }

    private fun closePicker() {
        findNavController().navigateUp()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val CUSTOM_MATERIAL_REQUEST_KEY = "custom_material_result"
        const val ARG_MODE = "arg_mode"
        const val ARG_TANK_ID = "arg_tank_id"
        const val ARG_CATEGORY_KEY = "arg_category_key"
        const val ARG_CATEGORY_TITLE = "arg_category_title"

        const val MODE_CREATE = "mode_create"
        const val MODE_SETTINGS = "mode_settings"

        const val RESULT_KEY = "material_picker_result"
        const val RESULT_CATEGORY_KEY = "material_category_key"
    }
}
