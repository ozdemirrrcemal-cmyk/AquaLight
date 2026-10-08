package com.aqua.aqualight.composition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

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

    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        createFromScope(modelClass) { factory -> factory.create(modelClass) }

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras
    ): T = createFromScope(modelClass) { factory -> factory.create(modelClass, extras) }

    private fun <T : ViewModel> createFromScope(
        modelClass: Class<T>,
        create: (ScopedViewModelFactory) -> T
    ): T {
        val processBinding = processFactory.supports(modelClass)
        val ownerBinding = ownerFactory.supports(modelClass)

        check(!(processBinding && ownerBinding)) {
            "ViewModel has duplicate process and owner bindings: ${modelClass.name}"
        }

        return when {
            processBinding -> create(processFactory)
            ownerBinding -> create(ownerFactory)
            else -> throw IllegalArgumentException(
                "No registered AquaLight ViewModel binding for ${modelClass.name}."
            )
        }
    }
}
