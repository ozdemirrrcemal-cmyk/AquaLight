package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankPhotoBinding
import com.aqua.aqualight.ui.common.bottomsheet.PhotoSourceBottomSheet
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

        setupPhotoSourceResultListener()
        setupClickListeners()
    }

    private fun setupPhotoSourceResultListener() {
        childFragmentManager.setFragmentResultListener(
            PhotoSourceBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->

            when (bundle.getString(PhotoSourceBottomSheet.RESULT_KEY)) {

                PhotoSourceBottomSheet.RESULT_CAMERA -> {
                    openCamera()
                }

                PhotoSourceBottomSheet.RESULT_GALLERY -> {
                    openGallery()
                }
            }
        }
    }

    private fun setupClickListeners() {
    binding.btnCamera.setOnClickListener {
        PhotoSourceBottomSheet
            .newInstance(
                title = "Aquarium photo"
            )
            .show(
                childFragmentManager,
                PhotoSourceBottomSheet.TAG
            )
    }

    binding.btnAddPlant.setOnClickListener {
        // Sonra Add Plant ekranına bağlanacak.
    }
}

    private fun openCamera() {
        /*
            Sonraki adımda burada:
            1. Kamera için geçici image Uri oluşturacağız
            2. ActivityResultContracts.TakePicture ile fotoğraf çekeceğiz
            3. Fotoğraf başarılıysa crop ekranını açacağız

            openCropScreen(cameraImageUri)
        */
    }

    private fun openGallery() {
        /*
            Sonraki adımda burada:
            1. Galeriden fotoğraf seçtireceğiz
            2. Gelen Uri ile crop ekranını açacağız

            openCropScreen(selectedGalleryUri)
        */
    }

    private fun openCropScreen(sourceUri: Uri) {
        /*
            Sonra crop fragment burada açılacak.

            Akış:
            sourceUri -> CropPhotoFragment
            CropPhotoFragment -> croppedUri result
            TankPhotoFragment -> imgAquariumPhoto güncelle
        */
    }

    override fun validateAndSave(): Boolean {
        viewModel.updateTankPhoto(selectedPhotoUri)
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}