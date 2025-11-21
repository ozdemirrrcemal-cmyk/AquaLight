package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentAboutAppBinding

class AboutAppFragment : Fragment(R.layout.fragment_about_app) {

    private var _binding: FragmentAboutAppBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAboutAppBinding.bind(view)

        // 🔙 Geri
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        bindStaticInfo()
        setupLegalClicks()
    }

    private fun bindStaticInfo() {
        val context = requireContext()
        val pm = context.packageManager
        val packageName = context.packageName

        val pkgInfo = pm.getPackageInfo(packageName, 0)
        val versionName = pkgInfo.versionName ?: "-"
        val versionCode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toString()
            }

        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val androidVersion = "Android ${Build.VERSION.RELEASE}"

        // Üst kart
        binding.tvAppVersion.text =
            getString(R.string.about_app_header_version_format, versionName, versionCode)

        // Sistem kartı
        binding.tvVersionValue.text = versionName
        binding.tvBuildValue.text = versionCode
        binding.tvDeviceValue.text = deviceModel
        binding.tvAndroidValue.text = androidVersion
    }

    private fun setupLegalClicks() {
        // Burada sadece boş click listener bırakıyorum ki sonradan nav / web açma ekleyebilesin
        binding.rowPrivacy.setOnClickListener {
            // TODO: Privacy Policy ekranına veya web sayfasına git
        }

        binding.rowTerms.setOnClickListener {
            // TODO: Terms of Use ekranına veya web sayfasına git
        }

        binding.rowLicenses.setOnClickListener {
            // TODO: Açık kaynak lisansları ekranına git
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}