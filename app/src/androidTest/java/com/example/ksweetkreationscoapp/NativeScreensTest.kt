package com.example.ksweetkreationscoapp

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ksweetkreationscoapp.data.SecureSession
import com.example.ksweetkreationscoapp.ui.BakeryApp
import com.example.ksweetkreationscoapp.ui.theme.KSweetKreationsCoAppTheme
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import com.example.ksweetkreationscoapp.data.*

@RunWith(AndroidJUnit4::class)
class NativeScreensTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var vm: AppViewModel
    private val store = ViewModelStore()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @Before fun open() {
        check(BuildConfig.API_BASE_URL == "http://10.0.2.2:5089/api/")
        SecureSession(context).clear()
        vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[AppViewModel::class.java]
        compose.setContent { KSweetKreationsCoAppTheme { BakeryApp(vm) } }
        compose.waitUntil(30_000) { !vm.state.value.loading }
    }
    @After fun close() { compose.runOnIdle { store.clear() }; SecureSession(context).clear() }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        instrumentation.uiAutomation.takeScreenshot().useBitmap { bitmap -> File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
    }
    private fun Bitmap.useBitmap(block: (Bitmap) -> Unit) { try { block(this) } finally { recycle() } }
    private fun prepareProof() = runBlocking {
        val admin = ApiClient(context)
        admin.request("Auth/login","POST",json("email" to "admin@integration.test","password" to "IntegrationOnly123!"))
        val suffix = UUID.randomUUID().toString().take(8)
        admin.request("website/products","POST",json("name" to "UI proof cake $suffix","description" to "Test cake","category" to "Cakes","price" to 100,"size" to "20 cm","servings" to "12","active" to true))
        val product = Snapshot.parse(admin.request("website")).products.single { it.name == "UI proof cake $suffix" }
        val client = ApiClient(context); client.clearSession()
        val email = "pdf-ui-$suffix@integration.test"
        client.request("Auth/register","POST",json("fullName" to "PDF Viewer Customer","email" to email,"phoneNumber" to "0821234567","password" to "AndroidTest123!","confirmPassword" to "AndroidTest123!"))
        client.request("Auth/login","POST",json("email" to email,"password" to "AndroidTest123!"))
        var day = com.example.ksweetkreationscoapp.domain.BakeryRules.today().plusDays(2)
        while (com.example.ksweetkreationscoapp.domain.BakeryRules.slots(day.toString()).isEmpty()) day=day.plusDays(1)
        client.request("website/orders","POST",json("submissionId" to UUID.randomUUID().toString(),"name" to "PDF Viewer Customer","phone" to "0821234567","date" to day.toString(),"time" to "09:00","items" to org.json.JSONArray().put(json("id" to product.id,"quantity" to 1))))
        val order = Snapshot.parse(client.request("website")).orders.single()
        val output = java.io.ByteArrayOutputStream()
        val pdf = android.graphics.pdf.PdfDocument()
        try { val page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(300,400,1).create()); page.canvas.drawText("EFT proof test",20f,40f,android.graphics.Paint()); pdf.finishPage(page); pdf.writeTo(output) } finally { pdf.close() }
        client.request("website/orders/${order.id}/proof","POST",fields=mapOf("reference" to "UI-PDF-$suffix"),upload=Upload("proof.pdf","application/pdf",output.toByteArray()))
        client.clearSession()
    }
    @Test fun mobileMenuRegistrationValidationAndPasswordVisibility() {
        screenshot("home")
        compose.onNodeWithContentDescription("Open navigation menu").performClick()
        compose.onNodeWithText("Gallery", useUnmergedTree = true).assertIsDisplayed()
        screenshot("navigation")
        compose.onNodeWithText("Sign in / register").performScrollTo().performClick()
        compose.onNodeWithTag("field-email").performTextInput("invalid")
        compose.onNodeWithTag("field-password").performTextInput("AndroidTest123!")
        compose.onNodeWithContentDescription("Show password").performClick()
        compose.onNodeWithContentDescription("Hide password").assertExists().performClick()
        compose.onNode(hasText("Sign in") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Enter a valid email address.").assertExists()
        compose.runOnIdle { vm.add(Product(999999,"Cart regression","","Cakes",java.math.BigDecimal("100.00"),true,"20 cm","12","")) }
        compose.onNodeWithTag("field-email").performTextReplacement("missing-${UUID.randomUUID()}@integration.test")
        compose.onNode(hasText("Sign in") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(30_000) { vm.state.value.error.isNotBlank() && !vm.state.value.busy }
        Assert.assertEquals(1,vm.state.value.cart[999999])
        compose.onNodeWithText("New here? Create an account").performScrollTo().performClick()
        val email = "ui-${UUID.randomUUID().toString().take(8)}@integration.test"
        compose.onNodeWithTag("field-name").performTextInput("Native UI Customer")
        compose.onNodeWithTag("field-phone").performTextInput("0821234567")
        compose.onNodeWithTag("field-email").performTextInput(email)
        compose.onNodeWithTag("field-password").performTextInput("AndroidTest123!")
        compose.onNodeWithTag("field-confirmPassword").performScrollTo().performTextInput("AndroidTest123!")
        compose.onNode(hasText("Create account") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(30_000) { vm.state.value.account?.email == email && !vm.state.value.busy }
        compose.runOnIdle { vm.navigate("account") }
        compose.onNodeWithText(email).assertExists()
        screenshot("customer-account")
        compose.runOnIdle { vm.navigate("products") }
        compose.onNodeWithText("Access restricted").assertExists()
        compose.runOnIdle { vm.logout() }
        compose.waitUntil(30_000) { vm.state.value.account == null && !vm.state.value.busy }
        Assert.assertTrue(vm.state.value.records.orders.isEmpty())
    }
    @Test fun adminWorkspaceProductImageFormAndRestrictedMenu() {
        prepareProof()
        compose.runOnIdle { vm.authenticate(mapOf("email" to "admin@integration.test","password" to "IntegrationOnly123!"),false) {} }
        compose.waitUntil(30_000) { vm.state.value.account?.isAdmin == true && !vm.state.value.busy }
        compose.onNodeWithText("Bakery workspace").assertExists()
        screenshot("admin-workspace")
        compose.onNodeWithContentDescription("Open navigation menu").performClick()
        compose.onNode(hasText("Products") and hasClickAction()).assertExists().performClick()
        compose.onNodeWithText("Add product").performScrollTo().performClick()
        compose.onNodeWithTag("field-name").performTextInput("Native UI cake")
        compose.onNodeWithTag("field-description").performTextInput("A delicious test cake")
        compose.onNodeWithTag("field-price").performTextInput("abc")
        compose.onNodeWithTag("field-size").performScrollTo().performTextInput("20 cm")
        compose.onNodeWithTag("field-servings").performScrollTo().performTextInput("12")
        compose.onNodeWithText("Choose photograph").performScrollTo().assertExists()
        compose.onNodeWithText("Save product").performScrollTo().performClick()
        compose.onNodeWithText("Enter a positive price with up to two decimal places.").assertExists()
        screenshot("product-upload-form")
        // Admin directories must be populated independently of any order selection.
        compose.runOnIdle { vm.navigate("customers") }
        compose.waitUntil(30_000) { vm.state.value.records.customers.isNotEmpty() }
        screenshot("admin-customers")
        val proof = vm.state.value.records.orders.flatMap { it.payments }.first { it.proofUrl.isNotBlank() }.proofUrl
        compose.runOnIdle { vm.openDocument(proof,"Test EFT proof") }
        compose.waitUntil(30_000) { compose.onAllNodesWithText("1 / 1").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("Test EFT proof").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Save").assertExists()
        val rendered = compose.onNodeWithContentDescription("Test EFT proof").captureToImage().asAndroidBitmap()
        val pixels = IntArray(rendered.width * rendered.height)
        rendered.getPixels(pixels,0,rendered.width,0,0,rendered.width,rendered.height)
        Assert.assertTrue("PDF text must be rendered on the page",pixels.count { (it and 0x00ffffff) < 0x00eeeeee } > 50)
        File(context.getExternalFilesDir(null),"pdf-rendered-page.png").outputStream().use { rendered.compress(Bitmap.CompressFormat.PNG,100,it) }
        screenshot("pdf-proof-viewer")
        compose.onNodeWithContentDescription("Close document").performClick()
        // A failed save must retain entered details and give the user a recovery action.
        compose.runOnIdle { vm.navigate("staff") }
        compose.onNodeWithTag("field-fullName").performScrollTo().performTextInput("Existing Account")
        compose.onNodeWithTag("field-email").performScrollTo().performTextInput("admin@integration.test")
        compose.onNodeWithTag("field-phoneNumber").performScrollTo().performTextInput("0821234567")
        compose.onNodeWithTag("field-password").performScrollTo().performTextInput("AndroidTest123!")
        compose.onNodeWithTag("field-confirmPassword").performScrollTo().performTextInput("AndroidTest123!")
        compose.onNode(hasText("Create staff account") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(30_000) { vm.state.value.error.isNotBlank() && !vm.state.value.busy }
        compose.onNodeWithTag("field-email").assertTextContains("admin@integration.test")
        compose.onNodeWithText("Retry / refresh").assertExists()
        screenshot("validation-error")
    }
}
