package com.aqua.aqualight.ui.tabs.aquarium.create.materials

import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.text.setTextSizeResource
import com.aqua.aqualight.ui.tabs.aquarium.catalog.material.AquariumMaterial
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

private const val CATEGORY_TAG = "material_picker_category"
private const val NAME_TAG = "material_picker_name"
private const val CHECK_TAG = "material_picker_check"

class MaterialPickerAdapter(
    private val categoryTitle: String,
    private val onProductClick: (String) -> Unit,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var products: List<AquariumMaterial> = emptyList()
    private val selectedProductIds = mutableSetOf<String>()

    fun submitProducts(
        products: List<AquariumMaterial>,
        selectedProductIds: Set<String>
    ) {
        this.products = products
        this.selectedProductIds.clear()
        this.selectedProductIds.addAll(selectedProductIds)
        notifyDataSetChanged()
    }

    fun updateSelection(
        productId: String,
        isSelected: Boolean
    ) {
        if (isSelected) {
            selectedProductIds.add(productId)
        } else {
            selectedProductIds.remove(productId)
        }

        val position = products.indexOfFirst { product ->
            product.id == productId
        }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int {
        return if (products.isEmpty()) {
            EMPTY_STATE_ITEM_COUNT + FOOTER_ITEM_COUNT
        } else {
            products.size + FOOTER_ITEM_COUNT
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (products.isEmpty()) {
            return if (position == 0) VIEW_TYPE_EMPTY else VIEW_TYPE_ADD
        }

        return if (position == products.size) {
            VIEW_TYPE_ADD
        } else {
            VIEW_TYPE_PRODUCT
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_PRODUCT -> ProductViewHolder(
                card = MaterialPickerCardFactory.createProductCard(parent),
                onProductClick = onProductClick
            )

            VIEW_TYPE_EMPTY -> EmptyViewHolder(
                MaterialPickerCardFactory.createEmptyView(parent)
            )

            VIEW_TYPE_ADD -> AddViewHolder(
                button = LayoutInflater.from(parent.context).inflate(
                    R.layout.item_material_picker_inline_add_button,
                    parent,
                    false
                ) as MaterialButton,
                categoryTitle = categoryTitle,
                onAddClick = onAddClick
            )

            else -> error("Unknown material picker view type: $viewType")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        when (holder) {
            is ProductViewHolder -> {
                val product = products[position]
                holder.bind(
                    product = product,
                    isSelected = selectedProductIds.contains(product.id)
                )
            }

            is AddViewHolder -> holder.bind()
        }
    }

    private class ProductViewHolder(
        private val card: MaterialCardView,
        private val onProductClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(card) {

        private val category: TextView = card.findViewWithTag(CATEGORY_TAG)
        private val name: TextView = card.findViewWithTag(NAME_TAG)
        private val check: TextView = card.findViewWithTag(CHECK_TAG)

        fun bind(
            product: AquariumMaterial,
            isSelected: Boolean
        ) {
            category.text = product.categoryTitle
            name.text = product.name

            card.strokeColor = ContextCompat.getColor(
                card.context,
                if (isSelected) {
                    R.color.aqua_card_accent
                } else {
                    R.color.aqua_card_outline
                }
            )
            card.setCardBackgroundColor(
                ContextCompat.getColor(
                    card.context,
                    if (isSelected) {
                        R.color.aqua_card_surface_pressed
                    } else {
                        R.color.aqua_card_surface
                    }
                )
            )

            check.text = if (isSelected) {
                card.context.getString(R.string.aqua_selected_symbol)
            } else {
                ""
            }
            check.setBackgroundResource(
                if (isSelected) {
                    R.drawable.bg_material_check_selected
                } else {
                    R.drawable.bg_material_check_unselected
                }
            )

            card.setOnClickListener {
                onProductClick(product.id)
            }
        }
    }

    private class EmptyViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view)

    private class AddViewHolder(
        private val button: MaterialButton,
        private val categoryTitle: String,
        private val onAddClick: () -> Unit
    ) : RecyclerView.ViewHolder(button) {

        fun bind() {
            button.text = button.context.getString(
                R.string.material_picker_new_title,
                categoryTitle
            )
            button.setOnClickListener {
                onAddClick()
            }
        }
    }

    private companion object {
        const val VIEW_TYPE_PRODUCT = 1
        const val VIEW_TYPE_EMPTY = 2
        const val VIEW_TYPE_ADD = 3

        const val EMPTY_STATE_ITEM_COUNT = 1
        const val FOOTER_ITEM_COUNT = 1
    }
}

private object MaterialPickerCardFactory {

    fun createProductCard(parent: ViewGroup): MaterialCardView {
        val context = parent.context
        val resources = context.resources

        return MaterialCardView(context).apply {
            radius = resources.getDimensionPixelOffset(
                R.dimen.aqua_size_16
            ).toFloat()
            strokeWidth = resources.getDimensionPixelOffset(
                R.dimen.aqua_size_1
            )
            cardElevation = 0f
            useCompatPadding = false
            isClickable = true
            isFocusable = true
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = resources.getDimensionPixelOffset(
                    R.dimen.aqua_size_9
                )
            }
            addView(createProductRow(parent))
        }
    }

    private fun createProductRow(parent: ViewGroup): LinearLayout {
        val context = parent.context
        val resources = context.resources

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = resources.getDimensionPixelOffset(
                R.dimen.aqua_size_74
            )
            setPadding(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_14),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_10),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_12),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_10)
            )
            addView(createProductTextBox(parent))
            addView(createCheckView(parent))
        }
    }

    private fun createProductTextBox(parent: ViewGroup): LinearLayout {
        val context = parent.context
        val resources = context.resources

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = resources.getDimensionPixelOffset(
                    R.dimen.aqua_size_12
                )
            }
            addView(createCategoryView(parent))
            addView(createNameView(parent))
        }
    }

    private fun createCategoryView(parent: ViewGroup): TextView {
        val context = parent.context

        return TextView(context).apply {
            tag = CATEGORY_TAG
            setTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.aqua_card_text_secondary
                )
            )
            setTextSizeResource(R.dimen.aqua_text_size_micro_plus)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    private fun createNameView(parent: ViewGroup): TextView {
        val context = parent.context
        val resources = context.resources

        return TextView(context).apply {
            tag = NAME_TAG
            setTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.aqua_card_text_primary
                )
            )
            setTextSizeResource(R.dimen.aqua_text_size_body_compact)
            setTypeface(null, Typeface.NORMAL)
            includeFontPadding = false
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = resources.getDimensionPixelOffset(
                    R.dimen.aqua_size_6
                )
            }
        }
    }

    private fun createCheckView(parent: ViewGroup): TextView {
        val context = parent.context
        val resources = context.resources

        return TextView(context).apply {
            tag = CHECK_TAG
            gravity = Gravity.CENTER
            setTextSizeResource(R.dimen.aqua_text_size_caption)
            setTypeface(null, Typeface.BOLD)
            setTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.aqua_content_on_dark
                )
            )
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelOffset(R.dimen.aqua_size_24),
                resources.getDimensionPixelOffset(R.dimen.aqua_size_24)
            )
        }
    }

    fun createEmptyView(parent: ViewGroup): TextView {
        val context = parent.context
        val resources = context.resources

        return TextView(context).apply {
            text = context.getString(
                R.string.material_picker_no_materials_found
            )
            gravity = Gravity.CENTER
            setTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.aqua_state_text_secondary
                )
            )
            setTextSize(
                android.util.TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(
                    R.dimen.aqua_text_size_state_title_small
                )
            )
            includeFontPadding = false
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = resources.getDimensionPixelOffset(
                    R.dimen.aqua_size_34
                )
                bottomMargin = resources.getDimensionPixelOffset(
                    R.dimen.aqua_size_18
                )
            }
        }
    }
}
