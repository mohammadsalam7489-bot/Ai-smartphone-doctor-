package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.diagnostics.RealDeviceDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AI Smartphone Doctor", appName)
    }

    @Test
    fun `device vitals read real metrics without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vitals = RealDeviceDiagnostics.getDeviceVitals(context)
        assertNotNull(vitals)
        assertTrue(vitals.healthScore in 10..100)
        assertTrue(vitals.storageTotalBytes >= 0)
    }
}
