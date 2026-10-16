package com.example.ksweetkreationscoapp.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ksweetkreationscoapp.*
import com.example.ksweetkreationscoapp.R
import com.example.ksweetkreationscoapp.data.*
import com.example.ksweetkreationscoapp.domain.BakeryRules
import com.example.ksweetkreationscoapp.domain.BakeryRules.money
import com.example.ksweetkreationscoapp.ui.theme.Lilac
import com.example.ksweetkreationscoapp.ui.theme.Plum
import org.json.JSONArray
import java.math.BigDecimal

@Composable fun HomeScreen(vm: AppViewModel, state: AppState) {
    Page("Made with love.", "Collected with a smile · Lenasia South") {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.background(Brush.linearGradient(listOf(Lilac, Color(0xFFC585BF)))).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("BAKED WITH LOVE ♡", color = Color.White, style = MaterialTheme.typography.labelLarge)
                Text("Celebrate life,\none sweet slice\nat a time.", style = MaterialTheme.typography.headlineLarge, fontStyle = FontStyle.Italic, color = Color.White)
                Text("Beautiful cakes, thoughtful details and a little magic. Made for the moments you’ll always remember.", color = Color.White)
                Button({ vm.navigate("shop") }, colors = ButtonDefaults.buttonColors(containerColor = Plum, contentColor = Color.White)) { Text("Explore our cakes") }
                Image(painterResource(R.drawable.cake_chocolate), "Chocolate celebration cake", Modifier.fillMaxWidth().height(240.dp), contentScale = ContentScale.Crop)
            }
        }
        Panel("Your next celebration") {
            Text("Custom cakes, cupcakes and desserts, made for you.")
            ActionButton("Create your cake") { vm.navigate("custom") }
            SecondaryButton("Explore our gallery") { vm.navigate("gallery") }
        }
        if (state.records.products.any { it.active }) {
            Text("Fresh favourites", style = MaterialTheme.typography.headlineSmall)
            state.records.products.filter { it.active }.take(3).forEach { ProductTile(vm, it) }
        }
        Panel("Collection only") { Text("Migson Manor, Lenasia South\nMonday–Friday 09:00–17:00\nSaturday 09:00–15:00\nClosed Sundays.") }
        SecondaryButton("Meet K Sweet Kreations Co") { vm.navigate("about") }
    }
}
@Composable fun ShopScreen(vm: AppViewModel, state: AppState, gallery: Boolean = false) {
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var sort by rememberSaveable { mutableStateOf("Featured") }
    var products = state.records.products.filter { it.active && (!gallery || it.image.isNotBlank()) && (category == "All" || it.category == category) && it.name.contains(search, true) }
    products = when (sort) { "Price: low to high" -> products.sortedBy { it.price }; "Price: high to low" -> products.sortedByDescending { it.price }; else -> products }
    LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (gallery) "Sweet inspiration" else "Find your favourite", style = MaterialTheme.typography.headlineMedium)
            SearchField(search, { search = it }, "Search cakes")
            ChoiceChips(listOf("All", "Cakes", "Cupcakes", "Desserts"), category) { category = it }
            if (!gallery) Choices("Sort", listOf("Featured", "Price: low to high", "Price: high to low"), sort) { sort = it }
        } }
        if (products.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyCard(if (state.loading) "Loading creations…" else "No matching treats", "Try another category or refresh the bakery records.") }
        items(products, key = { it.id }) { product ->
            Card(onClick = { if (gallery) vm.openDocument(product.image, product.name) else vm.navigate("product/${product.id}") }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Photo(vm.api, product.image, product.name, Modifier.fillMaxWidth().height(160.dp))
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                    Text(product.category, style = MaterialTheme.typography.bodySmall)
                    if (!gallery) { Text(money(product.price), color = MaterialTheme.colorScheme.primary); Text("${product.servings} servings", style = MaterialTheme.typography.bodySmall); ActionButton("Add to cart") { vm.add(product) } }
                }
            }
        }
    }
}
@Composable fun ProductTile(vm: AppViewModel, product: Product) {
    Panel(product.name) {
        Photo(vm.api, product.image, product.name, Modifier.fillMaxWidth().height(190.dp))
        Text(product.description)
        Text("${money(product.price)} · ${product.size} · ${product.servings} servings")
        ActionButton("View cake") { vm.navigate("product/${product.id}") }
    }
}
@Composable fun ProductScreen(vm: AppViewModel, state: AppState, id: Int) {
    val product = state.records.products.find { it.id == id && it.active }
    Page(product?.name ?: "Product unavailable") {
        if (product == null) { EmptyCard("This product is no longer available"); SecondaryButton("Browse our cakes") { vm.navigate("shop") } }
        else {
            Photo(vm.api, product.image, product.name, Modifier.fillMaxWidth().height(300.dp))
            StatusPill(product.category); Text(product.description)
            Text("${product.size} · Approximately ${product.servings} servings")
            Text(money(product.price), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            ActionButton("Add to cart") { vm.add(product) }
            SecondaryButton("View cart") { vm.navigate("cart") }
        }
    }
}
@Composable fun CartScreen(vm: AppViewModel, state: AppState) {
    val available = state.records.products.filter { it.active }.associateBy { it.id }
    val invalid = state.cart.keys.any { it !in available }
    val total = state.cart.entries.fold(BigDecimal.ZERO) { sum, (id, quantity) -> sum + (available[id]?.price ?: BigDecimal.ZERO).multiply(quantity.toBigDecimal()) }
    Page("Your sweet selection") {
        if (state.cart.isEmpty()) { EmptyCard("Your cart is empty", "Find a treat for your next celebration."); ActionButton("Browse cakes") { vm.navigate("shop") } }
        state.cart.forEach { (id, quantity) ->
            val product = available[id]
            Panel(product?.name ?: "Unavailable product") {
                if (product != null) {
                    Text("${product.size} · ${money(product.price)} each")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton({ vm.quantity(id, quantity-1) }) { AppIcon("minus", "Decrease ${product.name}") }
                        Text("$quantity", Modifier.padding(top = 12.dp))
                        IconButton({ vm.quantity(id, quantity+1) }, enabled = quantity < 20) { AppIcon("plus", "Increase ${product.name}") }
                    }
                    Text(money(product.price.multiply(quantity.toBigDecimal())))
                } else Text("This product was removed or made unavailable. Remove it before checking out.", color = MaterialTheme.colorScheme.error)
                TextButton({ vm.quantity(id, 0) }) { Text("Remove") }
            }
        }
        if (state.cart.isNotEmpty()) Panel("Total · ${money(total)}") {
            Text("Collection only. Standard orders require full EFT payment. Final prices are checked by the bakery service.")
            ActionButton(if (state.account == null) "Sign in to check out" else "Proceed to checkout", !invalid) { vm.navigate(if (state.account == null) "account" else "checkout") }
        }
    }
}
@Composable fun AccountScreen(vm: AppViewModel, state: AppState) {
    val user = state.account
    var register by rememberSaveable { mutableStateOf(false) }
    if (user != null) Page("Your account", "${user.name} · ${user.role}") {
        Panel { Text(user.email); if (user.isTeam) ActionButton("Open bakery workspace") { vm.navigate("workspace") } else ActionButton("My orders & quotes") { vm.navigate("orders") } }
        FormCard("profile-${user.id}", "Your details", listOf(InputSpec("name", "Full name", user.name, min = 2), InputSpec("phone", "Phone number", user.phone, max = 20, keyboard = KeyboardType.Phone)), state.busy, state.error, "Save profile",
            validate = { if (!BakeryRules.phoneValid(it["phone"].orEmpty())) "Enter a valid phone number." else null }) { values, _ -> vm.saveProfile(values["name"].orEmpty(), values["phone"].orEmpty()) }
        SecondaryButton("Sign out", !state.busy, vm::logout)
    } else Page(if (register) "Create your sweet space" else "Welcome back", "One account for the website and Android app.") {
        val fields = buildList {
            if (register) { add(InputSpec("name", "Full name", min = 2)); add(InputSpec("phone", "Phone number", max = 20, keyboard = KeyboardType.Phone)) }
            add(InputSpec("email", "Email address", max = 150, keyboard = KeyboardType.Email))
            add(InputSpec("password", "Password", min = 8, max = 128, password = true))
            if (register) add(InputSpec("confirmPassword", "Confirm password", min = 8, max = 128, password = true))
        }
        FormCard("auth-$register", if (register) "Join K Sweet" else "Sign in", fields, state.busy, state.error, if (register) "Create account" else "Sign in",
            validate = { when { !BakeryRules.emailValid(it["email"].orEmpty()) -> "Enter a valid email address."; register && !BakeryRules.phoneValid(it["phone"].orEmpty()) -> "Enter a valid phone number."; register && it["password"] != it["confirmPassword"] -> "Passwords must match."; else -> null } }) { values, clear -> vm.authenticate(values, register, clear) }
        TextButton({ register = !register; vm.clearError() }, enabled = !state.busy) { Text(if (register) "Already have an account? Sign in" else "New here? Create an account") }
    }
}
@Composable fun CustomerGate(vm: AppViewModel, state: AppState, content: @Composable () -> Unit) {
    when { state.account == null -> Page("Please sign in") { EmptyCard("A customer account is required"); ActionButton("Sign in / register") { vm.navigate("account") } }
        state.account.role != "Customer" -> Page("Customer account required") { EmptyCard("Use a customer account for shopping and cake requests", "Your bakery tools are available in the workspace."); ActionButton("Open workspace") { vm.navigate("workspace") } }
        else -> content()
    }
}
@Composable fun CheckoutScreen(vm: AppViewModel, state: AppState) = CustomerGate(vm, state) {
    val user = state.account!!
    var date by rememberSaveable { mutableStateOf("") }; var time by rememberSaveable { mutableStateOf("") }
    val products = state.records.products.filter { it.active }.associateBy { it.id }
    val valid = state.cart.isNotEmpty() && state.cart.keys.all { it in products }
    val total = state.cart.entries.fold(BigDecimal.ZERO) { sum, (id, qty) -> sum + (products[id]?.price ?: BigDecimal.ZERO).multiply(qty.toBigDecimal()) }
    Page("Collection checkout") {
        if (!valid) { EmptyCard("Update your cart before checking out"); ActionButton("View cart") { vm.navigate("cart") } }
        else {
            Panel("Order summary") { state.cart.forEach { (id, qty) -> Text("${products[id]?.name} × $qty · ${money(products.getValue(id).price.multiply(qty.toBigDecimal()))}") }; Text("Total · ${money(total)}", style = MaterialTheme.typography.titleMedium) }
            FormCard("checkout", "Collection details", listOf(InputSpec("name", "Full name", user.name, min = 2), InputSpec("phone", "Phone number", user.phone, max = 20, keyboard = KeyboardType.Phone), InputSpec("instructions", "Cake message / instructions", required = false, max = 1000, multiline = true)), state.busy, state.error, "Place order · ${money(total)}",
                extra = { CollectionPicker(date, time, false, state.busy, { date = it }, { time = it }); Text("Pay by EFT and submit proof from My orders. Contact the bakery for verified bank details.") },
                validate = { if (!BakeryRules.phoneValid(it["phone"].orEmpty())) "Enter a valid phone number." else BakeryRules.collectionError(date, time, false, state.records.blocked) }) { values, _ ->
                val items = JSONArray().apply { state.cart.forEach { (id, qty) -> put(json("id" to id, "quantity" to qty)) } }
                vm.action("website/orders", body = json("submissionId" to vm.submission("order"), "name" to values["name"]?.trim(), "phone" to values["phone"]?.trim(), "date" to date, "time" to time, "instructions" to values["instructions"], "items" to items), message = "Order placed. Submit your EFT proof in My orders.", onSuccess = { vm.completeSubmission("order") })
            }
        }
    }
}
@Composable fun CustomCakeScreen(vm: AppViewModel, state: AppState) = CustomerGate(vm, state) {
    var date by rememberSaveable { mutableStateOf("") }; var time by rememberSaveable { mutableStateOf("") }
    var size by rememberSaveable { mutableStateOf("20 cm · 12–16 servings") }; var flavour by rememberSaveable { mutableStateOf("Vanilla") }; var filling by rememberSaveable { mutableStateOf("Vanilla buttercream") }; var file by rememberSaveable { mutableStateOf("") }
    val user = state.account!!
    Page("Create your cake", "Share your inspiration. Our team will prepare a personal quotation, valid for three days.") {
        FormCard("custom-cake", "Tell us about your celebration", listOf(InputSpec("name", "Full name", user.name, min = 2), InputSpec("phone", "Phone number", user.phone, max = 20, keyboard = KeyboardType.Phone), InputSpec("comments", "Design, cake message and instructions", required = false, max = 1000, multiline = true)), state.busy, state.error, "Send cake request",
            extra = {
                Choices("Cake size", listOf("15 cm · 8–10 servings", "20 cm · 12–16 servings", "25 cm · 20–24 servings"), size, !state.busy) { size = it }
                Choices("Flavour", listOf("Vanilla", "Chocolate", "Red velvet"), flavour, !state.busy) { flavour = it }
                Choices("Filling", listOf("Vanilla buttercream", "Chocolate ganache", "Berry", "Discuss with bakery"), filling, !state.busy) { filling = it }
                CollectionPicker(date, time, true, state.busy, { date = it }, { time = it })
                Text("Inspiration photograph (optional)"); AttachmentPicker(file, { file = it }, enabled = !state.busy)
                Text("A verified 50% deposit and approved collection time secure your booking. The balance is due one day before collection.")
            }, validate = { if (!BakeryRules.phoneValid(it["phone"].orEmpty())) "Enter a valid phone number." else BakeryRules.collectionError(date, time, true, state.records.blocked) }) { values, _ ->
            vm.action("website/requests", fields = values + mapOf("submissionId" to vm.submission("request"), "date" to date, "time" to time, "size" to size, "flavour" to flavour, "filling" to filling), file = file.takeIf { it.isNotBlank() }?.let(Uri::parse), message = "Cake request sent to the bakery.", onSuccess = { vm.completeSubmission("request") })
        }
    }
}
