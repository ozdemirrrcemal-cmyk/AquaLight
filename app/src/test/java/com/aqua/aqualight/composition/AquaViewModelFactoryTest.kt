package com.aqua.aqualight.composition

import androidx.lifecycle.ViewModel
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class AquaViewModelFactoryTest {

    @Test
    fun `routes exact process and owner bindings`() {
        val processViewModel = ProcessTestViewModel()
        val ownerViewModel = OwnerTestViewModel()
        val factory = AquaViewModelFactory(
            processFactory = FakeScopedFactory(
                ProcessTestViewModel::class.java,
                processViewModel
            ),
            ownerFactory = FakeScopedFactory(
                OwnerTestViewModel::class.java,
                ownerViewModel
            )
        )

        assertSame(
            processViewModel,
            factory.create(ProcessTestViewModel::class.java)
        )
        assertSame(
            ownerViewModel,
            factory.create(OwnerTestViewModel::class.java)
        )
    }

    @Test
    fun `unknown ViewModel fails closed`() {
        val factory = AquaViewModelFactory(
            processFactory = FakeScopedFactory(
                ProcessTestViewModel::class.java,
                ProcessTestViewModel()
            ),
            ownerFactory = FakeScopedFactory(
                OwnerTestViewModel::class.java,
                OwnerTestViewModel()
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownTestViewModel::class.java)
        }
    }

    @Test
    fun `duplicate scope binding fails closed`() {
        val factory = AquaViewModelFactory(
            processFactory = FakeScopedFactory(
                ProcessTestViewModel::class.java,
                ProcessTestViewModel()
            ),
            ownerFactory = FakeScopedFactory(
                ProcessTestViewModel::class.java,
                ProcessTestViewModel()
            )
        )

        assertThrows(IllegalStateException::class.java) {
            factory.create(ProcessTestViewModel::class.java)
        }
    }

    private class FakeScopedFactory<T : ViewModel>(
        private val modelClass: Class<T>,
        private val viewModel: T
    ) : ScopedViewModelFactory {

        override fun supports(modelClass: Class<out ViewModel>): Boolean {
            return modelClass == this.modelClass
        }

        override fun <R : ViewModel> create(modelClass: Class<R>): R {
            check(supports(modelClass))
            @Suppress("UNCHECKED_CAST")
            return viewModel as R
        }
    }

    private class ProcessTestViewModel : ViewModel()
    private class OwnerTestViewModel : ViewModel()
    private class UnknownTestViewModel : ViewModel()
}
