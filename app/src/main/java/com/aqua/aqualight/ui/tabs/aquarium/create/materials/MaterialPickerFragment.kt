package com.aqua.aqualight.ui.tabs.aquarium.create.materials

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentMaterialPickerBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.catalog.MaterialCatalog
import com.aqua.aqualight.ui.tabs.aquarium.model.SavedAquariumTank
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.util.Locale

class MaterialPickerFragment : Fragment(R.layout.fragment_material_picker) {

    interface MaterialPickerHost {
        fun closeMaterialPickerFlow()
    }

    private var _binding: FragmentMaterialPickerBinding? = null
    private val binding get() = _binding!!

    private val createTankViewModel: CreateTankViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private lateinit var categoryKey: String
    private lateinit var categoryTitle: String

    private var pickerMode: String = MODE_CREATE
    private var tankId: Long = 0L
    private var currentTank: SavedAquariumTank? = null

    private var allProducts: List<AquariumMaterial> = emptyList()
    private val selectedProductIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pickerMode = requireArguments().getString(
            ARG_MODE,
            MODE_CREATE
        )

        tankId = requireArguments().getLong(
            ARG_TANK_ID,
            0L
        )

        categoryKey = requireArguments().getString(ARG_CATEGORY_KEY).orEmpty()
        categoryTitle = requireArguments().getString(ARG_CATEGORY_TITLE).orEmpty()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentMaterialPickerBinding.bind(view)

        setupClickListeners()
        setupSearch()

