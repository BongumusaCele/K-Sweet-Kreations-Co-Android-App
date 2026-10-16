package com.example.ksweetkreationscoapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ksweetkreationscoapp.AppState
import com.example.ksweetkreationscoapp.AppViewModel
import com.example.ksweetkreationscoapp.R
import com.example.ksweetkreationscoapp.data.json
import com.example.ksweetkreationscoapp.domain.BakeryRules

@Composable fun AboutScreen(vm: AppViewModel) = Page("A little joy in every celebration.", "Meet K Sweet Kreations Co") {
    Image(painterResource(R.drawable.cake_celebration), "A decorated celebration cake", Modifier.fillMaxWidth().height(260.dp), contentScale = ContentScale.Crop)
    Panel { Text("Based in Lenasia South, Migson Manor, our bakery specialises in novelty and custom cakes, cupcakes and desserts for celebrations, events and gatherings."); Text("Share your inspiration and preferences, and our team will prepare a personal quotation."); Text("Collection only · Monday–Friday 09:00–17:00 · Saturday 09:00–15:00. Closed Sundays."); ActionButton("Plan your custom cake") { vm.navigate("custom") } }
}
@Composable fun FaqScreen(vm: AppViewModel) = Page("A few sweet answers") {
    listOf(
        "How do I order?" to "Choose available cakes and check out, or send a custom cake request. Your orders and quotations are available in My orders.",
        "How much notice do you need?" to "Standard orders need at least one day’s notice. Custom cakes need at least seven days. Collection depends on bakery availability and approval.",
        "Do you deliver?" to "Orders are collected in Migson Manor, Lenasia South. We are open Monday–Friday 09:00–17:00 and Saturday 09:00–15:00. Closed Sundays.",
        "How do quotations work?" to "The bakery prepares your custom quotation. It is valid for three days. Accept it in My orders to create your custom cake order.",
        "How do I pay?" to "Pay by EFT using bank details verified directly with the bakery. Upload PDF, JPG, PNG or WebP proof (up to 5 MB). The bakery verifies payment before it counts toward your order.",
        "What deposit is required?" to "Custom cakes require a verified 50% deposit to secure the booking, with the balance due one day before collection. Standard orders require full payment.",
        "Can I cancel?" to "Request cancellation from your order details. The bakery reviews it and handles any refund directly.",
        "Can you cater for allergies?" to "Contact the bakery before ordering to discuss ingredients, dietary requirements and possible cross-contamination."
    ).forEach { (question, answer) -> var expanded by rememberSaveable(question) { mutableStateOf(false) }; Panel { TextButton({ expanded = !expanded }) { Text(if (expanded) "− $question" else "+ $question", style = MaterialTheme.typography.titleMedium) }; if (expanded) Text(answer) } }
    SecondaryButton("Ask the bakery") { vm.navigate("contact") }
}
@Composable fun ContactScreen(vm: AppViewModel, state: AppState) = Page("Let’s make something sweet", "Send an enquiry to the bakery.") {
    Panel("Visit for collection") { Text("Migson Manor, Lenasia South\nMonday–Friday 09:00–17:00\nSaturday 09:00–15:00\nClosed Sundays.") }
    FormCard("enquiry-${state.account?.id}", "Your enquiry", listOf(InputSpec("name", "Full name", state.account?.name.orEmpty(), min = 2), InputSpec("email", "Email address", state.account?.email.orEmpty(), max = 150, keyboard = KeyboardType.Email), InputSpec("subject", "Subject", max = 150), InputSpec("message", "Message", min = 10, max = 2000, multiline = true)), state.busy, state.error, "Send enquiry", validate = { if (!BakeryRules.emailValid(it["email"].orEmpty())) "Enter a valid email address." else null }) { values, clear ->
        vm.action("website/enquiries", body = json(*values.map { it.key to it.value.trim() }.toTypedArray()), message = "Enquiry sent to the bakery.", onSuccess = clear)
    }
}
@Composable fun ReviewsScreen(vm: AppViewModel, state: AppState) = Page("A little customer love", "Feedback is reviewed by the bakery before appearing publicly.") {
    val published = state.records.reviews.filter { it.status == "Published" }
    if (published.isEmpty()) EmptyCard("The first sweet words are still to come", "There are no published reviews yet.")
    published.forEach { Panel(it.name) { Text("★".repeat(it.rating.coerceIn(1,5)), color = MaterialTheme.colorScheme.primary); Text(it.message) } }
    if (state.account == null) ActionButton("Sign in to leave feedback") { vm.navigate("account") }
    else if (state.account.role == "Customer") {
        var rating by rememberSaveable { mutableStateOf("5") }
        var sent by rememberSaveable { mutableStateOf(false) }
        if (sent) EmptyCard("Thank you for sharing", "Your feedback is awaiting Admin review.")
        else FormCard("review-${state.account.id}", "Share a sweet moment", listOf(InputSpec("name", "Display name", state.account.name, max = 60), InputSpec("message", "Your feedback", max = 1000, multiline = true)), state.busy, state.error, "Submit feedback", extra = { Choices("Rating out of 5", listOf("5", "4", "3", "2", "1"), rating, !state.busy) { rating = it } }) { values, clear ->
            vm.action("website/reviews", body = json("name" to values["name"], "message" to values["message"], "rating" to rating.toInt()), message = "Feedback submitted for review.", onSuccess = { clear(); sent = true })
        }
    }
}
