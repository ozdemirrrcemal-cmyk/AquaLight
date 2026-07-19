package com.aqua.aqualight.ui.common.bottomsheet

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.DialogCareProfileBinding
import com.aqua.aqualight.databinding.ItemCareProfileRowBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.math.roundToInt

/** Re-creatable care-profile summary sheet. */
class CareProfileBottomSheet : BottomSheetDialogFragment(
    R.layout.dialog_care_profile
) {

    private var _binding: DialogCareProfileBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = DialogCareProfileBinding.bind(view)
        val args = requireArguments()
        val percent = args.getInt(ARG_PERCENT).coerceIn(0, 100)
        val titles = args.getStringArrayList(ARG_TITLES).orEmpty()
        val subtitles = args.getStringArrayList(ARG_SUBTITLES).orEmpty()
        val completed = args.getBooleanArray(ARG_COMPLETED) ?: booleanArrayOf()
        val tokens = args.getStringArrayList(ARG_TOKENS).orEmpty()
        val count = minOf(titles.size, subtitles.size, completed.size, tokens.size)

        binding.tvCareProfilePercent.text = args.getString(ARG_PERCENT_TEXT).orEmpty()
        binding.tvCareProfileSummary.text = args.getString(ARG_SUMMARY_TEXT).orEmpty()
        binding.careProgressTrack.background = rounded(
            color = ContextCompat.getColor(requireContext(), R.color.aqua_care_profile_bottom_sheet_color),
            radius = resources.getDimension(R.dimen.care_profile_progress_radius)
        )
        binding.careProgressFill.background = rounded(
            color = args.getInt(ARG_PROFILE_COLOR),
            radius = resources.getDimension(R.dimen.care_profile_progress_radius)
        )
        binding.careProgressTrack.post {
            binding.careProgressFill.layoutParams =
                binding.careProgressFill.layoutParams.apply {
                    width = (binding.careProgressTrack.width * percent / 100f).roundToInt()
                }
        }

        binding.careProfileItemsContainer.removeAllViews()
        repeat(count) { index ->
            val row = ItemCareProfileRowBinding.inflate(
                layoutInflater,
                binding.careProfileItemsContainer,
                false
            )
            val isCompleted = completed[index]
            row.tvCareProfileItemTitle.text = titles[index]
            row.tvCareProfileItemSubtitle.text = subtitles[index]
            row.tvCareProfileItemStatus.text = getString(
                if (isCompleted) R.string.aquarium_care_profile_status_complete
                else R.string.aquarium_care_profile_status_missing
            )
            row.tvCareProfileItemStatus.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isCompleted) R.color.aqua_accent_positive
                    else R.color.aqua_content_warning
                )
            )
            row.tvCareProfileItemStatus.background = rounded(
                color = ContextCompat.getColor(
                    requireContext(),
                    if (isCompleted) R.color.aqua_care_profile_bottom_sheet_color_variant_2
                    else R.color.aqua_care_profile_bottom_sheet_color_variant_3
                ),
                radius = resources.getDimension(R.dimen.care_profile_status_radius),
                strokeColor = ContextCompat.getColor(
                    requireContext(),
                    if (isCompleted) R.color.aqua_care_profile_bottom_sheet_color_variant_4
                    else R.color.aqua_care_profile_bottom_sheet_color_variant_5
                ),
                strokeWidth = resources.getDimensionPixelSize(R.dimen.care_profile_status_stroke)
            )
            row.root.setOnClickListener {
                parentFragmentManager.setFragmentResult(
                    args.getString(ARG_REQUEST_KEY).orEmpty(),
                    bundleOf(RESULT_TOKEN to tokens[index])
                )
                dismiss()
            }
            binding.careProfileItemsContainer.addView(row.root)
        }
    }

    override fun onStart() {
        super.onStart()
        val sheetDialog = dialog as? BottomSheetDialog ?: return
        val sheet = sheetDialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        val maxHeight = (resources.displayMetrics.heightPixels * MAX_HEIGHT_RATIO).roundToInt()
        sheet.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.aqua_color_transparent))
        sheet.layoutParams = sheet.layoutParams.apply { height = maxHeight }
        sheetDialog.behavior.apply {
            peekHeight = maxHeight
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun rounded(
        color: Int,
        radius: Float,
        strokeColor: Int? = null,
        strokeWidth: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
            if (strokeColor != null && strokeWidth > 0) setStroke(strokeWidth, strokeColor)
        }
    }

    companion object {
        const val RESULT_TOKEN = "care_profile_action_token"

        private const val ARG_PERCENT = "arg_percent"
        private const val ARG_PERCENT_TEXT = "arg_percent_text"
        private const val ARG_SUMMARY_TEXT = "arg_summary_text"
        private const val ARG_PROFILE_COLOR = "arg_profile_color"
        private const val ARG_TITLES = "arg_titles"
        private const val ARG_SUBTITLES = "arg_subtitles"
        private const val ARG_COMPLETED = "arg_completed"
        private const val ARG_TOKENS = "arg_tokens"
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val TAG = "CareProfileBottomSheet"
        private const val MAX_HEIGHT_RATIO = 0.82f

        fun show(
            fragmentManager: FragmentManager,
            percent: Int,
            percentText: String,
            summaryText: String,
            profileColor: Int,
            titles: List<String>,
            subtitles: List<String>,
            completed: BooleanArray,
            tokens: List<String>,
            requestKey: String
        ) {
            if (fragmentManager.findFragmentByTag(TAG) != null || fragmentManager.isStateSaved) return
            CareProfileBottomSheet().apply {
                arguments = bundleOf(
                    ARG_PERCENT to percent,
                    ARG_PERCENT_TEXT to percentText,
                    ARG_SUMMARY_TEXT to summaryText,
                    ARG_PROFILE_COLOR to profileColor,
                    ARG_TITLES to ArrayList(titles),
                    ARG_SUBTITLES to ArrayList(subtitles),
                    ARG_COMPLETED to completed,
                    ARG_TOKENS to ArrayList(tokens),
                    ARG_REQUEST_KEY to requestKey
                )
            }.show(fragmentManager, TAG)
        }
    }
}
