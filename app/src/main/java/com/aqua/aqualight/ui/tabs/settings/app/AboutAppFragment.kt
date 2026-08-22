package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentAboutAppBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import java.util.Calendar

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
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.screen_title_about)
            )
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

        val versionName = packageInfo.versionName
            ?: getString(R.string.common_not_available_symbol)

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

        binding.tvFooter.text =
            getString(
                R.string.about_app_footer_format,
                Calendar.getInstance().get(Calendar.YEAR)
            )
    }

    override fun onDestroyView() {
        _binding =
            null

        super.onDestroyView()
    }
}
