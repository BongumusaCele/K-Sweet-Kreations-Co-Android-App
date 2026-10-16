package com.example.ksweetkreationscoapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ksweetkreationscoapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only check: never signs in, creates accounts or changes Azure business data. */
@RunWith(AndroidJUnit4::class)
class ProductionConnectionTest {
    @Test fun azureHttpsPublicCatalogAndImages() = runBlocking {
        check(BuildConfig.API_BASE_URL == "https://k-sweet-creations-co.azurewebsites.net/api/") { "Run this read-only smoke test with the default Azure build." }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SecureSession(context).clear()
        val api = ApiClient(context)
        assertEquals("healthy",api.request("health").text("status"))
        val records = Snapshot.parse(api.request("website"))
        assertTrue(records.orders.isEmpty()); assertTrue(records.customers.isEmpty()); assertTrue(records.staff.isEmpty())
        records.products.filter { it.image.isNotBlank() }.take(3).forEach {
            val image = api.document(it.image)
            assertTrue(image.mime.startsWith("image/")); assertTrue(image.bytes.isNotEmpty())
        }
        try { api.document("https://example.com/private.pdf"); fail("Cross-origin media must be refused") } catch (_: IllegalArgumentException) { }
    }
}
