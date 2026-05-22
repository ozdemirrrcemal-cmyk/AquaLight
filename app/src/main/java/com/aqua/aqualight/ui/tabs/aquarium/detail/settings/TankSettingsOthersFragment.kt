package com.aqua.aqualight.ui.tabs.aquarium.detail.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankSettingsOthersBinding

class TankSettingsOthersFragment : Fragment(R.layout.fragment_tank_settings_others) {

    private var _binding: FragmentTankSettingsOthersBinding? = null
    private val binding get() = _binding!!

    private var tankId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tankId = requireArguments().getLong(ARG_TANK_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentTankSettingsOthersBinding.bind(view)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.rowDuplicateTank.setOnClickListener {
            (parentFragment as? TankSettingsFragment)
                ?.showDuplicateTankConfirmationDialog()
        }

        binding.rowExportTankData.setOnClickListener {
            (parentFragment as? TankSettingsFragment)
                ?.exportTankDataAsPdf()
        }

        binding.rowDeleteTank.setOnClickListener {
            (parentFragment as? TankSettingsFragment)
                ?.showDeleteTankConfirmationDialog()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"

        fun newInstance(
            tankId: Long
        ): TankSettingsOthersFragment {
            return TankSettingsOthersFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TANK_ID, tankId)
                }
            }
        }
    }
}