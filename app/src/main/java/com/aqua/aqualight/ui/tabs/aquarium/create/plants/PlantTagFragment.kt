package com.aqua.aqualight.ui.tabs.aquarium.create.plants

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import coil3.load
import coil3.request.crossfade
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.model.TankPlantTag
import com.aqua.aqualight.databinding.FragmentPlantTagBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderCardIconAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel
import com.google.android.material.card.MaterialCardView

class PlantTagFragment : Fragment(R.layout.fragment_plant_tag) {

    private var _binding: FragmentPlantTagBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by navGraphViewModels(R.id.nav_create_tank)

    private val selectedPlants = mutableListOf<TankPlantTag>()

    private var pendingMarkerX: Float = 0.5f
    private var pendingMarkerY: Float = 0.5f

    private var hasInitializedSelectedPlants: Boolean = false
    private var isOpeningPlantPicker: Boolean = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentPlantTagBinding.bind(view)

        initializeSelectedPlantsIfNeeded()

        setupHeader()
        setupImage()
        setupResultListener()
        setupClickListeners()
        renderPlants()
        renderMarkers()
    }

    override fun onResume() {
        super.onResume()
        isOpeningPlantPicker = false
    }

    private fun initializeSelectedPlantsIfNeeded() {
        if (hasInitializedSelectedPlants) {
            return
        }

        selectedPlants.clear()
        selectedPlants.addAll(viewModel.tankDraft.plants)
        hasInitializedSelectedPlants = true
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = "Tag your plants",
                onBackClick = {
                    closePlantTagFlow()
                },
                cardIconAction = AquaHeaderCardIconAction(
                    iconRes = R.drawable.ic_check_24,
                    contentDescription = "Confirm",
                    backgroundColor = Color.parseColor("#1F6F4A"),
                    strokeColor = Color.parseColor("#2A8A5E"),
                    iconTintColor = Color.WHITE,
                    onClick = {
                        confirmPlantTags()
                    }
                )
            )
        )
    }

    private fun setupImage() {
        val photoUri = viewModel.tankDraft.photoUri

        if (!photoUri.isNullOrBlank()) {
            binding.imgAquariumPhoto.load(photoUri) {
                crossfade(true)
            }
        } else {
            binding.imgAquariumPhoto.setImageResource(R.drawable.nature_aquarium)
        }
    }

    private fun setupResultListener() {
        val savedStateHandle = findNavController()
            .currentBackStackEntry
            ?.savedStateHandle
            ?: return

        savedStateHandle.getLiveData<Bundle?>(
            PlantPickerFragment.RESULT_BUNDLE_KEY
        ).observe(viewLifecycleOwner) { bundle ->
            if (bundle == null) {
                return@observe
            }

            savedStateHandle.set<Bundle?>(
                PlantPickerFragment.RESULT_BUNDLE_KEY,
                null
            )

            val plantName = bundle.getString(
                PlantPickerFragment.RESULT_PLANT_NAME
            ) ?: return@observe

            val category = bundle.getString(
                PlantPickerFragment.RESULT_PLANT_CATEGORY
            ) ?: return@observe

            selectedPlants.add(
                TankPlantTag(
                    plantName = plantName,
                    category = category,
                    markerX = pendingMarkerX,
                    markerY = pendingMarkerY
                )
            )

            renderPlants()
            renderMarkers()
        }
    }

    private fun setupClickListeners() {
        binding.imageTouchArea.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                pendingMarkerX = event.x / view.width.toFloat()
                pendingMarkerY = event.y / view.height.toFloat()

                if (!isOpeningPlantPicker) {
                    val didNavigate = findNavController().navigateSafelyFrom(
                        sourceDestinationId = R.id.createPlantTagFragment,
                        directions = PlantTagFragmentDirections
                            .actionCreatePlantTagFragmentToCreatePlantPickerFragment(
                                useNavResult = true
                            )
                    )

                    isOpeningPlantPicker = didNavigate
                }

                true
            } else {
                true
            }
        }
    }

    private fun confirmPlantTags() {
        viewModel.updateTankPlants(selectedPlants)

        findNavController()
            .previousBackStackEntry
            ?.savedStateHandle
            ?.set(
                RESULT_KEY,
                true
            )

        closePlantTagFlow()
    }

    private fun closePlantTagFlow() {
        findNavController().navigateUp()
    }

    private fun renderPlants() {
        binding.plantListContainer.removeAllViews()

        selectedPlants.forEachIndexed { index, plant ->

            val card = MaterialCardView(requireContext()).apply {
                radius = 16.dp().toFloat()
                strokeWidth = 1.dp()
                strokeColor = Color.parseColor("#223A57")
                setCardBackgroundColor(Color.parseColor("#10233A"))
                useCompatPadding = false

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = 10.dp()
                layoutParams = params
            }

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    14.dp(),
                    11.dp(),
                    12.dp(),
                    11.dp()
                )
            }

            val number = TextView(requireContext()).apply {
                text = "${index + 1}"
                gravity = Gravity.CENTER
                textSize = 13f
                includeFontPadding = false
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_plant_number_circle)

                layoutParams = LinearLayout.LayoutParams(
                    34.dp(),
                    34.dp()
                )
            }

            val textBox = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL

                val params = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                params.marginStart = 14.dp()
                layoutParams = params
            }

            val categoryText = TextView(requireContext()).apply {
                text = plant.category
                textSize = 12f
                includeFontPadding = false
                setTextColor(Color.parseColor("#8FA4BE"))
            }

            val nameText = TextView(requireContext()).apply {
                text = plant.plantName
                textSize = 14f
                includeFontPadding = false
                maxLines = 2
                setTextColor(Color.WHITE)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = 5.dp()
                layoutParams = params
            }

            val delete = TextView(requireContext()).apply {
                text = "×"
                textSize = 23f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(Color.parseColor("#8FA4BE"))

                setOnClickListener {
                    selectedPlants.removeAt(index)
                    renderPlants()
                    renderMarkers()
                }

                layoutParams = LinearLayout.LayoutParams(
                    34.dp(),
                    34.dp()
                )
            }

            textBox.addView(categoryText)
            textBox.addView(nameText)

            row.addView(number)
            row.addView(textBox)
            row.addView(delete)

            card.addView(row)
            binding.plantListContainer.addView(card)
        }
    }

    private fun renderMarkers() {
        binding.markerContainer.post {
            binding.markerContainer.removeAllViews()

            val width = binding.markerContainer.width
            val height = binding.markerContainer.height

            selectedPlants.forEachIndexed { index, plant ->

                val marker = TextView(requireContext()).apply {
                    text = "${index + 1}"
                    gravity = Gravity.CENTER
                    textSize = 12f
                    includeFontPadding = false
                    setTextColor(Color.WHITE)
                    setTypeface(null, Typeface.BOLD)
                    setBackgroundResource(R.drawable.bg_plant_marker_circle)
                }

                val size = 28.dp()

                val params = FrameLayout.LayoutParams(
                    size,
                    size
                )

                marker.x = (plant.markerX * width) - size / 2f
                marker.y = (plant.markerY * height) - size / 2f

                binding.markerContainer.addView(marker, params)
            }
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
        const val RESULT_KEY = "plant_tag_result"
        const val RESULT_UPDATED = "plant_tag_updated"
    }
}