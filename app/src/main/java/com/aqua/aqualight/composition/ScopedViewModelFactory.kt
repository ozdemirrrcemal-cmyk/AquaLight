package com.aqua.aqualight.composition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

/**
 * A closed set of ViewModel bindings for exactly one dependency scope.
 *
 * Implementations must match exact ViewModel classes. Assignable/fallback
 * resolution is intentionally forbidden because it can silently construct an
 * unregistered ViewModel with the wrong owner or process dependencies.
 */
internal interface ScopedViewModelFactory : ViewModelProvider.Factory {
    fun supports(modelClass: Class<out ViewModel>): Boolean

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        create(modelClass)
}
