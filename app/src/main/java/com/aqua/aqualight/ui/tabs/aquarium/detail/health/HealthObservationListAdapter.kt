package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationSnapshot
import com.aqua.aqualight.databinding.ItemHealthObservationBinding
import com.aqua.aqualight.i18n.LocaleFormatter

internal class HealthObservationListAdapter(private val open: (Long) -> Unit) :
    ListAdapter<HealthObservationSnapshot, HealthObservationListAdapter.Holder>(ObservationDiff) {

    init {
        setHasStableIds(true)
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    fun submit(page: List<HealthObservationSnapshot>) {
        submitList(page.toList())
    }

    override fun getItemId(position: Int) = getItem(position).id
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemHealthObservationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        val context = holder.itemView.context
        holder.ui.date.text = LocaleFormatter.formatDateTime(context, row.input.identity.observedAtMillis)
        holder.ui.subject.text = row.subject?.displayName ?: context.getString(R.string.tank_health_tab_algae_control)
        holder.ui.summary.text = listOf(
            context.getString(HealthObservationLabels.label(row.input.notes.followUp.phase)),
            context.getString(HealthObservationPresentation.state(row.assessment.state))).joinToString(" · ")
        holder.itemView.setOnClickListener { open(row.id) }
    }

    class Holder(val ui: ItemHealthObservationBinding) : RecyclerView.ViewHolder(ui.root)
}

private object ObservationDiff : DiffUtil.ItemCallback<HealthObservationSnapshot>() {
    override fun areItemsTheSame(oldItem: HealthObservationSnapshot, newItem: HealthObservationSnapshot) =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: HealthObservationSnapshot, newItem: HealthObservationSnapshot) =
        oldItem == newItem
}
