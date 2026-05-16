package com.aqua.aqualight.ui.common.bottomsheet

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.aqua.aqualight.databinding.BottomSheetPhotoSourceBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PhotoSourceBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPhotoSourceBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAG = "PhotoSourceBottomSheet"

        const val REQUEST_KEY = "photo_source_request"
        const val RESULT_KEY = "photo_source_result"

        const val RESULT_CAMERA = "camera"
        const val RESULT_GALLERY = "gallery"

        private const val ARG_TITLE = "arg_title"

        fun newInstance(
            title: String = "Profile photo"
        ): PhotoSourceBottomSheet {
            return PhotoSourceBottomSheet().apply {
                arguments = bundleOf(
                    ARG_TITLE to title
                )
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
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

        setupTexts()
        setupClickListeners()
    }

    private fun setupTexts() {
        binding.tvSheetTitle.text =
            arguments?.getString(ARG_TITLE) ?: "Profile photo"
    }

    private fun setupClickListeners() {
        binding.btnCamera.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(RESULT_KEY to RESULT_CAMERA)
            )
            dismiss()
        }

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