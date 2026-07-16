package com.aqua.aqualight.composition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * A closed set of ViewModel bindings for exactly one dependency scope.
 *
 * Implementations must match exact ViewModel classes. Assignable/fallback
 * resolution is intentionally forbidden because it can silently construct an
 * unregistered ViewModel with the wrong owner or process dependencies.
 */
internal interface ScopedViewModelFactory : ViewModelProvider.Factory {
    fun supports(modelClass: Class<out ViewModel>): Boolean
}
