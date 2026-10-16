package com.example.ksweetkreationscoapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.ksweetkreationscoapp.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val publicDestinations = listOf("home" to "Home", "shop" to "Our cakes", "gallery" to "Gallery", "custom" to "Custom cake", "orders" to "My orders", "about" to "About us", "faq" to "FAQs", "reviews" to "Customer love", "contact" to "Contact")
private val teamRoutes = setOf("workspace", "team-orders", "team-requests", "products", "collections", "enquiries", "feedback", "customers", "staff")
private val adminRoutes = setOf("products", "customers", "staff", "product-edit")

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BakeryApp(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val snack = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.refresh(); while (true) { delay(30_000); vm.refresh() } } }
    LaunchedEffect(state.notice) { if (state.notice.isNotBlank()) { snack.showSnackbar(state.notice); vm.clearNotice() } }
    // Key the whole navigation tree to the signed-in identity so another account never inherits form drafts.
    key(state.account?.id, state.account?.role) {
        val route = state.screen.substringBefore('/')
        val teamScreen = route in teamRoutes || route == "product-edit" || (state.account?.isTeam == true && route in setOf("order", "request"))
        BackHandler(drawer.isOpen || state.screen != "home" && state.screen != "workspace") {
            if (drawer.isOpen) scope.launch { drawer.close() }
            else if (!vm.back()) vm.navigate(if (state.account?.isTeam == true) "workspace" else "home")
        }
        ModalNavigationDrawer(drawerState = drawer, drawerContent = {
            ModalDrawerSheet(Modifier.widthIn(max = 330.dp).fillMaxHeight()) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("K Sweet", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
                    Text("KREATIONS CO", style = MaterialTheme.typography.labelMedium)
                    Text(state.account?.let { "${it.name} · ${it.role}" } ?: "Made with love. Collected with a smile.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp))
                    if (state.account?.isTeam == true) {
                        Text("BAKERY WORKSPACE", style = MaterialTheme.typography.labelSmall)
                        teamDestinations(state.account!!.isAdmin).forEach { (destination, title) -> NavigationDrawerItem(label = { Text(title) }, selected = route == destination, onClick = { vm.navigate(destination); scope.launch { drawer.close() } }) }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    }
                    Text("CUSTOMER WEBSITE", style = MaterialTheme.typography.labelSmall)
                    publicDestinations.forEach { (destination, title) -> NavigationDrawerItem(label = { Text(title) }, selected = route == destination, onClick = { vm.navigate(destination); scope.launch { drawer.close() } }) }
                    NavigationDrawerItem(label = { Text(if (state.account == null) "Sign in / register" else "My account") }, selected = route == "account", onClick = { vm.navigate("account"); scope.launch { drawer.close() } })
                    if (state.account != null) TextButton({ scope.launch { drawer.close() }; vm.logout() }, enabled = !state.busy) { Text("Sign out") }
                }
            }
        }) {
            Scaffold(snackbarHost = { SnackbarHost(snack) }, topBar = {
                TopAppBar(title = { Column { Text("K Sweet", style = MaterialTheme.typography.titleLarge); Text(if (teamScreen) "BAKERY WORKSPACE" else "KREATIONS CO", style = MaterialTheme.typography.labelSmall) } }, navigationIcon = {
                    IconButton({ scope.launch { drawer.open() } }) { AppIcon("menu", "Open navigation menu") }
                }, actions = {
                    IconButton(vm::refresh, enabled = !state.busy && !state.loading) { AppIcon("refresh", "Refresh records") }
                    if (!teamScreen) IconButton({ vm.navigate("cart") }) {
                        BadgedBox(badge = { if (state.cart.isNotEmpty()) Badge { Text(state.cart.values.sum().toString()) } }) { AppIcon("cart", "Shopping cart") }
                    }
                })
            }, bottomBar = {
                if (!teamScreen) NavigationBar {
                    listOf(Triple("home", "Home", "home"), Triple("shop", "Cakes", "cake"), Triple("orders", "Orders", "orders"), Triple("account", "Account", "account")).forEach { (destination, label, icon) ->
                        NavigationBarItem(selected = route == destination, onClick = { vm.navigate(destination) }, icon = { AppIcon(icon) }, label = { Text(label) })
                    }
                }
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                    if (state.busy || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = if (state.busy) "Saving changes" else "Loading bakery records" })
                    if (state.error.isNotBlank()) Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(state.error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            Row { TextButton(vm::refresh, enabled = !state.busy && !state.loading) { Text("Retry / refresh") }; TextButton(vm::clearError) { Text("Dismiss") } }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        else if ((teamScreen && state.account?.isTeam != true) || (route in adminRoutes && state.account?.isAdmin != true)) Page("Access restricted") { EmptyCard("Sign in with the required bakery account"); ActionButton("Open account") { vm.navigate("account") } }
                        else Route(vm, state)
                    }
                }
            }
        }
        state.document?.let { DocumentViewer(vm, it, state.documentTitle) }
    }
}
@Composable private fun Route(vm: AppViewModel, state: AppState) {
    val route = state.screen.substringBefore('/')
    val id = state.screen.substringAfter('/', "").toIntOrNull()
    when (route) {
        "home" -> HomeScreen(vm, state)
        "shop" -> ShopScreen(vm, state)
        "gallery" -> ShopScreen(vm, state, true)
        "product" -> ProductScreen(vm, state, id ?: -1)
        "cart" -> CartScreen(vm, state)
        "account" -> AccountScreen(vm, state)
        "checkout" -> CheckoutScreen(vm, state)
        "custom" -> CustomCakeScreen(vm, state)
        "orders" -> CustomerGate(vm, state) { OrdersScreen(vm, state) }
        "order" -> if (state.account != null) OrderScreen(vm, state, id ?: -1) else CustomerGate(vm, state) {}
        "request" -> if (state.account != null) RequestScreen(vm, state, id ?: -1) else CustomerGate(vm, state) {}
        "about" -> AboutScreen(vm)
        "faq" -> FaqScreen(vm)
        "contact" -> ContactScreen(vm, state)
        "reviews" -> ReviewsScreen(vm, state)
        "workspace" -> WorkspaceScreen(vm, state)
        "team-orders" -> OrdersScreen(vm, state, true)
        "team-requests" -> RequestsScreen(vm, state)
        "products" -> ProductsScreen(vm, state)
        "product-edit" -> ProductEditor(vm, state, id)
        "collections" -> CollectionsScreen(vm, state)
        "enquiries" -> EnquiriesScreen(vm, state)
        "feedback" -> FeedbackScreen(vm, state)
        "customers" -> DirectoryScreen(vm, state)
        "staff" -> DirectoryScreen(vm, state, true)
        else -> HomeScreen(vm, state)
    }
}
