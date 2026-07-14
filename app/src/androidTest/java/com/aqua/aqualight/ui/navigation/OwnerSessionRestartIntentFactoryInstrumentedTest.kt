package com.aqua.aqualight.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnerSessionRestartIntentFactoryInstrumentedTest {

    @Test
    fun ownerChangeCreatesFreshMainTask() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = OwnerSessionRestartIntentFactory.create(context)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags containsFlag Intent.FLAG_ACTIVITY_NEW_TASK)
        assertTrue(intent.flags containsFlag Intent.FLAG_ACTIVITY_CLEAR_TASK)
        assertTrue(intent.flags containsFlag Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    private infix fun Int.containsFlag(flag: Int): Boolean {
        return this and flag == flag
    }
}
