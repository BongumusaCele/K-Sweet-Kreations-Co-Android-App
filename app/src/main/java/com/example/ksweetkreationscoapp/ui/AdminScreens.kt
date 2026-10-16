package com.example.ksweetkreationscoapp.ui

import android.net.Uri
import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ksweetkreationscoapp.*
import com.example.ksweetkreationscoapp.data.*
import com.example.ksweetkreationscoapp.domain.BakeryRules
import com.example.ksweetkreationscoapp.domain.BakeryRules.money
import java.time.LocalDate

@Composable fun WorkspaceScreen(vm: AppViewModel, state: AppState) = Page("Bakery workspace", "Welcome, ${state.account?.name.orEmpty()} · ${state.account?.role.orEmpty()}") {
    Panel("Today’s overview") {
        Text("${state.records.orders.count { it.status !in listOf("Collected", "Cancelled") }} open orders\n${state.records.requests.count { it.status !in listOf("Accepted", "Declined", "Expired") }} cake requests\n${state.records.orders.sumOf { o -> o.payments.count { it.status == "Awaiting verification" } }} payments awaiting verification\n${state.records.enquiries.count { it.status != "Resolved" }} open enquiries")
        Text("Records refresh when you return and every 30 seconds while the app is open.", style = MaterialTheme.typography.bodySmall)
        SecondaryButton("Refresh records", !state.busy, vm::refresh)
    }
    teamDestinations(state.account?.isAdmin == true).filter { it.first != "workspace" }.forEach { (route, label) -> Panel(label) { ActionButton("Open $label") { vm.navigate(route) } } }
    SecondaryButton("View customer storefront") { vm.navigate("home") }
}
fun teamDestinations(admin: Boolean) = buildList {
    add("workspace" to "Overview"); add("team-orders" to "Orders"); add("team-requests" to "Cake requests")
    if (admin) add("products" to "Products")
    add("collections" to "Collections"); add("enquiries" to "Enquiries"); add("feedback" to "Feedback")
    if (admin) { add("customers" to "Customers"); add("staff" to "Staff accounts") }
}
@Composable fun RequestsScreen(vm: AppViewModel, state: AppState) {
    var search by rememberSaveable { mutableStateOf("") }; var status by rememberSaveable { mutableStateOf("All") }
    Page("Cake requests") {
        SearchField(search, { search = it }, "Search customer or request")
        Choices("Status", listOf("All", "Submitted", "Under review", "Quoted", "Accepted", "Declined", "Expired"), status) { status = it }
        val requests = state.records.requests.filter { (status == "All" || it.status == status) && "${it.id} ${it.name} ${it.phone}".contains(search, true) }
        if (requests.isEmpty()) EmptyCard("No matching cake requests")
        requests.forEach { RequestSummary(vm, it) }
    }
}
@Composable fun ProductsScreen(vm: AppViewModel, state: AppState) {
    var search by rememberSaveable { mutableStateOf("") }; var deleting by remember { mutableStateOf<Product?>(null) }
    Page("Products", "Manage the same catalog used by the website and Android app.") {
        ActionButton("Add product", !state.busy) { vm.navigate("product-edit/new") }
        SearchField(search, { search = it }, "Search products")
        val products = state.records.products.filter { "${it.name} ${it.category}".contains(search, true) }
        if (products.isEmpty()) EmptyCard("No matching products")
        products.forEach { product -> Panel(product.name) {
            Photo(vm.api, product.image, product.name, Modifier.fillMaxWidth().height(180.dp))
            Text("${money(product.price)} · ${product.category}"); StatusPill(if (product.active) "Available" else "Unavailable")
            ActionButton("Edit product", !state.busy) { vm.navigate("product-edit/${product.id}") }
            SecondaryButton("Delete product", !state.busy) { deleting = product }
        } }
    }
    deleting?.let { product -> AlertDialog(onDismissRequest = { if (!state.busy) deleting = null }, title = { Text("Delete ${product.name}?") }, text = { Text("This removes the product from the catalog. Existing orders keep their product and payment history.") }, confirmButton = { TextButton({ vm.action("website/products/${product.id}", "DELETE", message = "Product deleted.", onSuccess = { deleting = null }) }, enabled = !state.busy) { Text("Delete product", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ deleting = null }, enabled = !state.busy) { Text("Keep product") } }) }
}
@Composable fun ProductEditor(vm: AppViewModel, state: AppState, id: Int?) {
    val product = state.records.products.find { it.id == id }
    var category by rememberSaveable(id) { mutableStateOf(product?.category ?: "Cakes") }
    var active by rememberSaveable(id) { mutableStateOf(product?.active ?: true) }
    var file by rememberSaveable(id) { mutableStateOf("") }
    Page(if (id == null) "Add a sweet treat" else "Edit product") {
        if (id != null && product == null) EmptyCard("Product unavailable", "It may have been deleted. Refresh to see the latest catalog.")
        else FormCard("product-${id ?: "new"}", "Product details", listOf(InputSpec("name", "Product name", product?.name.orEmpty(), max = 150), InputSpec("description", "Description", product?.description.orEmpty(), max = 1000, multiline = true), InputSpec("price", "Price (R)", product?.price?.toPlainString().orEmpty(), max = 12, keyboard = KeyboardType.Decimal), InputSpec("size", "Size", product?.size.orEmpty(), max = 100), InputSpec("servings", "Servings", product?.servings.orEmpty(), max = 100)), state.busy, state.error, "Save product",
            extra = {
                Choices("Category", listOf("Cakes", "Cupcakes", "Desserts"), category, !state.busy) { category = it }
                Row { Checkbox(active, { active = it }, enabled = !state.busy); Text("Available to order", Modifier.padding(top = 12.dp)) }
                if (product?.image?.isNotBlank() == true) Photo(vm.api, product.image, product.name, Modifier.fillMaxWidth().height(160.dp))
                Text(if (id == null) "Product photograph" else "Replace photograph (optional)"); AttachmentPicker(file, { file = it }, enabled = !state.busy)
            }, validate = { values -> when {
                values["price"]?.toBigDecimalOrNull()?.let { it > java.math.BigDecimal.ZERO && it.scale() <= 2 } != true -> "Enter a positive price with up to two decimal places."
                id == null && file.isBlank() -> "Choose a product photograph."
                else -> null
            } }) { values, _ ->
            vm.action(if (id == null) "website/products/upload" else "website/products/$id/upload", if (id == null) "POST" else "PUT", fields = values.mapValues { it.value.trim() } + mapOf("category" to category, "active" to active.toString()), file = file.takeIf { it.isNotBlank() }?.let(Uri::parse), message = "Product saved.", onSuccess = { vm.navigate("products") })
        }
    }
}
@Composable fun CollectionsScreen(vm: AppViewModel, state: AppState) {
    val context = LocalContext.current
    var chosen by rememberSaveable { mutableStateOf("") }; var removing by remember { mutableStateOf("") }
    Page("Collection diary", "All times use South African time.") {
        val upcoming = state.records.orders.filter { it.status !in listOf("Collected", "Cancelled") && it.date >= BakeryRules.today().toString() }.sortedWith(compareBy({ it.date }, { it.time }))
        if (upcoming.isEmpty()) EmptyCard("No upcoming collections")
        upcoming.forEach { order -> Panel("${order.date} · ${order.time}") { Text("${order.reference} · ${order.name}"); StatusPill(order.status); Text(if (order.slotApproved) "Collection approved" else "Awaiting approval"); ActionButton("View order") { vm.navigate("order/${order.id}") } } }
        Panel("Blocked dates") {
            Text("Customers cannot choose these dates for new orders or cake requests.")
            state.records.blocked.sorted().forEach { date -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(date, Modifier.padding(top = 14.dp)); if (state.account?.isAdmin == true) TextButton({ removing = date }, enabled = !state.busy) { Text("Unblock") } } }
            if (state.records.blocked.isEmpty()) Text("No dates are blocked.")
            if (state.account?.isAdmin == true) {
                SecondaryButton(if (chosen.isBlank()) "Choose date to block" else chosen, !state.busy) {
                    val date = BakeryRules.today().plusDays(1)
                    DatePickerDialog(context, { _, y, m, d -> chosen = LocalDate.of(y,m+1,d).toString() }, date.year,date.monthValue-1,date.dayOfMonth).apply { datePicker.minDate = BakeryRules.today().atStartOfDay(BakeryRules.zone).toInstant().toEpochMilli() }.show()
                }
                ActionButton("Block date", !state.busy && chosen.isNotBlank()) { vm.action("website/blocked/$chosen", "PUT", message = "Collection date blocked.", onSuccess = { chosen = "" }) }
                Text("The service prevents blocking dates with existing bookings.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (removing.isNotBlank()) AlertDialog(onDismissRequest = { if (!state.busy) removing = "" }, title = { Text("Unblock $removing?") }, text = { Text("Customers will be able to request collection on this date during normal opening hours.") }, confirmButton = { TextButton({ vm.action("website/blocked/$removing", "DELETE", onSuccess = { removing = "" }) }, enabled = !state.busy) { Text("Unblock") } }, dismissButton = { TextButton({ removing = "" }, enabled = !state.busy) { Text("Keep blocked") } })
}
@Composable fun EnquiriesScreen(vm: AppViewModel, state: AppState) {
    var search by rememberSaveable { mutableStateOf("") }; var filter by rememberSaveable { mutableStateOf("All") }
    Page("Customer enquiries") {
        SearchField(search, { search = it }); ChoiceChips(listOf("All", "New", "In progress", "Resolved"), filter) { filter = it }
        val list = state.records.enquiries.filter { (filter == "All" || it.status == filter) && "${it.name} ${it.email} ${it.subject} ${it.message}".contains(search,true) }
        if (list.isEmpty()) EmptyCard("No matching enquiries")
        list.forEach { enquiry -> Panel(enquiry.subject) { Text("${enquiry.name}\n${enquiry.email}"); Text(enquiry.message); StatusPill(enquiry.status); Choices("Update status", listOf("New", "In progress", "Resolved"), enquiry.status, !state.busy) { vm.action("website/enquiries/${enquiry.id}/status", "PATCH", json("status" to it)) } } }
    }
}
@Composable fun FeedbackScreen(vm: AppViewModel, state: AppState) = Page("Customer feedback", "An administrator reviews feedback before publishing it.") {
    if (state.records.reviews.isEmpty()) EmptyCard("No feedback to review yet")
    state.records.reviews.forEach { review -> Panel("${review.name} · ${review.rating}/5") {
        StatusPill(review.status); Text(review.message)
        if (state.account?.isAdmin == true) {
            ActionButton("Publish feedback", !state.busy && review.status != "Published") { vm.action("website/reviews/${review.id}/status", "PATCH", json("status" to "Published")) }
            SecondaryButton("Hide feedback", !state.busy && review.status != "Hidden") { vm.action("website/reviews/${review.id}/status", "PATCH", json("status" to "Hidden")) }
        }
    } }
}
@Composable fun DirectoryScreen(vm: AppViewModel, state: AppState, staff: Boolean = false) {
    var search by rememberSaveable { mutableStateOf("") }
    Page(if (staff) "Staff accounts" else "Customers", if (staff) "Staff can manage orders, cake requests, collections and enquiries." else "Includes registered customers who have not placed any orders.") {
        SearchField(search, { search = it }, "Search name, email or phone")
        val list = (if (staff) state.records.staff else state.records.customers).filter { "${it.name} ${it.email} ${it.phone}".contains(search, true) }
        if (list.isEmpty()) EmptyCard("No matching accounts")
        list.forEach { person -> Panel(person.name) { Text("${person.email}\n${person.phone}"); StatusPill(if (person.active) "Active" else "Inactive"); Text("Joined ${BakeryRules.timestamp(person.createdAt)}"); if (!staff) Text("${person.orderCount} orders · ${person.requestCount} cake requests") } }
        if (staff) FormCard("new-staff", "Create staff account", listOf(InputSpec("fullName", "Full name", min = 2), InputSpec("email", "Email address", max = 150, keyboard = KeyboardType.Email), InputSpec("phoneNumber", "Phone number", max = 20, keyboard = KeyboardType.Phone), InputSpec("password", "Password", min = 8, max = 128, password = true), InputSpec("confirmPassword", "Confirm password", min = 8, max = 128, password = true)), state.busy, state.error, "Create staff account", validate = { when { !BakeryRules.emailValid(it["email"].orEmpty()) -> "Enter a valid email address."; !BakeryRules.phoneValid(it["phoneNumber"].orEmpty()) -> "Enter a valid phone number."; it["password"] != it["confirmPassword"] -> "Passwords must match."; else -> null } }) { values, clear ->
            vm.action("Auth/staff", body = json(*values.map { it.key to it.value }.toTypedArray()), message = "Staff account created.", onSuccess = clear)
        }
    }
}
