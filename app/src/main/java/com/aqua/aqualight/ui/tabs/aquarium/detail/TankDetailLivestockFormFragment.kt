package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumIdGenerator
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumLivestockIdentity
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentTankLivestockFormBinding
import com.aqua.aqualight.i18n.DateOnly
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.dialog.AppDatePickerDialogFragment
import com.aqua.aqualight.ui.common.feedback.FeedbackBottomSheet
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.text.setTextSizeResource
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.data.aquarium.catalog.livestock.LivestockCatalog
import com.aqua.aqualight.data.aquarium.catalog.livestock.LivestockCatalogEntry
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.aqua.aqualight.ui.tabs.aquarium.navigation.TankDetailTabArgs
import java.util.Calendar
import kotlinx.coroutines.launch

class TankDetailLivestockFormFragment :
    Fragment(R.layout.fragment_tank_livestock_form) {

    private val args: TankDetailLivestockFormFragmentArgs by navArgs()

    private var _binding: FragmentTankLivestockFormBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var editingLivestockId: Long = 0L
    private var selectedCatalogEntryId: String = ""
    private var selectedCatalogEntry: LivestockCatalogEntry? = null
    private var openedFromPicker: Boolean = false
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
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentTankLivestockFormBinding.bind(view)

        readArguments()
        setupInitialUi()
        setupClickListeners()
        setupResultListeners()
        setupSystemBackButton()
        setupNamePreviewListener()
        renderCategoryOptions()

        if (editingLivestockId > 0L) {
            observeEditingLivestockIfNeeded()
        } else {
            initializeAddSelection()
        }

        updatePreview()
        updateQuantity()
        updateDateText()
        updateIdentityFieldVisibility()
    }

    private fun setupResultListeners() {
        childFragmentManager.setFragmentResultListener(
            LIVESTOCK_DATE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(AppDatePickerDialogFragment.RESULT_KEY) !=
                AppDatePickerDialogFragment.RESULT_SELECTED
            ) {
                return@setFragmentResultListener
            }

            selectedAddedDateEpochDay = DateOnly.fromPickerMillis(
                result.getLong(AppDatePickerDialogFragment.RESULT_MILLIS)
            )
            updateDateText()
        }

        childFragmentManager.setFragmentResultListener(
            LIVESTOCK_DELETE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(FeedbackBottomSheet.RESULT_KEY) ==
                FeedbackBottomSheet.RESULT_PRIMARY
            ) {
                deleteLivestock()
            }
        }

        childFragmentManager.setFragmentResultListener(
            LIVESTOCK_MISSING_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            closeForm()
        }
    }

    private fun readArguments() {
        tankId = args.tankId
        editingLivestockId = args.livestockId
        selectedCatalogEntryId = args.catalogEntryId.trim()
        openedFromPicker = args.openedFromPicker

        selectedCategory = args.presetCategory
            .takeIf { category -> category in LivestockCategories.all }
            ?: LivestockCategories.FISH
        selectedQuantity = 1
        selectedAddedDateEpochDay = DateOnly.todayEpochDay()
    }

    private fun initializeAddSelection() {
        selectedCatalogEntry = LivestockCatalog.findById(
            context = requireContext(),
            entryId = selectedCatalogEntryId
        )

        if (
            selectedCatalogEntryId.isNotBlank() &&
            selectedCatalogEntry == null
        ) {
            showMissingDataDialogAndClose(
                title = getString(R.string.aquarium_livestock_not_found_title),
                message = getString(R.string.livestock_catalog_entry_missing_message)
            )
            return
        }

        selectedCatalogEntry?.let { entry ->
            selectedCategory = entry.category
            binding.etLifeName.setText(entry.displayName(requireContext()))
        } ?: run {
            binding.etLifeName.setText(args.presetName.trim())
        }

        renderCategoryOptions()
        updateIdentityFieldVisibility()
        updatePreview()
    }

    private fun observeEditingLivestockIfNeeded() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            if (isNavigatingBack || isDeletingLivestock) {
                return@observe
            }

            val tank = tanks.firstOrNull { tank ->
                tank.id == tankId
            }

            if (tank == null) {
                showMissingDataDialogAndClose(
                    title = getString(R.string.aquarium_tank_not_found_title),
                    message = getString(R.string.aquarium_tank_no_longer_exists_message)
                )
                return@observe
            }

            val livestock = tank.livestock.firstOrNull { item ->
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
        selectedCatalogEntryId = livestock.catalogEntryId.trim()
        val isCustomIdentity = AquariumLivestockIdentity.isCustom(
            selectedCatalogEntryId
        )
        selectedCatalogEntry = if (isCustomIdentity) {
            null
        } else {
            LivestockCatalog.findById(
                context = requireContext(),
                entryId = selectedCatalogEntryId
            )
        }

        if (!isCustomIdentity && selectedCatalogEntry == null) {
            showMissingDataDialogAndClose(
                title = getString(R.string.livestock_catalog_entry_missing_title),
                message = getString(R.string.livestock_catalog_entry_missing_message)
            )
            return
        }

        selectedCatalogEntry?.let { entry ->
            selectedCategory = entry.category
        } ?: run {
            selectedCategory = livestock.category.takeIf { category ->
                category in LivestockCategories.all
            } ?: LivestockCategories.FISH
        }

        selectedQuantity = livestock.quantity.coerceAtLeast(1)
        selectedAddedDateEpochDay = livestock.addedDateEpochDay
            ?.takeIf { epochDay -> epochDay > 0L }
            ?: DateOnly.todayEpochDay()

        binding.etLifeName.setText(
            selectedCatalogEntry?.displayName(requireContext()) ?: livestock.name
        )
        binding.etLifeNote.setText(livestock.note)

        renderCategoryOptions()
        updateIdentityFieldVisibility()
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

    private fun updateIdentityFieldVisibility() {
        val hasCatalogIdentity = selectedCatalogEntry != null
        val categoryLockedByPicker = openedFromPicker && editingLivestockId <= 0L

        binding.tvLifeNameLabel.isVisible = !hasCatalogIdentity
        binding.etLifeName.isVisible = !hasCatalogIdentity
        binding.tvLifeCategoryLabel.isVisible = !hasCatalogIdentity && !categoryLockedByPicker
        binding.categoryGrid.isVisible = !hasCatalogIdentity && !categoryLockedByPicker
    }

    private fun renderCategoryOptions() {
        binding.categoryGrid.removeAllViews()

        LivestockCategories.all.forEach { category ->
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
            text = getString(LivestockCategories.labelRes(category))
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

            layoutParams = GridLayout.LayoutParams().apply {
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

            setOnClickListener {
                selectedCategory = category
                renderCategoryOptions()
                updatePreview()
            }
        }
    }

    private fun updatePreview() {
        val displayName = selectedCatalogEntry
            ?.displayName(requireContext())
            ?: binding.etLifeName.text.toString().trim()

        binding.tvLifePreviewTitle.text = displayName.ifBlank {
            getString(R.string.aquarium_livestock_default_title)
        }

        val scientificName = selectedCatalogEntry?.scientificName.orEmpty()
        binding.tvLifeScientificPreview.isVisible = scientificName.isNotBlank()
        binding.tvLifeScientificPreview.text = scientificName

        val parameterSummary = selectedCatalogEntry?.parameterSummary().orEmpty()
        binding.tvLifeParametersPreview.isVisible = parameterSummary.isNotBlank()
        binding.tvLifeParametersPreview.text = parameterSummary

        binding.ivLifeIconPreview.setImageResource(
            LivestockCategories.iconRes(selectedCategory)
        )
        binding.ivLifeIconPreview.setColorFilter(
            ContextCompat.getColor(
                requireContext(),
                R.color.aqua_content_on_dark
            )
        )
        binding.ivLifeIconPreview.background = createIconBackground(
            color = LivestockCategories.colorRes(selectedCategory)
        )

        binding.tvLifeCategoryPreview.text = getString(
            LivestockCategories.labelRes(selectedCategory)
        )
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

        val name = selectedCatalogEntry
            ?.displayName(requireContext())
            ?: binding.etLifeName.text.toString().trim()

        if (name.length < 2) {
            showSnackBar(
                message = getString(R.string.aquarium_validation_livestock_name_min),
                type = BaseActivity.SnackType.WARNING
            )
            return
        }

        val livestockId = if (editingLivestockId > 0L) {
            editingLivestockId
        } else {
            AquariumIdGenerator.newLong()
        }
        val catalogIdentity = selectedCatalogEntry
            ?.id
            ?: selectedCatalogEntryId
                .takeIf(AquariumLivestockIdentity::isCustom)
            ?: AquariumLivestockIdentity.custom(livestockId)

        val livestock = AquariumLivestock(
            id = livestockId,
            catalogEntryId = catalogIdentity,
            name = name,
            category = selectedCategory,
            quantity = selectedQuantity.coerceAtLeast(1),
            addedDateEpochDay = selectedAddedDateEpochDay,
            note = binding.etLifeNote.text.toString().trim()
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

                finishAfterMutation()
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
                finishAfterMutation()
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

    private fun finishAfterMutation() {
        if (isNavigatingBack) {
            return
        }

        val navController = findNavController()

        if (openedFromPicker && editingLivestockId <= 0L) {
            isNavigatingBack = true

            runCatching {
                navController.getBackStackEntry(R.id.tankDetailFragment)
                    .savedStateHandle
                    .set(
                        TankDetailFragment.KEY_RETURN_TAB,
                        TankDetailTabArgs.TANK_LIFE
                    )
            }

            if (navController.popBackStack(R.id.tankDetailFragment, false)) {
                return
            }

            isNavigatingBack = false
        }

        closeForm()
    }

    private fun closeForm() {
        if (isNavigatingBack) {
            return
        }

        isNavigatingBack = true

        val navController = findNavController()

        if (!openedFromPicker) {
            navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set(
                    TankDetailFragment.KEY_RETURN_TAB,
                    TankDetailTabArgs.TANK_LIFE
                )
        }

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

    private fun createIconBackground(
        @ColorRes color: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(ContextCompat.getColor(requireContext(), color))
            cornerRadius = resources.getDimensionPixelOffset(
                R.dimen.aqua_size_18
            ).toFloat()
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
    }
}
