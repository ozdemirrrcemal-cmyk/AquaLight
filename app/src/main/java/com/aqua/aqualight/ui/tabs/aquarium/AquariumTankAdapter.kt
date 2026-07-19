package com.aqua.aqualight.ui.tabs.aquarium

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.databinding.ItemAquariumTankBinding
import com.aqua.aqualight.i18n.DateOnly
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.text.resolve
import com.aqua.aqualight.ui.tabs.maintenance.TankCareSummaryUi
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class AquariumTankAdapter(
  private val onTankClick: (AquariumTankSnapshot) -> Unit,
  private val onTankLongClick: (AquariumTankSnapshot) -> Unit
) : ListAdapter<AquariumTankSnapshot, AquariumTankAdapter.TankViewHolder>(DiffCallback) {

  private var isDeleteMode: Boolean = false
  private var selectedTankIds: Set<Long> = emptySet()
  private var careSummaryByTankId: Map<Long, TankCareSummaryUi> = emptyMap()

  fun setDeleteMode(enabled: Boolean, selectedIds: Set<Long>) {
    isDeleteMode = enabled
    selectedTankIds = selectedIds.toSet()
    notifyDataSetChanged()
  }

  fun setCareSummaryByTankId(summaries: Map<Long, TankCareSummaryUi>) {
    careSummaryByTankId = summaries
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TankViewHolder {
    val binding = ItemAquariumTankBinding.inflate(
      LayoutInflater.from(parent.context),
      parent,
      false
    )
    return TankViewHolder(binding, onTankClick, onTankLongClick)
  }

  override fun onBindViewHolder(holder: TankViewHolder, position: Int) {
    val tank = getItem(position)
    holder.bind(
      tank = tank,
      careSummary = careSummaryByTankId[tank.id],
      isDeleteMode = isDeleteMode,
      isSelected = selectedTankIds.contains(tank.id)
    )
  }

  class TankViewHolder(
    private val binding: ItemAquariumTankBinding,
    private val onTankClick: (AquariumTankSnapshot) -> Unit,
    private val onTankLongClick: (AquariumTankSnapshot) -> Unit
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(
      tank: AquariumTankSnapshot,
      careSummary: TankCareSummaryUi?,
      isDeleteMode: Boolean,
      isSelected: Boolean
    ) {
      binding.tvTankName.text = tank.name
      val context = binding.root.context

      binding.tvTankDay.text = getTankDayText(
        context = context,
        setupDateEpochDay = tank.setupDateEpochDay
      )
      binding.tvCareInfo.text = getCareInfoText(context, careSummary)
      binding.tvTankSize.text = getTankSizeText(context, tank)
      binding.tvSetupDate.text = getSetupDateText(
        context = context,
        setupDateEpochDay = tank.setupDateEpochDay
      )

      bindTankPhoto(tank)
      binding.selectionCircle.isVisible = isDeleteMode
      binding.selectionCircle.setImageResource(if (isSelected) R.drawable.ic_check else 0)
      binding.selectionCircle.setBackgroundResource(
        if (isSelected) R.drawable.bg_tank_selection_selected
        else R.drawable.bg_tank_selection_unselected
      )
      binding.root.alpha = if (isDeleteMode && !isSelected) 0.72f else 1f
      binding.root.setOnClickListener { onTankClick(tank) }
      binding.root.setOnLongClickListener {
        onTankLongClick(tank)
        true
      }
    }

    private fun bindTankPhoto(tank: AquariumTankSnapshot) {
      val photoUri = tank.photoUri?.trim()?.takeIf(String::isNotEmpty)
      val photoKey = photoUri ?: DEFAULT_TANK_PHOTO_KEY
      if (binding.imgTankPhoto.tag == photoKey) return
      binding.imgTankPhoto.tag = photoKey

      if (photoUri == null) {
        binding.imgTankPhoto.load(R.drawable.nature_aquarium) { crossfade(false) }
        return
      }

      binding.imgTankPhoto.setImageDrawable(null)
      binding.imgTankPhoto.load(Uri.parse(photoUri)) {
        error(R.drawable.nature_aquarium)
        crossfade(false)
      }
    }

    private fun getCareInfoText(
      context: Context,
      careSummary: TankCareSummaryUi?
    ): String {
      val lastWaterChange = careSummary?.lastWaterChangeText
        ?.let { context.resolve(it) }
        ?: context.getString(R.string.aquarium_no_value_placeholder)
      val lastTrim = careSummary?.lastTrimText
        ?.let { context.resolve(it) }
        ?: context.getString(R.string.aquarium_no_value_placeholder)

      return context.getString(
        R.string.aquarium_care_info_format,
        lastWaterChange,
        lastTrim
      )
    }

    private fun getTankSizeText(context: Context, tank: AquariumTankSnapshot): String {
      return context.getString(
        R.string.aquarium_tank_size_card_format,
        tank.widthCm,
        tank.lengthCm,
        tank.heightCm
      )
    }

    private fun getSetupDateText(
      context: Context,
      setupDateEpochDay: Long?
    ): String {
      if (setupDateEpochDay == null) {
        return context.getString(R.string.aquarium_setup_date_empty)
      }

      return context.getString(
        R.string.aquarium_setup_date_card_format,
        LocaleFormatter.formatDateEpochDay(context, setupDateEpochDay)
      )
    }

    private fun getTankDayText(
      context: Context,
      setupDateEpochDay: Long?
    ): String {
      val setupDate = setupDateEpochDay
        ?.let(DateOnly::toLocalDate)
        ?: LocalDate.now()
      val day = ChronoUnit.DAYS
        .between(setupDate, LocalDate.now())
        .coerceAtLeast(0L)

      return context.getString(R.string.aquarium_day_format, day)
    }

    companion object {
      private const val DEFAULT_TANK_PHOTO_KEY = "default_tank_photo"
    }
  }

  private object DiffCallback : DiffUtil.ItemCallback<AquariumTankSnapshot>() {
    override fun areItemsTheSame(
      oldItem: AquariumTankSnapshot,
      newItem: AquariumTankSnapshot
    ): Boolean = oldItem.id == newItem.id

    override fun areContentsTheSame(
      oldItem: AquariumTankSnapshot,
      newItem: AquariumTankSnapshot
    ): Boolean = oldItem == newItem
  }
}
