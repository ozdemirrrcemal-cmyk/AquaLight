package com.aqua.aqualight.ui.common.bottomsheet

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.aqua.aqualight.databinding.BottomSheetPhotoSourceBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.core.os.bundleOf
import com.aqua.aqualight.R

class PhotoSourceBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPhotoSourceBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAG = "PhotoSourceBottomSheet"
        const val REQUEST_KEY = "photo_source_request"
        const val RESULT_KEY = "photo_source_result"
        const val RESULT_CAMERA = "camera"
        const val RESULT_GALLERY = "gallery"

        fun newInstance() = PhotoSourceBottomSheet()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Material BottomSheetDialog
        return BottomSheetDialog(requireContext(), theme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPhotoSourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 📷 Kamera
        binding.btnCamera.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(RESULT_KEY to RESULT_CAMERA)
            )
            dismiss()
        }

        // 🖼️ Galeri
        binding.btnGallery.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(RESULT_KEY to RESULT_GALLERY)
            )
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}