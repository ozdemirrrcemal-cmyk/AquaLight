package com.aqua.aqualight.composition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Process-level dispatcher for the two explicit ViewModel dependency scopes.
 *
 * Unknown and duplicate bindings fail closed. No Android default factory is
 * consulted, so every production ViewModel dependency remains visible in the
 * composition root.
 */
internal class AquaViewModelFactory(
    private val processFactory: ScopedViewModelFactory,
    private val ownerFactory: ScopedViewModelFactory
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val processBinding = processFactory.supports(modelClass)
        val ownerBinding = ownerFactory.supports(modelClass)

        check(!(processBinding && ownerBinding)) {
            "ViewModel has duplicate process and owner bindings: ${modelClass.name}"
        }

        return when {
            processBinding -> processFactory.create(modelClass)
            ownerBinding -> ownerFactory.create(modelClass)
            else -> throw IllegalArgumentException(
                "No registered AquaLight ViewModel binding for ${modelClass.name}."
            )
        }
    }
}
