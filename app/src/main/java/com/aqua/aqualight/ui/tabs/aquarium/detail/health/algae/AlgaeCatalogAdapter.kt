package com.aqua.aqualight.ui.tabs.aquarium.detail.health.algae

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeTypeId
import com.aqua.aqualight.databinding.ItemAlgaeCatalogCardBinding

class AlgaeCatalogAdapter(
    private val onClick: (AlgaeTypeId) -> Unit
) : RecyclerView.Adapter<AlgaeCatalogAdapter.ViewHolder>() {

    private var items: List<AlgaeUiDefinition> = emptyList()
    private var selectedId: AlgaeTypeId? = null

    fun submitItems(
        definitions: List<AlgaeUiDefinition>,
        selected: AlgaeTypeId? = selectedId
    ) {
        items = definitions
        selectedId = selected
        notifyDataSetChanged()
    }

    fun setSelected(id: AlgaeTypeId?) {
        if (selectedId == id) {
            return
        }
        selectedId = id
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlgaeCatalogCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemAlgaeCatalogCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AlgaeUiDefinition) {
            val context = binding.root.context
            val selected = item.id == selectedId

            binding.ivAlgaeImage.setImageResource(item.imageRes)
            binding.tvAlgaeName.setText(item.nameRes)
            binding.cardRoot.strokeWidth = context.resources.getDimensionPixelSize(
                if (selected) R.dimen.aqua_size_2 else R.dimen.aqua_size_1
            )
            binding.cardRoot.strokeColor = ContextCompat.getColor(
                context,
                if (selected) R.color.aqua_accent_primary else R.color.aqua_card_metric_outline
            )
            binding.cardRoot.setOnClickListener {
                onClick(item.id)
            }
        }
    }
}
