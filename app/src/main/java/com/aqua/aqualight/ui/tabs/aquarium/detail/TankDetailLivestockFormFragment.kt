package com.aqua.aqualight.ui.tabs.aquarium.detail

import com.aqua.aqualight.ui.common.text.setTextSizeResource
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentTankLivestockFormBinding
import com.aqua.aqualight.i18n.DateOnly
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.dialog.AppDatePickerDialogFragment
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumIdGenerator
import kotlinx.coroutines.launch
import java.util.Calendar
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
    private var selectedAddedDateEpochDay: Long = DateOnly.todayEpochDay()
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
        setupResultListeners()
        setupSystemBackButton()
        setupNamePreviewListener()
        renderCategoryOptions()
        updatePreview()
        updateQuantity()
        updateDateText()
        observeEditingLivestockIfNeeded()
    }


    private fun setupResultListeners() {
        childFragmentManager.setFragmentResultListener(
            LIVESTOCK_DATE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(AppDatePickerDialogFragment.RESULT_KEY) !=
                AppDatePickerDialogFragment.RESULT_SELECTED
            ) return@setFragmentResultListener
            selectedAddedDateEpochDay = DateOnly.fromPickerMillis(
                result.getLong(AppDatePickerDialogFragment.RESULT_MILLIS)
            )
            updateDateText()
        }
        childFragmentManager.setFragmentResultListener(
            LIVESTOCK_DELETE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(FeedbackBottomSheet.RESULT_KEY) ==
                FeedbackBottomSheet.RESULT_PRIMARY
            ) deleteLivestock()
        }
        childFragmentManager.setFragmentResultListener(
            LIVESTOCK_MISSING_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, _ -> closeForm() }
    }

    private fun readArguments() {
        tankId = args.tankId
        editingLivestockId = args.livestockId

        selectedCategory = LivestockCategories.FISH
        selectedQuantity = 1
        selectedAddedDateEpochDay = DateOnly.todayEpochDay()
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
        livestock: AquariumLivestock
    ) {
        selectedCategory = livestock.category.ifBlank {
            LivestockCategories.FISH
        }

        selectedQuantity = livestock.quantity.coerceAtLeast(1)

        selectedAddedDateEpochDay = livestock.addedDateEpochDay
            ?.takeIf { epochDay -> epochDay > 0L }
            ?: DateOnly.todayEpochDay()

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

        FeedbackBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = title,
            message = message,
            primaryText = getString(R.string.ok),
            cancelText = null,
            tone = FeedbackBottomSheet.FeedbackTone.ERROR,
            requestKey = LIVESTOCK_MISSING_REQUEST_KEY,
            actionId = ""
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
            setTextSizeResource(R.dimen.aqua_text_size_body_compact)
            isSelected = selected
            background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bg_aqua_selection_row_compact
            )
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

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = resources.getDimensionPixelOffset(R.dimen.aqua_size_46)
                columnSpec = GridLayout.spec(
                    GridLayout.UNDEFINED,
                    1f
                )
                setMargins(
                    0,
                    0,
                    resources.getDimensionPixelOffset(R.dimen.aqua_size_8),
                    resources.getDimensionPixelOffset(R.dimen.aqua_size_8)
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

        binding.ivLifeIconPreview.setColorFilter(ContextCompat.getColor(requireContext(), R.color.aqua_content_on_dark))

        binding.ivLifeIconPreview.background = createIconBackground(
            color = getCategoryColor(selectedCategory)
        )

        binding.tvLifeCategoryPreview.text = selectedCategory
    }

    private fun updateQuantity() {
        binding.tvQuantityValue.text = LocaleFormatter.formatInteger(
            requireContext(),
            selectedQuantity
        )
    }

    private fun updateDateText() {
        binding.tvAddedDateValue.text = LocaleFormatter.formatDateEpochDay(
            requireContext(),
            selectedAddedDateEpochDay
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

        val livestock = AquariumLivestock(
            id = if (editingLivestockId > 0L) {
                editingLivestockId
            } else {
                AquariumIdGenerator.newLong()
            },
            name = name,
            category = selectedCategory,
            quantity = selectedQuantity.coerceAtLeast(1),
            addedDateEpochDay = selectedAddedDateEpochDay,
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
        FeedbackBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.aquarium_delete_livestock_title),
            message = getString(R.string.aquarium_delete_livestock_message),
            primaryText = getString(R.string.delete),
            cancelText = getString(R.string.cancel),
            tone = FeedbackBottomSheet.FeedbackTone.WARNING,
            requestKey = LIVESTOCK_DELETE_REQUEST_KEY,
            actionId = editingLivestockId.toString()
        )
    }

    private fun deleteLivestock() {
        if (editingLivestockId <= 0L || isDeletingLivestock) return
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
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val minMillis = Calendar.getInstance().apply {
            set(currentYear - 20, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val maxMillis = Calendar.getInstance().apply {
            set(currentYear + 5, Calendar.DECEMBER, 31, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        AppDatePickerDialogFragment.show(
            fragmentManager = childFragmentManager,
            requestKey = LIVESTOCK_DATE_REQUEST_KEY,
            initialMillis = DateOnly.toPickerMillis(selectedAddedDateEpochDay),
            minMillis = minMillis,
            maxMillis = maxMillis
        )
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
    ): Int {
        return when (category) {
            LivestockCategories.FISH -> R.color.aqua_tank_detail_livestock_form_fragment_color
            LivestockCategories.SHRIMP -> R.color.aqua_tank_detail_livestock_form_fragment_color_variant_2
            LivestockCategories.SNAIL -> R.color.aqua_tank_detail_livestock_form_fragment_color_variant_3
            LivestockCategories.CRAB_CRAYFISH -> R.color.aqua_tank_detail_livestock_form_fragment_color_variant_4
            LivestockCategories.CORAL -> R.color.aqua_tank_detail_livestock_form_fragment_color_variant_5
            else -> R.color.aqua_tank_detail_livestock_form_fragment_color_variant_6
        }
    }

    private fun createIconBackground(
        @ColorRes color: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(ContextCompat.getColor(requireContext(), color))
            cornerRadius = resources.getDimensionPixelOffset(R.dimen.aqua_size_18).toFloat()
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val LIVESTOCK_DATE_REQUEST_KEY = "livestock_added_date_result"
        private const val LIVESTOCK_DELETE_REQUEST_KEY = "livestock_delete_result"
        private const val LIVESTOCK_MISSING_REQUEST_KEY = "livestock_missing_result"
        private const val ARG_TANK_ID = "tankId"
        private const val ARG_LIVESTOCK_ID = "livestockId"
    }
}