        if (pickerMode == MODE_SETTINGS) {
            observeSettingsTank()
        } else {
            initializeCreateMode()
        }
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
            } ?: return@observe

            currentTank = tank

            val currentSelections = tank.materials
                .filter { material ->
                    material.categoryKey == categoryKey
                }
                .map { material ->
                    TankMaterialSelection(
                        id = material.id,
                        productId = material.productId,
                        categoryKey = material.categoryKey,
                        categoryTitle = material.categoryTitle,
                        name = material.name,
                        brand = material.brand,
                        note = material.note
                    )
                }

            refreshPickerContent(currentSelections)
        }
    }

    private fun refreshPickerContent(
        currentSelections: List<TankMaterialSelection>
    ) {
        allProducts = buildProductList(currentSelections)

        selectedProductIds.clear()
        selectedProductIds.addAll(
            currentSelections.map { selection ->
                selection.productId
            }
        )

        renderKeywords()
        renderMaterialList(allProducts)
        updateSelectedCount()
    }

    private fun buildProductList(
        currentSelections: List<TankMaterialSelection>
    ): List<AquariumMaterial> {
        val catalogProducts = MaterialCatalog.getByCategory(categoryKey)
        val catalogIds = catalogProducts.map { product ->
            product.id
        }.toSet()

        val customProducts = currentSelections
            .filterNot { selection ->
                catalogIds.contains(selection.productId)
            }
            .map { selection ->
                AquariumMaterial(
                    id = selection.productId,
                    name = selection.name,
                    brand = selection.brand,
                    categoryKey = selection.categoryKey,
                    categoryTitle = selection.categoryTitle,
                    keywords = listOf(
                        selection.categoryTitle,
                        selection.name,
                        selection.brand,
                        "custom"
                    )
                )
            }

        return catalogProducts + customProducts
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            closePicker()
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchMaterials.setText("")
        }

        binding.btnSave.setOnClickListener {
            saveSelections()
        }
    }

    private fun setupSearch() {
        binding.etSearchMaterials.addTextChangedListener(
            object : TextWatcher {
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
                    val query = s
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                    binding.btnClearSearch.isVisible = query.isNotEmpty()

                    renderMaterialList(
                        getFilteredProducts(query)
                    )
                }

                override fun afterTextChanged(s: Editable?) = Unit
            }
        )
    }

    private fun renderKeywords() {
        binding.keywordContainer.removeAllViews()

        val keywords = MaterialCatalog.getPopularKeywords(categoryKey)

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
            radius = 14.dp().toFloat()
            strokeWidth = 1.dp()
            strokeColor = Color.parseColor("#223A57")
            setCardBackgroundColor(Color.parseColor("#10233A"))
            cardElevation = 0f
            isClickable = true
            isFocusable = true

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                42.dp()
            )
            params.marginEnd = 10.dp()
            layoutParams = params

            setOnClickListener {
                binding.etSearchMaterials.setText(keyword)
                binding.etSearchMaterials.setSelection(
                    binding.etSearchMaterials.text.length
                )
            }
        }

        val text = TextView(requireContext()).apply {
            this.text = keyword.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#8FA4BE"))
            textSize = 13f
            includeFontPadding = false
            setPadding(
                18.dp(),
                0,
                18.dp(),
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
            text = "No materials found"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#8FA4BE"))
            textSize = 15f
            includeFontPadding = false

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 34.dp()
            params.bottomMargin = 18.dp()
            layoutParams = params
        }

        binding.listContainer.addView(emptyText)
    }

    private fun createMaterialCard(
        product: AquariumMaterial
    ): View {
        val isSelected = selectedProductIds.contains(product.id)

        val card = MaterialCardView(requireContext()).apply {
            radius = 18.dp().toFloat()
            strokeWidth = 1.dp()
            strokeColor = Color.parseColor(
                if (isSelected) "#2B93F6" else "#223A57"
            )
            setCardBackgroundColor(
                Color.parseColor(
                    if (isSelected) "#143456" else "#10233A"
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
            params.bottomMargin = 12.dp()
            layoutParams = params

            setOnClickListener {
                toggleSelection(product.id)

                val query = binding.etSearchMaterials.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()

                renderMaterialList(
                    getFilteredProducts(query)
                )

                updateSelectedCount()
            }
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                18.dp(),
                15.dp(),
                14.dp(),
                15.dp()
            )
        }

        val textBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            params.marginEnd = 14.dp()
            layoutParams = params
        }

        val category = TextView(requireContext()).apply {
            text = product.categoryTitle
            setTextColor(Color.parseColor("#8FA4BE"))
            textSize = 12f
            includeFontPadding = false
        }

        val name = TextView(requireContext()).apply {
            text = product.name
            setTextColor(Color.WHITE)
            textSize = 14f
            includeFontPadding = false
            maxLines = 3

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 8.dp()
            layoutParams = params
        }

        val check = TextView(requireContext()).apply {
            text = if (isSelected) "✓" else ""
            gravity = Gravity.CENTER
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            includeFontPadding = false
            setBackgroundResource(
                if (isSelected) {
                    R.drawable.bg_material_check_selected
                } else {
                    R.drawable.bg_material_check_unselected
                }
            )

            layoutParams = LinearLayout.LayoutParams(
                30.dp(),
                30.dp()
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
            text = "New $categoryTitle"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setAllCaps(false)
            cornerRadius = 16.dp()
            setBackgroundColor(Color.parseColor("#2196F3"))

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                56.dp()
            )
            params.topMargin = 10.dp()
            params.bottomMargin = 20.dp()
            layoutParams = params

            setOnClickListener {
                showNewMaterialSheet()
            }
        }
    }

    private fun showNewMaterialSheet() {
        val dialog = BottomSheetDialog(requireContext())

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                24.dp(),
                22.dp(),
                24.dp(),
                24.dp()
            )
            background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bg_aqua_bottom_sheet
            )
        }

        addSheetHeader(
            root = root,
            title = "New Material",
            dialog = dialog
        )

        val labelName = TextView(requireContext()).apply {
            text = "Material Name"
            setTextColor(Color.WHITE)
            textSize = 14f

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 18.dp()
            layoutParams = params
        }

        root.addView(labelName)

        val nameInputCard = MaterialCardView(requireContext()).apply {
            radius = 14.dp().toFloat()
            strokeWidth = 1.dp()
            strokeColor = Color.parseColor("#223A57")
            setCardBackgroundColor(Color.parseColor("#16314D"))

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                56.dp()
            )
            params.topMargin = 10.dp()
            layoutParams = params
        }

        val currentSearchText = binding.etSearchMaterials.text
            ?.toString()
            ?.trim()
            .orEmpty()

        val nameInput = EditText(requireContext()).apply {
            setText(currentSearchText)

            if (currentSearchText.isNotBlank()) {
                setSelection(currentSearchText.length)
            }

            hint = "Enter material name"
            setHintTextColor(Color.parseColor("#7F91AA"))
            setTextColor(Color.WHITE)
            textSize = 15f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = null
            setPadding(
                16.dp(),
                0,
                16.dp(),
                0
            )
        }

        nameInputCard.addView(
            nameInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(nameInputCard)

        val labelCategory = TextView(requireContext()).apply {
            text = "Category"
            setTextColor(Color.WHITE)
            textSize = 14f

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 22.dp()
            layoutParams = params
        }

        root.addView(labelCategory)

        val categoryInputCard = MaterialCardView(requireContext()).apply {
            radius = 14.dp().toFloat()
            strokeWidth = 1.dp()
            strokeColor = Color.parseColor("#223A57")
            setCardBackgroundColor(Color.parseColor("#16314D"))

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                56.dp()
            )
            params.topMargin = 10.dp()
            layoutParams = params
        }

        val categoryText = TextView(requireContext()).apply {
            text = categoryTitle
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.parseColor("#D6E2F0"))
            textSize = 15f
            setTypeface(null, Typeface.NORMAL)
            setPadding(
                16.dp(),
                0,
                16.dp(),
                0
            )
            includeFontPadding = false
        }

        categoryInputCard.addView(
            categoryText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(categoryInputCard)

        val saveButton = MaterialButton(requireContext()).apply {
            text = "Save"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setAllCaps(false)
            cornerRadius = 16.dp()
            setBackgroundColor(Color.parseColor("#2196F3"))

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                56.dp()
            )
            params.topMargin = 28.dp()
            layoutParams = params

            setOnClickListener {
                val materialName = nameInput.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()

                if (materialName.isBlank()) {
                    nameInput.error = "Required"
                    return@setOnClickListener
                }

                addCustomMaterial(materialName)
                dialog.dismiss()
            }
        }

        root.addView(saveButton)

        val cancel = TextView(requireContext()).apply {
            text = "Cancel"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#8FA4BE"))
            textSize = 15f
            setPadding(
                0,
                18.dp(),
                0,
                0
            )

            setOnClickListener {
                dialog.dismiss()
            }
        }

        root.addView(
            cancel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        dialog.setContentView(root)

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )

            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
        }

        dialog.show()
    }

    private fun addSheetHeader(
        root: LinearLayout,
        title: String,
        dialog: BottomSheetDialog
    ) {
        val header = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                46.dp()
            )
        }

        val titleView = TextView(requireContext()).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val closeView = TextView(requireContext()).apply {
            text = "×"
            setTextColor(Color.WHITE)
            textSize = 34f
            gravity = Gravity.CENTER
            includeFontPadding = false

            setOnClickListener {
                dialog.dismiss()
            }

            val params = FrameLayout.LayoutParams(
                44.dp(),
                44.dp(),
                Gravity.END or Gravity.CENTER_VERTICAL
            )
            layoutParams = params
        }

        header.addView(titleView)
        header.addView(closeView)

        root.addView(header)
    }

    private fun addCustomMaterial(
        materialName: String
    ) {
        val existingProduct = allProducts.firstOrNull { product ->
            product.name.equals(materialName, ignoreCase = true)
        }

        val productToSelect = existingProduct ?: AquariumMaterial(
            id = buildCustomMaterialId(materialName),
            name = materialName,
            brand = "",
            categoryKey = categoryKey,
            categoryTitle = categoryTitle,
            keywords = listOf(
                categoryTitle,
                materialName,
                "custom"
            )
        )

        if (existingProduct == null) {
            allProducts = allProducts + productToSelect
        }

        selectedProductIds.add(productToSelect.id)

        binding.etSearchMaterials.setText("")
        renderMaterialList(allProducts)
        updateSelectedCount()
    }

    private fun buildCustomMaterialId(
        materialName: String
    ): String {
        val safeName = materialName
            .lowercase(Locale.ENGLISH)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

        return "custom_${categoryKey}_${safeName}_${System.currentTimeMillis()}"
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
            "No materials selected"
        } else {
            "$count selected"
        }
    }

    private fun saveSelections() {
        val currentSelections = getCurrentMaterialSelections()

        val selectedMaterials = allProducts
            .filter { product ->
                selectedProductIds.contains(product.id)
            }
            .map { product ->
                val existingSelection = currentSelections.firstOrNull { selection ->
                    selection.productId == product.id
                }

                TankMaterialSelection(
                    id = existingSelection?.id ?: System.nanoTime(),
                    productId = product.id,
                    categoryKey = product.categoryKey,
                    categoryTitle = product.categoryTitle,
                    name = product.name,
                    brand = product.brand,
                    note = existingSelection?.note.orEmpty()
                )
            }

        if (pickerMode == MODE_SETTINGS) {
            saveSettingsSelections(selectedMaterials)
        } else {
            saveCreateSelections(selectedMaterials)
        }
    }

    private fun getCurrentMaterialSelections(): List<TankMaterialSelection> {
        if (pickerMode == MODE_SETTINGS) {
            val tank = currentTank ?: return emptyList()

            return tank.materials
                .filter { material ->
                    material.categoryKey == categoryKey
                }
                .map { material ->
                    TankMaterialSelection(
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
        selectedMaterials: List<TankMaterialSelection>
    ) {
        createTankViewModel.updateTankMaterialsForCategory(
            categoryKey = categoryKey,
            materials = selectedMaterials
        )

        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(
                RESULT_CATEGORY_KEY to categoryKey
            )
        )

        closePicker()
    }

    private fun saveSettingsSelections(
        selectedMaterials: List<TankMaterialSelection>
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            aquariumTankViewModel.updateTankMaterialsForCategory(
                tankId = tankId,
                categoryKey = categoryKey,
                materials = selectedMaterials
            )

            closePicker()
        }
    }

    private fun closePicker() {
        (requireParentFragment() as? MaterialPickerHost)
            ?.closeMaterialPickerFlow()
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_MODE = "arg_mode"
        private const val ARG_TANK_ID = "arg_tank_id"
        private const val ARG_CATEGORY_KEY = "arg_category_key"
        private const val ARG_CATEGORY_TITLE = "arg_category_title"

        private const val MODE_CREATE = "mode_create"
        private const val MODE_SETTINGS = "mode_settings"

        const val RESULT_KEY = "material_picker_result"
        const val RESULT_CATEGORY_KEY = "material_category_key"

        fun newCreateInstance(
            categoryKey: String,
            categoryTitle: String
        ): MaterialPickerFragment {
            return MaterialPickerFragment().apply {
                arguments = bundleOf(
                    ARG_MODE to MODE_CREATE,
                    ARG_CATEGORY_KEY to categoryKey,
                    ARG_CATEGORY_TITLE to categoryTitle
                )
            }
        }

        fun newSettingsInstance(
            tankId: Long,
            categoryKey: String,
            categoryTitle: String
        ): MaterialPickerFragment {
            return MaterialPickerFragment().apply {
                arguments = bundleOf(
                    ARG_MODE to MODE_SETTINGS,
                    ARG_TANK_ID to tankId,
                    ARG_CATEGORY_KEY to categoryKey,
                    ARG_CATEGORY_TITLE to categoryTitle
                )
            }
        }

        fun newInstance(
            categoryKey: String,
            categoryTitle: String
        ): MaterialPickerFragment {
            return newCreateInstance(
                categoryKey = categoryKey,
                categoryTitle = categoryTitle
            )
        }
    }
}