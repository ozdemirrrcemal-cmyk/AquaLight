package com.aqua.aqualight.ui.common.media

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.aqua.aqualight.application.media.MediaScope
import com.aqua.aqualight.composition.requireAppContainer

/**
 * Central presentation wiring for the shared media coordinator.
 * Feature screens provide only semantic scope, owner token and crop policy.
 */
internal fun Fragment.mediaFlowFactory(
    scope: MediaScope,
    ownerToken: () -> String,
    cropSpec: MediaCropSpec
): ViewModelProvider.Factory {
    val container = requireContext().requireAppContainer()
    return MediaFlowCoordinatorViewModel.factory(
        context = requireContext().applicationContext,
        configuration = MediaFlowConfiguration(
            scope = scope,
            ownerToken = ownerToken(),
            ownerUid = container.authenticatedOwnerIdentity.requireOwnerUid(),
            cropSpec = cropSpec
        ),
        mediaOperations = container.mediaFlowOperations
    )
}
