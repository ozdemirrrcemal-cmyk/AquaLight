package com.aqua.aqualight.ui.tabs.aquarium.detail

import androidx.fragment.app.activityViewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.platform.media.AppMediaScope
import com.aqua.aqualight.ui.common.media.TankRecordPhotoFragment
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import kotlinx.coroutines.CancellationException

abstract class TankPlantPhotoFragment : TankRecordPhotoFragment(
    R.layout.fragment_tank_detail_plants, AppMediaScope.PLANT,
    R.string.aquarium_plant_photo_title, R.string.aquarium_plant_photo_crop_title
) {
    protected val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    protected val tankId: Long get() = requireArguments().getLong(TankDetailPlantsFragment.ARG_TANK_ID)
    override val photoTankId: Long get() = tankId
    private var isPhotoMutationInProgress = false
    override val photoActionsBlocked: Boolean get() = isPhotoMutationInProgress

    protected fun showPlantPhotoSource(plant: AquariumPlantTag) {
        if (photoTarget.isInProgress || photoActionsBlocked) return
        showRecordPhotoSource(
            plant.id, requireContext().requireAppContainer().authenticatedOwnerIdentity.requireOwnerUid(),
            persistedUri = plant.photoUri, resetSelection = true
        )
    }

    override suspend fun onPhotoSelected(photoUri: String?) {
        val plantId = photoTarget.recordId
        val ownerUid = photoTarget.ownerUid
        val hasTarget = plantId != null && ownerUid != null
        if (!hasPhotoView || !hasTarget || isPhotoMutationInProgress) {
            mediaFlow.rollbackSelection()
            photoTarget.finish()
            return
        }
        isPhotoMutationInProgress = true
        try {
            runCatching {
                if (photoUri == null) mediaFlow.selectRemoval()
                aquariumTankViewModel.updatePlantPhoto(
                    tankId, requireNotNull(plantId), photoUri, requireNotNull(ownerUid)
                )
                mediaFlow.commitSelection(deletePersistedMedia = false)
                val message = if (photoUri == null) R.string.aquarium_plant_photo_removed
                    else R.string.aquarium_plant_photo_updated
                showPhotoSnackBar(getString(message), BaseActivity.SnackType.SUCCESS)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                mediaFlow.rollbackSelection()
                val message = if (photoUri == null) R.string.aquarium_plant_photo_remove_failed
                    else R.string.aquarium_plant_photo_save_failed
                showPhotoSnackBar(getString(message), BaseActivity.SnackType.ERROR)
            }
        } finally {
            isPhotoMutationInProgress = false
            photoTarget.finish()
        }
    }

}
