package com.aqua.aqualight.ui.tabs.settings.legal

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentLegalCenterBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class LegalCenterFragment : Fragment(R.layout.fragment_legal_center) {

    private var _binding: FragmentLegalCenterBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLegalCenterBinding.bind(view)

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.screen_title_legal_center)
            )
        )

        binding.rowPrivacyNotice.setOnClickListener {
            safeNavigate(
                LegalCenterFragmentDirections.actionLegalCenterFragmentToPrivacyFragment()
            )
        }

        binding.rowTermsOfUse.setOnClickListener {
            safeNavigate(
                LegalCenterFragmentDirections.actionLegalCenterFragmentToTermsOfUseFragment()
            )
        }

        binding.rowThirdPartyLicenses.setOnClickListener {
            safeNavigate(
                LegalCenterFragmentDirections.actionLegalCenterFragmentToThirdPartyLicensesFragment()
            )
        }
    }

    private fun safeNavigate(directions: NavDirections) {
        val navController = runCatching {
            findNavController()
        }.getOrNull() ?: return

        if (navController.currentDestination?.id != R.id.legalCenterFragment) {
            return
        }

        runCatching {
            navController.navigate(directions)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
