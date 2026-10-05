package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.LIVESTOCK_HEALTH_MAX_PHOTOS
import com.aqua.aqualight.databinding.DialogLivestockHealthPhotoViewerBinding
import com.aqua.aqualight.databinding.ItemLivestockHealthPhotoViewerBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderTrailingText
import com.aqua.aqualight.ui.common.header.setupAquaHeader

internal class LivestockHealthPhotoViewerDialogFragment :
    DialogFragment(R.layout.dialog_livestock_health_photo_viewer) {

    private var _binding: DialogLivestockHealthPhotoViewerBinding? = null
    private val binding get() = _binding!!
    private var pageCallback: ViewPager2.OnPageChangeCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.AppTheme)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = DialogLivestockHealthPhotoViewerBinding.bind(view)

        val photoUris = requireNotNull(arguments?.getStringArrayList(ARG_PHOTO_URIS))
        require(photoUris.isNotEmpty() && photoUris.size <= LIVESTOCK_HEALTH_MAX_PHOTOS)
        require(photoUris.all(String::isNotBlank))

        val initialIndex = requireArguments()
            .getInt(ARG_INITIAL_INDEX, 0)
            .coerceIn(0, photoUris.lastIndex)
        val initialCounter = photoCounter(initialIndex, photoUris.size)
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.livestock_health_photo_viewer_title),
                onBackClick = { dismiss() },
                trailingText = AquaHeaderTrailingText(
                    text = initialCounter,
                    contentDescription = initialCounter
                )
            )
        )
        binding.photoViewerPager.adapter = LivestockHealthPhotoPagerAdapter(photoUris)
        binding.photoViewerPager.setCurrentItem(initialIndex, false)

        pageCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                renderPageState(position, photoUris.size)
            }
        }.also(binding.photoViewerPager::registerOnPageChangeCallback)
        renderPageState(initialIndex, photoUris.size)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(
                ColorDrawable(
                    ContextCompat.getColor(requireContext(), R.color.background_color)
                )
            )
        }
    }

    override fun onDestroyView() {
        pageCallback?.let { callback ->
            binding.photoViewerPager.unregisterOnPageChangeCallback(callback)
        }
        pageCallback = null
        binding.photoViewerPager.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun renderPageState(position: Int, total: Int) {
        val counter = photoCounter(position, total)
        binding.appHeader.tvTrailingText.text = counter
        binding.appHeader.tvTrailingText.contentDescription = counter
        binding.photoViewerIndicator.render(total, position)
    }

    private fun photoCounter(position: Int, total: Int): String =
        getString(
            R.string.livestock_health_photo_viewer_counter,
            position + 1,
            total
        )

    companion object {
        private const val ARG_PHOTO_URIS = "photo_uris"
        private const val ARG_INITIAL_INDEX = "initial_index"
        private const val TAG = "LivestockHealthPhotoViewerDialogFragment"

        fun show(
            fragmentManager: FragmentManager,
            photoUris: List<String>,
            initialIndex: Int = 0
        ) {
            require(photoUris.isNotEmpty() && photoUris.size <= LIVESTOCK_HEALTH_MAX_PHOTOS)
            require(photoUris.all(String::isNotBlank))
            if (fragmentManager.isStateSaved || fragmentManager.findFragmentByTag(TAG) != null) {
                return
            }
            LivestockHealthPhotoViewerDialogFragment().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_PHOTO_URIS, ArrayList(photoUris))
                    putInt(ARG_INITIAL_INDEX, initialIndex.coerceIn(0, photoUris.lastIndex))
                }
            }.show(fragmentManager, TAG)
        }
    }
}

private class LivestockHealthPhotoPagerAdapter(
    private val photoUris: List<String>
) : RecyclerView.Adapter<LivestockHealthPhotoPagerAdapter.PhotoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemLivestockHealthPhotoViewerBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photoUris[position], position, photoUris.size)
    }

    override fun getItemCount(): Int = photoUris.size

    class PhotoViewHolder(
        private val binding: ItemLivestockHealthPhotoViewerBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(photoUri: String, position: Int, total: Int) {
            binding.ivPhotoViewer.contentDescription = itemView.context.getString(
                R.string.livestock_health_photo_viewer_image_description,
                position + 1,
                total
            )
            binding.ivPhotoViewer.load(Uri.parse(photoUri)) {
                placeholder(R.drawable.ic_camera_24)
                error(R.drawable.ic_camera_24)
                crossfade(true)
            }
        }
    }
}
