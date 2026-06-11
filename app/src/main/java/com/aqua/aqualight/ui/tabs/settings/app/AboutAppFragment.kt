package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentAboutAppBinding
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class AboutAppFragment : Fragment(R.layout.fragment_about_app) {

    private var _binding: FragmentAboutAppBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentAboutAppBinding.bind(view)

        setupHeader()
        bindStaticInfo()
        setupLegalClicks()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this
        )
    }

    private fun bindStaticInfo() {
        val context =
            requireContext()

        val packageManager =
            context.packageManager

        val packageName =
            context.packageName

        val packageInfo =
            packageManager.getPackageInfo(
                packageName,
                0
            )

        val versionName =
            packageInfo.versionName ?: "-"

        val versionCode =
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            }

        val deviceModel =
            "${Build.MANUFACTURER} ${Build.MODEL}".trim()

        val androidVersion =
            "Android ${Build.VERSION.RELEASE}"

        binding.tvAppVersion.text =
            getString(
                R.string.about_app_header_version_format,
                versionName,
                versionCode
            )

        binding.tvVersionValue.text =
            versionName

        binding.tvBuildValue.text =
            versionCode

        binding.tvDeviceValue.text =
            deviceModel

        binding.tvAndroidValue.text =
            androidVersion
    }

    private fun setupLegalClicks() =
        with(binding) {

            rowPrivacy.setOnClickListener {
                findNavController().navigate(
                    AboutAppFragmentDirections.actionAboutAppFragmentToPrivacyFragment()
                )
            }

            rowTerms.setOnClickListener {
                findNavController().navigate(
                    AboutAppFragmentDirections.actionAboutAppFragmentToTermsOfUseFragment()
                )
            }

            rowLicenses.setOnClickListener {
                findNavController().navigate(
                    AboutAppFragmentDirections.actionAboutAppFragmentToOpenSourceLicensesFragment()
                )
            }
        }

    override fun onDestroyView() {
        _binding =
            null

        super.onDestroyView()
    }
}