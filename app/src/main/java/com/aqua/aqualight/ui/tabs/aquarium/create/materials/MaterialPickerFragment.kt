package com.aqua.aqualight.ui.tabs.aquarium.create.materials

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentMaterialPickerBinding
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.catalog.MaterialCatalog
import com.google.android.material.card.MaterialCardView

class MaterialPickerFragment : Fragment(R.layout.fragment_material_picker) {

    private var _binding: FragmentMaterialPickerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private lateinit var categoryKey: String
    private lateinit var categoryTitle: String

    private var allProducts: List<AquariumMaterial> = emptyList()
    private val selectedProductIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        categoryKey = requireArguments().getString(ARG_CATEGORY_KEY).orEmpty()
        categoryTitle = requireArguments().getString(ARG_CATEGORY_TITLE).orEmpty()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentMaterialPickerBinding.bind(view)

        allProducts = MaterialCatalog.getByCategory(categoryKey)

        selectedProductIds.clear()
        selectedProductIds.addAll(
            viewModel.getMaterialsByCategory(categoryKey)
                .map { it.productId }
        )

        setupClickListeners()
        setupSearch()
        renderKeywords()
        renderMaterialList(allProducts)
        updateSelectedCount()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            (requireParentFragment() as? CreateTankFragment)
                ?.closeMaterialPickerFlow()
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

                    val filteredProducts = MaterialCatalog.search(
                        categoryKey = categoryKey,
                        query = query
                    )

                    renderMaterialList(filteredProducts)
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
            setPadding(18.dp(), 0, 18.dp(), 0)
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

    private fun renderMaterialList(
        products: List<AquariumMaterial>
    ) {
        binding.listContainer.removeAllViews()

        if (products.isEmpty()) {
            showEmptyState()
            return
        }

        products.forEach { product ->
            binding.listContainer.addView(
                createMaterialCard(product)
            )
        }
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

                val filteredProducts = MaterialCatalog.search(
                    categoryKey = categoryKey,
                    query = query
                )

                renderMaterialList(filteredProducts)
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
        val selectedMaterials = allProducts
            .filter { selectedProductIds.contains(it.id) }
            .map { product ->
                TankMaterialSelection(
                    productId = product.id,
                    categoryKey = product.categoryKey,
                    categoryTitle = product.categoryTitle,
                    name = product.name,
                    brand = product.brand
                )
            }

        viewModel.updateTankMaterialsForCategory(
            categoryKey = categoryKey,
            materials = selectedMaterials
        )

        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(
                RESULT_CATEGORY_KEY to categoryKey
            )
        )

        (requireParentFragment() as? CreateTankFragment)
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
        private const val ARG_CATEGORY_KEY = "arg_category_key"
        private const val ARG_CATEGORY_TITLE = "arg_category_title"

        const val RESULT_KEY = "material_picker_result"
        const val RESULT_CATEGORY_KEY = "material_category_key"

        fun newInstance(
            categoryKey: String,
            categoryTitle: String
        ): MaterialPickerFragment {
            return MaterialPickerFragment().apply {
                arguments = bundleOf(
                    ARG_CATEGORY_KEY to categoryKey,
                    ARG_CATEGORY_TITLE to categoryTitle
                )
            }
        }
    }
}