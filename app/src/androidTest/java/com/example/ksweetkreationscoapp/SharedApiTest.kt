package com.example.ksweetkreationscoapp

import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ksweetkreationscoapp.data.*
import com.example.ksweetkreationscoapp.domain.BakeryRules
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Runs only against tests/start-test-api.mjs, never against Azure. */
@RunWith(AndroidJUnit4::class)
class SharedApiTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun fails(status: Int, work: suspend () -> Unit) {
        try { work(); fail("Expected HTTP $status") } catch (e: ApiException) { assertEquals(e.message, status, e.status) }
    }
    @Test fun customerStaffAdminShareRecordsAndEnforcePermissions() = runBlocking {
        check(BuildConfig.API_BASE_URL == "http://10.0.2.2:5089/api/") { "Integration mutations require the disposable local API." }
        SecureSession(context).clear()
        val admin = ApiClient(context)
        val suffix = UUID.randomUUID().toString().take(8)
        val email = "android-$suffix@integration.test"
        val password = "AndroidTest123!"
        suspend fun snapshot(client: ApiClient) = Snapshot.parse(client.request("website"))
        suspend fun login(client: ApiClient, address: String, pass: String = password) = Account.parse(client.request("Auth/login", "POST", json("email" to address, "password" to pass)))
        login(admin,"admin@integration.test","IntegrationOnly123!")
        val customer = ApiClient(context)
        customer.clearSession()
        customer.request("Auth/register", "POST", json("fullName" to "Android Customer", "email" to email, "phoneNumber" to "0821234567", "password" to password, "confirmPassword" to password))
        val directory = snapshot(admin).customers.single { it.email == email }
        assertEquals(0, directory.orderCount); assertEquals(0, directory.requestCount)
        assertEquals("Customer", login(customer,email).role)
        // A fresh client must restore the encrypted session after process recreation.
        val restored = ApiClient(context)
        assertEquals(email, Account.parse(restored.request("Auth/me")).email)
        customer.request("Auth/me","PUT",json("name" to "Updated Android Customer","phone" to "0831234567"))
        assertEquals("Updated Android Customer",snapshot(admin).customers.single { it.email == email }.name)
        val staffEmail = "staff-$suffix@integration.test"
        admin.request("Auth/staff","POST",json("fullName" to "Android Staff", "email" to staffEmail,"phoneNumber" to "0821234567","password" to password,"confirmPassword" to password))
        val staff = ApiClient(context); staff.clearSession(); assertEquals("Staff", login(staff,staffEmail).role)
        val productFields = mapOf("name" to "Android cake $suffix", "description" to "Native Android integration cake", "category" to "Cakes", "price" to "150.99", "size" to "20 cm", "servings" to "12", "active" to "true")
        val image = Upload("cake.jpg","image/jpeg",context.resources.openRawResource(R.drawable.cake_chocolate).use { it.readBytes() })
        admin.request("website/products/upload","POST",fields=productFields,upload=image)
        val product = snapshot(customer).products.single { it.name == productFields["name"] }
        assertEquals("150.99", product.price.toPlainString()); assertTrue(customer.document(product.image).bytes.isNotEmpty())
        fails(403) { staff.request("website/products/${product.id}","DELETE") }
        fails(403) { customer.request("website/products/${product.id}","DELETE") }
        var collection = BakeryRules.today().plusDays(10)
        while (BakeryRules.slots(collection.toString()).isEmpty()) collection = collection.plusDays(1)
        val date = collection.toString()
        val submission = UUID.randomUUID().toString()
        val checkout = json("submissionId" to submission,"name" to "Android Customer","phone" to "0821234567","date" to date,"time" to "09:00","instructions" to "Happy birthday","items" to JSONArray().put(json("id" to product.id,"quantity" to 2)))
        customer.request("website/orders","POST",checkout); customer.request("website/orders","POST",checkout)
        assertEquals(1,snapshot(customer).orders.size)
        val order = snapshot(customer).orders.single()
        assertEquals("301.98",order.total.toPlainString())
        fails(409) { customer.request("website/orders","POST",json("submissionId" to submission,"name" to "Changed","phone" to "0821234567","date" to date,"time" to "09:00","items" to JSONArray().put(json("id" to product.id,"quantity" to 1)))) }
        fails(409) { admin.request("website/orders/${order.id}/status","PATCH",json("status" to "Confirmed")) }
        val pdfBytes = ByteArrayOutputStream().apply { val pdf = PdfDocument(); try { val page=pdf.startPage(PdfDocument.PageInfo.Builder(300,400,1).create()); page.canvas.drawText("Test EFT proof",20f,40f,android.graphics.Paint()); pdf.finishPage(page); pdf.writeTo(this) } finally { pdf.close() } }.toByteArray()
        val pdf = Upload("proof.pdf","application/pdf",pdfBytes)
        customer.request("website/orders/${order.id}/proof","POST",fields=mapOf("reference" to "ANDROID-$suffix"),upload=pdf)
        val payment = snapshot(customer).orders.single().payments.single()
        assertEquals("application/pdf",customer.document(payment.proofUrl).mime)
        assertArrayEquals(pdfBytes,staff.document(payment.proofUrl).bytes)
        fails(409) { customer.request("website/orders/${order.id}/proof","POST",fields=mapOf("reference" to "DUP-$suffix"),upload=pdf) }
        fails(403) { staff.request("website/payments/${payment.id}/verify","PATCH",json("status" to "Verified")) }
        admin.request("website/payments/${payment.id}/verify","PATCH",json("status" to "Rejected"))
        customer.request("website/orders/${order.id}/proof","POST",fields=mapOf("reference" to "RETRY-$suffix"),upload=pdf)
        val replacement = snapshot(customer).orders.single().payments.single { it.status == "Awaiting verification" }
        admin.request("website/payments/${replacement.id}/verify","PATCH",json("status" to "Verified"))
        staff.request("website/orders/${order.id}/approve-slot","POST")
        for (status in listOf("Confirmed","In progress","Ready for collection","Collected")) staff.request("website/orders/${order.id}/status","PATCH",json("status" to status))
        assertEquals("Collected",snapshot(customer).orders.single().status)
        customer.request("website/requests","POST",fields=mapOf("submissionId" to UUID.randomUUID().toString(),"name" to "Android Customer","phone" to "0821234567","date" to date,"time" to "10:00","size" to "20 cm","flavour" to "Vanilla","filling" to "Berry","comments" to "Native inspiration"),upload=image)
        val request = snapshot(customer).requests.single()
        assertTrue(customer.document(request.image).bytes.isNotEmpty())
        staff.request("website/requests/${request.id}/status","PATCH",json("status" to "Under review"))
        staff.request("website/requests/${request.id}/quote","POST",json("amount" to "500.99","notes" to "Collection reviewed, berry filling."))
        assertEquals("Quoted",snapshot(customer).requests.single().status)
        customer.request("website/requests/${request.id}/respond","POST",json("status" to "Accepted"))
        val custom = snapshot(customer).orders.single { it.type == "Custom" }
        customer.request("website/orders/${custom.id}/proof","POST",fields=mapOf("reference" to "DEPOSIT-$suffix"),upload=pdf)
        val deposit = snapshot(customer).orders.single { it.id == custom.id }.payments.single()
        assertEquals(0, java.math.BigDecimal("250.50").compareTo(deposit.amount))
        admin.request("website/payments/${deposit.id}/verify","PATCH",json("status" to "Verified"))
        staff.request("website/orders/${custom.id}/status","PATCH",json("status" to "Confirmed"))
        customer.request("website/orders/${custom.id}/cancel","POST")
        fails(403) { staff.request("website/orders/${custom.id}/status","PATCH",json("status" to "Cancelled")) }
        admin.request("website/orders/${custom.id}/status","PATCH",json("status" to "Cancelled"))
        customer.request("website/enquiries","POST",json("name" to "Android Customer","email" to email,"subject" to suffix,"message" to "Please discuss ingredients for this cake."))
        val enquiry = snapshot(staff).enquiries.single { it.subject == suffix }
        staff.request("website/enquiries/${enquiry.id}/status","PATCH",json("status" to "Resolved"))
        customer.request("website/reviews","POST",json("name" to suffix,"rating" to 5,"message" to "A wonderful integration cake."))
        val review = snapshot(admin).reviews.single { it.name == suffix }
        assertFalse(snapshot(customer).reviews.any { it.id == review.id })
        admin.request("website/reviews/${review.id}/status","PATCH",json("status" to "Published"))
        assertTrue(snapshot(customer).reviews.any { it.id == review.id })
        val blocked = collection.plusDays(2).toString()
        admin.request("website/blocked/$blocked","PUT"); assertTrue(blocked in snapshot(customer).blocked)
        admin.request("website/blocked/$blocked","DELETE"); assertFalse(blocked in snapshot(customer).blocked)
        admin.request("website/products/${product.id}/upload","PUT",fields=productFields + ("active" to "false"))
        assertFalse(snapshot(customer).products.any { it.id == product.id })
        admin.request("website/products/${product.id}","DELETE")
        assertFalse(snapshot(admin).products.any { it.id == product.id })
        assertTrue(snapshot(customer).orders.single { it.id == order.id }.items.any { it.name == product.name })
        val other = ApiClient(context); other.clearSession()
        fails(401) { other.document(payment.proofUrl) }
        other.request("Auth/register","POST",json("fullName" to "Other Customer","email" to "other-$email","phoneNumber" to "0821234567","password" to password,"confirmPassword" to password))
        login(other,"other-$email"); assertTrue(snapshot(other).orders.isEmpty()); assertTrue(snapshot(other).customers.isEmpty())
        fails(404) { other.document(payment.proofUrl) }; fails(404) { other.document(request.image) }
        customer.request("Auth/logout","POST"); customer.clearSession(); fails(401) { restored.request("Auth/me") }
        SecureSession(context).clear()
    }
}
