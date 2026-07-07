package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentTankLivestockFormBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.data.aquarium.catalog.livestock.LivestockCategories
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.data.aquarium.util.AquariumIdGenerator
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.text.Editable
import android.text.TextWatcher
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.ui.tabs.aquarium.navigation.TankDetailTabArgs


class TankDetailLivestockFormFragment :
Fragment(R.layout.fragment_tank_livestock_form) {

    private val args: TankDetailLivestockFormFragmentArgs by navArgs()


    private var _binding: FragmentTankLivestockFormBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var editingLivestockId: Long = 0L
    private var selectedCategory: String = LivestockCategories.FISH
    private var selectedQuantity: Int = 1
    private var selectedAddedDateMillis: Long = System.currentTimeMillis()
    private var hasLoadedEditingLivestock: Boolean = false
    private var hasShownMissingDataDialog: Boolean = false
    private var isDeletingLivestock: Boolean = false
    private var isSavingLivestock: Boolean = false
    private var isNavigatingBack: Boolean = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentTankLivestockFormBinding.bind(view)

        readArguments()
        setupInitialUi()
        setupClickListeners()
        setupSystemBackButton()
        setupNamePreviewListener()
        renderCategoryOptions()
        updatePreview()
        updateQuantity()
        updateDateText()
        observeEditingLivestockIfNeeded()
    }

    private fun readArguments() {
        tankId = args.tankId
        editingLivestockId = args.livestockId

        selectedCategory = LivestockCategories.FISH
        selectedQuantity = 1
        selectedAddedDateMillis = System.currentTimeMillis()
    }

    private fun observeEditingLivestockIfNeeded() {
        if (editingLivestockId <= 0L) {
            return
        }

        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) {
            tanks ->
            if (isNavigatingBack || isDeletingLivestock) {
                return@observe
            }

            val tank = tanks.firstOrNull {
                tank ->
                tank.id == tankId
            }

            if (tank == null) {
                showMissingDataDialogAndClose(
                    title = getString(R.string.aquarium_tank_not_found_title),
                    message = getString(R.string.aquarium_tank_no_longer_exists_message)
                )
                return@observe
            }

            val livestock = tank.livestock.firstOrNull {
                item ->
                item.id == editingLivestockId
            }

            if (livestock == null) {
                if (!hasLoadedEditingLivestock) {
                    showMissingDataDialogAndClose(
                        title = getString(R.string.aquarium_livestock_not_found_title),
                        message = getString(R.string.aquarium_livestock_no_longer_exists_message)
                    )
                }

                return@observe
            }

            if (hasLoadedEditingLivestock) {
                return@observe
            }

            bindEditingLivestock(livestock)
            hasLoadedEditingLivestock = true
        }
    }

    private fun bindEditingLivestock(
        livestock: SavedAquariumLivestock
    ) {
        selectedCategory = livestock.category.ifBlank {
            LivestockCategories.FISH
        }

        selectedQuantity = livestock.quantity.coerceAtLeast(1)

        selectedAddedDateMillis = livestock.addedDateMillis
        ?.takeIf {
            millis ->
            millis > 0L
        } ?: System.currentTimeMillis()

        binding.etLifeName.setText(livestock.name)
        binding.etLifeNote.setText(livestock.note)

        renderCategoryOptions()
        updatePreview()
        updateQuantity()
        updateDateText()
    }

    private fun showMissingDataDialogAndClose(
        title: String,
        message: String
    ) {
        if (hasShownMissingDataDialog) {
            return
        }

        hasShownMissingDataDialog = true

        DialogManager.showInfoDialog(
            context = requireContext(),
            type = DialogType.ERROR,
            title = title,
            message = message,
            onDismiss = {
                closeForm()
            }
        )
    }

    private fun setupInitialUi() {
        val isEditing = editingLivestockId > 0L

        setupHeader(
            title = if (isEditing) {
                getString(R.string.aquarium_livestock_form_title_edit)
            } else {
                getString(R.string.aquarium_livestock_form_title_add)
            }
        )

        binding.btnSaveLife.text = if (isEditing) {
            getString(R.string.aquarium_action_save_changes)
        } else {
            getString(R.string.aquarium_text_save_livestock)
        }

        binding.btnDeleteLife.isVisible = isEditing
    }

    private fun setupHeader(
        title: String
    ) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = title,
                onBackClick = {
                    closeForm()
                }
            )
        )
    }

    private fun setupClickListeners() {

        binding.btnDecreaseQuantity.setOnClickListener {
            if (selectedQuantity > 1) {
                selectedQuantity--
                updateQuantity()
            }
        }

        binding.btnIncreaseQuantity.setOnClickListener {
            selectedQuantity++
            updateQuantity()
        }

        binding.rowAddedDate.setOnClickListener {
            showAddedDateSheet()
        }

        binding.btnSaveLife.setOnClickListener {
            saveLivestock()
        }

        binding.btnDeleteLife.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun setupSystemBackButton() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    closeForm()
                }
            }
        )
    }

    private fun setupNamePreviewListener() {
        binding.etLifeName.addTextChangedListener(
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
                    updatePreview()
                }

                override fun afterTextChanged(
                    s: Editable?
                ) = Unit
            }
        )
    }

    private fun renderCategoryOptions() {
        binding.categoryGrid.removeAllViews()

        LivestockCategories.all.forEach {
            category ->
            binding.categoryGrid.addView(
                createCategoryOption(
                    category = category,
                    selected = category == selectedCategory
                )
            )
        }
    }

    private fun createCategoryOption(
        category: String,
        selected: Boolean
    ): View {
        return TextView(requireContext()).apply {
            text = category
            gravity = Gravity.CENTER
            textSize = 13.5f
            setTextColor(Color.WHITE)
            setTypeface(
                null,
                if (selected) Typeface.BOLD else Typeface.NORMAL
            )
            includeFontPadding = false

            background = createRoundedDrawable(
                color = if (selected) "#1C3D63" else "#10233A",
                radiusPx = 15.dp(),
                strokeColor = if (selected) "#2196F3" else "#223A57",
                strokeWidthPx = 1.dp()
            )

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = 46.dp()
                columnSpec = GridLayout.spec(
                    GridLayout.UNDEFINED,
                    1f
                )
                setMargins(
                    0,
                    0,
                    8.dp(),
                    8.dp()
                )
            }

            layoutParams = params

            setOnClickListener {
                selectedCategory = category
                renderCategoryOptions()
                updatePreview()
            }
        }
    }

    private fun updatePreview() {
        val lifeName = binding.etLifeName.text
        .toString()
        .trim()

        binding.tvLifePreviewTitle.text = lifeName.ifBlank {
            getString(R.string.aquarium_livestock_default_title)
        }

        binding.ivLifeIconPreview.setImageResource(
            getCategoryIcon(selectedCategory)
        )

        binding.ivLifeIconPreview.setColorFilter(Color.WHITE)

        binding.ivLifeIconPreview.background = createIconBackground(
            color = getCategoryColor(selectedCategory)
        )

        binding.tvLifeCategoryPreview.text = selectedCategory
    }

    private fun updateQuantity() {
        binding.tvQuantityValue.text = selectedQuantity.toString()
    }

    private fun updateDateText() {
        val formatter = SimpleDateFormat(
            "dd MMM yyyy",
            Locale.getDefault()
        )

        binding.tvAddedDateValue.text = formatter.format(
            Date(selectedAddedDateMillis)
        )
    }

    private fun saveLivestock() {
        if (isSavingLivestock) {
            return
        }

        val name = binding.etLifeName.text
        .toString()
        .trim()

        if (name.length < 2) {
            showSnackBar(
                message = getString(R.string.aquarium_validation_livestock_name_min),
                type = BaseActivity.SnackType.WARNING
            )
            return
        }

        val livestock = SavedAquariumLivestock(
            id = if (editingLivestockId > 0L) {
                editingLivestockId
            } else {
                AquariumIdGenerator.newLong()
            },
            name = name,
            category = selectedCategory,
            quantity = selectedQuantity.coerceAtLeast(1),
            addedDateMillis = selectedAddedDateMillis,
            note = binding.etLifeNote.text
            .toString()
            .trim()
        )

        isSavingLivestock = true
        binding.btnSaveLife.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (editingLivestockId > 0L) {
                    aquariumTankViewModel.updateLivestockInTank(
                        tankId = tankId,
                        livestock = livestock
                    )
                } else {
                    aquariumTankViewModel.addLivestockToTank(
                        tankId = tankId,
                        livestock = livestock
                    )
                }

                closeForm()
            } catch (exception: Exception) {
                exception.printStackTrace()

                isSavingLivestock = false
                binding.btnSaveLife.isEnabled = true

                showSnackBar(
                    message = getString(R.string.aquarium_error_livestock_save_failed),
                    type = BaseActivity.SnackType.ERROR
                )
            }
        }
    }

    private fun showDeleteConfirmation() {
        DialogManager.showConfirmDialog(
            context = requireContext(),
            type = DialogType.WARNING,
            title = getString(R.string.aquarium_delete_livestock_title),
            message = getString(R.string.aquarium_delete_livestock_message),
            confirmTextResId = R.string.delete,
            cancelTextResId = R.string.cancel,
            onConfirm = {
                deleteLivestock()
            }
        )
    }

    private fun deleteLivestock() {
        if (editingLivestockId <= 0L || isDeletingLivestock) {
            return
        }

        isDeletingLivestock = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                aquariumTankViewModel.removeLivestockFromTank(
                    tankId = tankId,
                    livestockId = editingLivestockId
                )

                closeForm()
            } catch (exception: Exception) {
                exception.printStackTrace()

                isDeletingLivestock = false

                showSnackBar(
                    message = getString(R.string.aquarium_error_livestock_delete_failed),
                    type = BaseActivity.SnackType.ERROR
                )
            }
        }
    }


    private fun showAddedDateSheet() {
        val dialog = BottomSheetDialog(requireContext(), R.style.AquaBottomSheetDialogTheme)

        val calendar = Calendar.getInstance().apply {
            timeInMillis = selectedAddedDateMillis
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                22.dp(),
                14.dp(),
                22.dp(),
                24.dp()
            )
            background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bg_bottomsheet_rounded
            )
        }

        val handle = View(requireContext()).apply {
            background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bg_bottomsheet_handle
            )
            layoutParams = LinearLayout.LayoutParams(
                42.dp(),
                4.dp()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 16.dp()
            }
        }

        val title = TextView(requireContext()).apply {
            text = getString(R.string.aquarium_added_date_label)
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_bottom_sheet_text_primary
                )
            )
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val pickerRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 24.dp()
            layoutParams = params
        }

        val monthNames = Array(12) { index ->
            DateFormatSymbols(Locale.getDefault())
                .months[index]
                .replaceFirstChar { char ->
                    if (char.isLowerCase()) {
                        char.titlecase(Locale.getDefault())
                    } else {
                        char.toString()
                    }
                }
        }

        val dayPicker = createDateNumberPicker().apply {
            minValue = 1
            maxValue = 31
            value = calendar.get(Calendar.DAY_OF_MONTH)
        }

        val monthPicker = createDateNumberPicker().apply {
            minValue = 0
            maxValue = 11
            displayedValues = monthNames
            value = calendar.get(Calendar.MONTH)
        }

        val yearPicker = createDateNumberPicker().apply {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            minValue = currentYear - 20
            maxValue = currentYear + 5
            value = calendar.get(Calendar.YEAR)
        }

        fun updateDayMax() {
            val tempCalendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, yearPicker.value)
                set(Calendar.MONTH, monthPicker.value)
                set(Calendar.DAY_OF_MONTH, 1)
            }

            val maxDay = tempCalendar.getActualMaximum(
                Calendar.DAY_OF_MONTH
            )

            dayPicker.maxValue = maxDay

            if (dayPicker.value > maxDay) {
                dayPicker.value = maxDay
            }
        }

        monthPicker.setOnValueChangedListener { _, _, _ ->
            updateDayMax()
        }

        yearPicker.setOnValueChangedListener { _, _, _ ->
            updateDayMax()
        }

        updateDayMax()

        pickerRow.addView(dayPicker)
        pickerRow.addView(monthPicker)
        pickerRow.addView(yearPicker)

        val saveButton = com.google.android.material.button.MaterialButton(
            requireContext()
        ).apply {
            text = getString(R.string.common_save)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setAllCaps(false)
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_bottom_sheet_on_primary
                )
            )
            cornerRadius = 18.dp()
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.aqua_bottom_sheet_primary
                )
            )

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                52.dp()
            )
            params.topMargin = 24.dp()
            layoutParams = params

            setOnClickListener {
                val selectedCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, yearPicker.value)
                    set(Calendar.MONTH, monthPicker.value)
                    set(Calendar.DAY_OF_MONTH, dayPicker.value)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                selectedAddedDateMillis = selectedCalendar.timeInMillis
                updateDateText()

                dialog.dismiss()
            }
        }

        container.addView(handle)
        container.addView(title)
        container.addView(pickerRow)
        container.addView(saveButton)

        dialog.setContentView(container)

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )

            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
        }

        dialog.show()
    }

    private fun createDateNumberPicker(): NumberPicker {
        return NumberPicker(requireContext()).apply {
            wrapSelectorWheel = false

            layoutParams = LinearLayout.LayoutParams(
                0,
                128.dp(),
                1f
            )
        }
    }

    private fun closeForm() {
        if (isNavigatingBack) {
            return
        }

        isNavigatingBack = true

        val navController = findNavController()

        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set(
                TankDetailFragment.KEY_RETURN_TAB,
                TankDetailTabArgs.TANK_LIFE
            )

        navController.navigateUp()
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

    private fun getCategoryIcon(
        category: String
    ): Int {
        return when (category) {
            LivestockCategories.FISH -> R.drawable.ic_life_fish_24
            LivestockCategories.SHRIMP -> R.drawable.ic_life_shrimp_24
            LivestockCategories.SNAIL -> R.drawable.ic_life_snail_24
            LivestockCategories.CRAB_CRAYFISH -> R.drawable.ic_life_crab_24
            LivestockCategories.CORAL -> R.drawable.ic_life_coral_24
            else -> R.drawable.ic_life_other_24
        }
    }

    private fun getCategoryColor(
        category: String
    ): String {
        return when (category) {
            LivestockCategories.FISH -> "#1C5D8F"
            LivestockCategories.SHRIMP -> "#8F4A3A"
            LivestockCategories.SNAIL -> "#3E6B4A"
            LivestockCategories.CRAB_CRAYFISH -> "#7A4D2D"
            LivestockCategories.CORAL -> "#7A4E8F"
            else -> "#3E536B"
        }
    }

    private fun createIconBackground(
        color: String
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(color))
            cornerRadius = 18.dp().toFloat()
        }
    }

    private fun createRoundedDrawable(
        color: String,
        radiusPx: Int,
        strokeColor: String? = null,
        strokeWidthPx: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(color))
            cornerRadius = radiusPx.toFloat()

            if (strokeColor != null && strokeWidthPx > 0) {
                setStroke(
                    strokeWidthPx,
                    Color.parseColor(strokeColor)
                )
            }
        }
    }

    private fun createTopRoundedDrawable(
        color: String,
        radiusPx: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(color))

            cornerRadii = floatArrayOf(
                radiusPx.toFloat(),
                radiusPx.toFloat(),
                radiusPx.toFloat(),
                radiusPx.toFloat(),
                0f,
                0f,
                0f,
                0f
            )
        }
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"
        private const val ARG_LIVESTOCK_ID = "livestockId"
    }
}