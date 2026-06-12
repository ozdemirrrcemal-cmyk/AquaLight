package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.base.BaseActivity
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentPlantTagBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderCardIconAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.PlantPickerFragment
import com.aqua.aqualight.data.aquarium.model.TankPlantTag
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

class TankDetailPlantTagFragment : Fragment(R.layout.fragment_plant_tag) {

    private val args: TankDetailPlantTagFragmentArgs by navArgs()

    private var _binding: FragmentPlantTagBinding? = null
    private val binding get() = _binding!!

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var photoUri: String? = null

    private val selectedPlants = mutableListOf<TankPlantTag>()

    private var pendingMarkerX: Float = 0.5f
    private var pendingMarkerY: Float = 0.5f

    private var hasLoadedTankData: Boolean = false
    private var isSaving: Boolean = false
    private var isOpeningPlantPicker: Boolean = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        tankId = args.tankId
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentPlantTagBinding.bind(view)

        setupHeader()
        setupTankData()
        setupResultListener()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        isOpeningPlantPicker = false
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = "Tag your plants",
                onBackClick = {
                    findNavController().navigateUp()
                },
                cardIconAction = AquaHeaderCardIconAction(
                    iconRes = R.drawable.ic_check_24,
                    contentDescription = "Confirm",
                    backgroundColor = Color.parseColor("#1F6F4A"),
                    strokeColor = Color.parseColor("#2A8A5E"),
                    iconTintColor = Color.WHITE,
                    enabled = !isSaving,
                    onClick = {
                        savePlantsAndClose()
                    }
                )
            )
        )
    }

    private fun setupTankData() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            if (hasLoadedTankData) {
                return@observe
            }

            val tank = tanks.firstOrNull { tank ->
                tank.id == tankId
            }

            if (tank == null) {
                findNavController().navigateUp()
                return@observe
            }

            photoUri = tank.photoUri

            selectedPlants.clear()
            selectedPlants.addAll(
                tank.plants.map { plant ->
                    TankPlantTag(
                        id = plant.id,
                        plantName = plant.plantName,
                        category = plant.category,
                        markerX = plant.markerX,
                        markerY = plant.markerY
                    )
                }
            )

            hasLoadedTankData = true

            setupImage()
            renderPlants()
            renderMarkers()
        }
    }

    private fun setupImage() {
        val imageUri = photoUri

        if (!imageUri.isNullOrBlank()) {
            binding.imgAquariumPhoto.load(Uri.parse(imageUri)) {
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

        savedStateHandle.getLiveData<Bundle>(
            PlantPickerFragment.RESULT_BUNDLE_KEY
        ).observe(viewLifecycleOwner) { bundle ->
            savedStateHandle.remove<Bundle>(
                PlantPickerFragment.RESULT_BUNDLE_KEY
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
        binding.imageTouchArea.setOnTouchListener { touchedView, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                pendingMarkerX = event.x / touchedView.width.toFloat()
                pendingMarkerY = event.y / touchedView.height.toFloat()

                openPlantPickerScreen()

                touchedView.performClick()
                true
            } else {
                true
            }
        }
    }

    private fun openPlantPickerScreen() {
        if (isOpeningPlantPicker) {
            return
        }

        val didNavigate = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.tankDetailPlantTagFragment,
            directions = TankDetailPlantTagFragmentDirections.actionTankDetailPlantTagFragmentToPlantPickerFragment(
                useNavResult = true
            )
        )

        isOpeningPlantPicker = didNavigate
    }

    private fun savePlantsAndClose() {
        if (isSaving) {
            return
        }

        isSaving = true
        setupHeader()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                aquariumTankViewModel.updateTankPlants(
                    tankId = tankId,
                    plants = selectedPlants
                )

                findNavController().navigateUp()
            } catch (exception: Exception) {
                exception.printStackTrace()

                isSaving = false
                setupHeader()

                (activity as? BaseActivity)?.showSnackBar(
                    message = "Plants could not be saved.",
                    type = BaseActivity.SnackType.ERROR
                )
            }
        }
    }

    private fun renderPlants() {
        binding.plantListContainer.removeAllViews()

        selectedPlants.forEachIndexed { index, plant ->
            val card = MaterialCardView(requireContext()).apply {
                radius = 18.dp().toFloat()
                strokeWidth = 1.dp()
                strokeColor = Color.parseColor("#223A57")
                setCardBackgroundColor(Color.parseColor("#10233A"))
                cardElevation = 0f
                useCompatPadding = false

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = 12.dp()
                layoutParams = params
            }

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    14.dp(),
                    12.dp(),
                    12.dp(),
                    12.dp()
                )
            }

            val number = TextView(requireContext()).apply {
                text = "${index + 1}"
                gravity = Gravity.CENTER
                textSize = 13f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.NORMAL)
                setBackgroundResource(R.drawable.bg_plant_number_circle)
                includeFontPadding = false

                layoutParams = LinearLayout.LayoutParams(
                    38.dp(),
                    38.dp()
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
                setTextColor(Color.parseColor("#8FA4BE"))
                includeFontPadding = false
            }

            val nameText = TextView(requireContext()).apply {
                text = plant.plantName
                textSize = 14f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.NORMAL)
                includeFontPadding = false

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = 6.dp()
                layoutParams = params
            }

            val delete = TextView(requireContext()).apply {
                text = "×"
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#8FA4BE"))
                includeFontPadding = false

                setOnClickListener {
                    selectedPlants.removeAt(index)
                    renderPlants()
                    renderMarkers()
                }

                layoutParams = LinearLayout.LayoutParams(
                    36.dp(),
                    36.dp()
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
        binding.markerContainer.removeAllViews()

        binding.markerContainer.post {
            val width = binding.markerContainer.width
            val height = binding.markerContainer.height

            selectedPlants.forEachIndexed { index, plant ->
                val marker = TextView(requireContext()).apply {
                    text = "${index + 1}"
                    gravity = Gravity.CENTER
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    setTypeface(null, Typeface.BOLD)
                    setBackgroundResource(R.drawable.bg_plant_marker)
                    includeFontPadding = false
                }

                val size = 30.dp()

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
        private const val ARG_TANK_ID = "tankId"

        fun newInstance(
            tankId: Long
        ): TankDetailPlantTagFragment {
            return TankDetailPlantTagFragment().apply {
                arguments = bundleOf(
                    ARG_TANK_ID to tankId
                )
            }
        }
    }
}
