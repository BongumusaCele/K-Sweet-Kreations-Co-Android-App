package com.example.ksweetkreationscoapp.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ksweetkreationscoapp.*
import com.example.ksweetkreationscoapp.data.*
import com.example.ksweetkreationscoapp.domain.BakeryRules
import com.example.ksweetkreationscoapp.domain.BakeryRules.money

@Composable fun OrdersScreen(vm: AppViewModel, state: AppState, team: Boolean = false) {
    var search by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("All") }
    Page(if (team) "Bakery orders" else "My orders & quotes") {
        SearchField(search, { search = it }, "Search reference or customer")
        Choices("Order status", listOf("All", "Awaiting confirmation", "Confirmed", "In progress", "Ready for collection", "Collected", "Cancelled"), status) { status = it }
        val orders = state.records.orders.filter { (status == "All" || it.status == status) && "${it.reference} ${it.name} ${it.phone}".contains(search, true) }
        if (orders.isEmpty()) EmptyCard("No matching orders", "New orders appear here after checkout or accepting a quote.")
        orders.forEach { order -> Panel(order.reference) {
            StatusPill(order.status); Text("${order.name} · ${order.type}")
            Text("Collection: ${order.date} at ${order.time}")
            Text("${money(order.total)} · ${BakeryRules.paymentState(order)}")
            if (order.cancelRequested) Text("Cancellation requested", color = MaterialTheme.colorScheme.error)
            ActionButton("View order") { vm.navigate("order/${order.id}") }
        } }
        if (!team) {
            Text("Cake requests & quotes", style = MaterialTheme.typography.headlineSmall)
            if (state.records.requests.isEmpty()) EmptyCard("No cake requests yet")
            state.records.requests.forEach { RequestSummary(vm, it) }
            SecondaryButton("Request a custom cake") { vm.navigate("custom") }
        }
    }
}
@Composable fun RequestSummary(vm: AppViewModel, request: CakeRequest) = Panel("Cake request #${request.id}") {
    StatusPill(request.status); Text("${request.name} · ${request.size}")
    Text("${request.date} at ${request.time} · ${request.flavour}")
    request.amount?.let { Text("Quote · ${money(it)}") }
    ActionButton("View request") { vm.navigate("request/${request.id}") }
}
@Composable fun RequestScreen(vm: AppViewModel, state: AppState, id: Int) {
    val request = state.records.requests.find { it.id == id }
    var confirm by remember { mutableStateOf("") }
    Page("Cake request #$id") {
        if (request == null) EmptyCard("Request unavailable", "Refresh to load your latest requests.")
        else {
            val team = state.account?.isTeam == true
            Panel("Celebration details") {
                StatusPill(request.status)
                Text("${request.name}\n${request.phone}")
                Text("${request.size}\n${request.flavour} · ${request.filling}")
                Text("Preferred collection: ${request.date} at ${request.time}")
                if (request.comments.isNotBlank()) Text(request.comments)
                if (request.image.isNotBlank()) SecondaryButton("View inspiration photograph") { vm.openDocument(request.image, "Cake inspiration") }
            }
            request.amount?.let { amount -> Panel("Your quotation · ${money(amount)}") {
                Text(request.notes)
                Text("50% deposit · ${money(BakeryRules.deposit(amount))}")
                if (request.expiresAt.isNotBlank()) Text("Valid until ${BakeryRules.timestamp(request.expiresAt)} (South Africa)")
                if (!team && request.status == "Quoted") {
                    if (BakeryRules.expired(request.expiresAt)) Text("This quote has expired. Contact the bakery to request a new quotation.")
                    else {
                        Text("Accepting creates a custom order. A verified deposit secures your booking; the balance is due one day before collection.")
                        ActionButton("Accept quote", !state.busy) { confirm = "Accepted" }
                        SecondaryButton("Decline quote", !state.busy) { confirm = "Declined" }
                    }
                }
            } }
            if (team && request.status !in listOf("Accepted", "Declined")) {
                var reviewed by rememberSaveable(id) { mutableStateOf(false) }
                FormCard("quote-$id", "Prepare quotation", listOf(InputSpec("amount", "Quote amount (R)", request.amount?.toPlainString().orEmpty(), keyboard = KeyboardType.Decimal, max = 12), InputSpec("notes", "Quote notes", request.notes, max = 1000, multiline = true)), state.busy, state.error, "Send quote",
                    extra = { Row { Checkbox(reviewed, { reviewed = it }, enabled = !state.busy); Text("I have reviewed the requested collection date and time.", Modifier.padding(top = 12.dp)) } },
                    validate = { when { it["amount"]?.toBigDecimalOrNull()?.let { a -> a > java.math.BigDecimal.ZERO && a.scale() <= 2 } != true -> "Enter a positive amount with up to two decimal places."; !reviewed -> "Review the requested collection slot before quoting."; else -> null } }) { values, _ ->
                    vm.action("website/requests/$id/quote", body = json("amount" to values.getValue("amount").toBigDecimal(), "notes" to values["notes"]), message = "Quotation sent.")
                }
                SecondaryButton("Mark under review", !state.busy) { vm.action("website/requests/$id/status", "PATCH", json("status" to "Under review")) }
                SecondaryButton("Decline request", !state.busy) { confirm = "Declined" }
            }
        }
    }
    if (confirm.isNotBlank()) AlertDialog(onDismissRequest = { if (!state.busy) confirm = "" }, title = { Text(if (confirm == "Accepted") "Accept this quote?" else "Decline this request?") }, text = { Text(if (confirm == "Accepted") "An order will be created for this quotation. You can then upload your EFT deposit proof." else "The bakery and customer will see the updated status.") }, confirmButton = { TextButton({
        val decision = confirm
        vm.action("website/requests/$id/${if (state.account?.isTeam == true) "status" else "respond"}", if (state.account?.isTeam == true) "PATCH" else "POST", json("status" to decision), message = "Request ${decision.lowercase()}.", onSuccess = { confirm = "" })
    }, enabled = !state.busy) { Text("Confirm") } }, dismissButton = { TextButton({ confirm = "" }, enabled = !state.busy) { Text("Keep request") } })
}
@Composable fun OrderScreen(vm: AppViewModel, state: AppState, id: Int) {
    val order = state.records.orders.find { it.id == id }
    var confirm by remember { mutableStateOf("") }
    var file by rememberSaveable(id) { mutableStateOf("") }
    Page(order?.reference ?: "Order") {
        if (order == null) EmptyCard("Order unavailable", "Refresh to load the latest records.")
        else {
            val team = state.account?.isTeam == true
            val admin = state.account?.isAdmin == true
            val terminal = order.status in listOf("Collected", "Cancelled")
            Panel("Order details") {
                StatusPill(order.status); Text("${order.name}\n${order.phone}")
                Text("Collection: ${order.date} at ${order.time}")
                Text(if (order.slotApproved) "Collection time approved" else "Collection time awaiting bakery approval")
                Text("${order.type} order"); if (order.instructions.isNotBlank()) Text(order.instructions)
                order.items.forEach { Text("${it.name} · ${it.size} × ${it.quantity}\n${money(it.price.multiply(it.quantity.toBigDecimal()))}") }
            }
            Panel("Payment summary") {
                Text("Total · ${money(order.total)}\nVerified payments · ${money(order.paid)}\nOutstanding · ${money(BakeryRules.outstanding(order))}")
                StatusPill(BakeryRules.paymentState(order))
                if (order.type == "Custom") Text("Deposit · ${money(BakeryRules.deposit(order.total))}\nBalance due one day before collection.")
                Text("Only verified EFT payments count toward the balance.")
            }
            if (!team && !terminal && BakeryRules.outstanding(order).signum() > 0) {
                if (order.payments.any { it.status == "Awaiting verification" }) EmptyCard("Proof awaiting verification", "The bakery will review your proof before another payment can be submitted.")
                else FormCard("proof-$id", "Submit EFT payment proof", listOf(InputSpec("reference", "EFT reference", max = 100)), state.busy, state.error, "Upload proof",
                    extra = { Text("Transfer ${money(if (order.type == "Custom" && order.paid.signum() == 0) BakeryRules.deposit(order.total) else BakeryRules.outstanding(order))}. Contact the bakery for verified bank details. Your proof is reviewed before payment is confirmed."); AttachmentPicker(file, { file = it }, proof = true, enabled = !state.busy) },
                    validate = { if (file.isBlank()) "Choose your EFT proof file." else null }) { values, clear ->
                    vm.action("website/orders/$id/proof", fields = values, file = Uri.parse(file), proof = true, message = "Proof submitted for verification.", onSuccess = { clear(); file = "" })
                }
            }
            if (order.payments.isNotEmpty()) Panel("Payment records") {
                order.payments.forEach { payment ->
                    StatusPill(payment.status); Text("${money(payment.amount)} · ${payment.method}\nReference: ${payment.reference}")
                    if (payment.proofUrl.isNotBlank()) SecondaryButton("View payment proof") { vm.openDocument(payment.proofUrl, "EFT proof · ${order.reference}") }
                    if (admin && payment.status == "Awaiting verification") {
                        ActionButton("Verify payment", !state.busy) { confirm = "verify/${payment.id}" }
                        SecondaryButton("Reject payment", !state.busy) { confirm = "reject/${payment.id}" }
                    }
                    HorizontalDivider()
                }
            }
            if (team && !terminal) Panel("Bakery actions") {
                if (!order.slotApproved) ActionButton("Approve collection time", !state.busy) { vm.action("website/orders/$id/approve-slot", message = "Collection approved.") }
                val next = mapOf("Awaiting confirmation" to "Confirmed", "Confirmed" to "In progress", "In progress" to "Ready for collection", "Ready for collection" to "Collected")[order.status]
                if (next != null) { ActionButton("Mark $next", !state.busy && BakeryRules.canTransition(order, next)) { vm.action("website/orders/$id/status", "PATCH", json("status" to next)) }; if (!BakeryRules.canTransition(order, next)) Text("Approve collection and verify the required payment before progressing. Full payment is required for collection.", style = MaterialTheme.typography.bodySmall) }
                if (order.cancelRequested) Text("Customer requested cancellation. An administrator must review any refund separately.")
                if (admin) SecondaryButton("Cancel order", !state.busy) { confirm = "admin-cancel" }
            }
            if (!team && !terminal) {
                if (order.cancelRequested) EmptyCard("Cancellation requested", "The bakery will review your request. Contact them about any refund.")
                else SecondaryButton("Request cancellation", !state.busy) { confirm = "cancel" }
            }
            Panel("Order timeline") { order.history.forEach { Text("${it.status}\n${BakeryRules.timestamp(it.at)}", style = MaterialTheme.typography.bodyMedium); HorizontalDivider() } }
        }
    }
    if (confirm.isNotBlank()) AlertDialog(onDismissRequest = { if (!state.busy) confirm = "" }, title = { Text(when { confirm.startsWith("verify") -> "Verify this payment?"; confirm.startsWith("reject") -> "Reject this proof?"; else -> "Cancel this order?" }) }, text = { Text(when { confirm.startsWith("verify") -> "Confirm that the EFT has cleared in the bakery’s bank account. This updates the paid balance."; confirm.startsWith("reject") -> "The customer will be able to submit a replacement proof."; confirm == "admin-cancel" -> "The order will be cancelled. Handle any refund directly with the customer."; else -> "This sends a cancellation request to the bakery for review." }) }, confirmButton = { TextButton({
        val value = confirm
        when {
            value.startsWith("verify/") || value.startsWith("reject/") -> vm.action("website/payments/${value.substringAfter('/')}/verify", "PATCH", json("status" to if (value.startsWith("verify")) "Verified" else "Rejected"), onSuccess = { confirm = "" })
            value == "admin-cancel" -> vm.action("website/orders/$id/status", "PATCH", json("status" to "Cancelled"), onSuccess = { confirm = "" })
            else -> vm.action("website/orders/$id/cancel", message = "Cancellation requested.", onSuccess = { confirm = "" })
        }
    }, enabled = !state.busy) { Text("Confirm") } }, dismissButton = { TextButton({ confirm = "" }, enabled = !state.busy) { Text("Go back") } })
}
