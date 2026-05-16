package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankPhotoBinding
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel

class TankPhotoFragment : Fragment(R.layout.fragment_tank_photo), TankStepFragment {

    private var _binding: FragmentTankPhotoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private var selectedPhotoUri: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentTankPhotoBinding.bind(view)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnCamera.setOnClickListener {
            // Sonra burada fotoğraf seçme fragmentına geçeceğiz.
            // Örnek:
            // findNavController().navigate(R.id.action_tankPhoto_to_photoPickerFragment)
        }

        binding.btnAddPlant.setOnClickListener {
            // Sonra burada plant ekleme/tag fragmentına geçeceğiz.
            // Şimdilik boş.
        }
    }

    override fun validateAndSave(): Boolean {
        /*
            Şimdilik kullanıcı fotoğraf seçmese bile devam edebilsin.
            Varsayılan görsel drawable/natureaquarium olarak ekranda gösteriliyor.

            Kalıcı kayıt aşamasında:
            selectedPhotoUri null ise default görsel kullanılacak.
        */

        viewModel.updateTankPhoto(selectedPhotoUri)

        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}