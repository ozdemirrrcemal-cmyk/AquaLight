package com.aqua.aqualight.ui.tabs.aquarium.detail.livestock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.google.android.material.button.MaterialButton

internal class TankLivestockAddFooterAdapter(
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<TankLivestockAddFooterAdapter.ViewHolder>() {

    private var category: String = LivestockCategories.FISH

    fun setCategory(
        category: String
    ) {
        if (this.category == category) {
            return
        }

        this.category = category
        notifyItemChanged(0)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val button = LayoutInflater.from(parent.context).inflate(
            R.layout.item_catalog_inline_add_button,
            parent,
            false
        ) as MaterialButton

        return ViewHolder(
            button = button,
            onAddClick = onAddClick
        )
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(category)
    }

    override fun getItemCount(): Int = 1

    class ViewHolder(
        private val button: MaterialButton,
        private val onAddClick: () -> Unit
    ) : RecyclerView.ViewHolder(button) {

        fun bind(
            category: String
        ) {
            val context = button.context
            button.text = context.getString(
                R.string.livestock_picker_new_category,
                context.getString(LivestockCategories.labelRes(category))
            )
            button.setOnClickListener {
                onAddClick()
            }
        }
    }
}
