package com.example.ksweetkreationscoapp

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ksweetkreationscoapp.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.util.UUID

data class AppState(val account: Account? = null, val records: Snapshot = Snapshot(), val loading: Boolean = true,
                    val busy: Boolean = false, val error: String = "", val notice: String = "", val screen: String = "home",
                    val cart: Map<Int, Int> = emptyMap(), val document: String? = null, val documentTitle: String = "")

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val api = ApiClient(application)
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private val history = ArrayDeque<String>()
    private val submissions = mutableMapOf<String, String>()
    private var generation = 0L
    private var refreshJob: Job? = null
    init { initialize() }

    private fun initialize() = viewModelScope.launch {
        var warning = ""
        try {
            val account = Account.parse(api.request("Auth/me"))
            mutable.update { it.copy(account = account, screen = if (account.isTeam) "workspace" else "home") }
        } catch (e: Exception) {
            if (e is ApiException && e.status == 401) api.clearSession() else warning = friendly(e)
        }
        try { if (warning.isBlank()) mutable.update { it.copy(records = Snapshot.parse(api.request("website"))) } }
        catch (e: Exception) { warning = friendly(e) }
        mutable.update { it.copy(loading = false, error = warning) }
    }
    fun navigate(destination: String) {
        if (destination == mutable.value.screen) return
        history.addLast(mutable.value.screen)
        mutable.update { it.copy(screen = destination, error = "") }
        refresh()
    }
    fun back(): Boolean {
        if (mutable.value.document != null) { closeDocument(); return true }
        if (history.isEmpty()) return false
        mutable.update { it.copy(screen = history.removeLast(), error = "") }
        return true
    }
    fun clearError() { mutable.update { it.copy(error = "") } }
    fun clearNotice() { mutable.update { it.copy(notice = "") } }
    fun notify(message: String) { mutable.update { it.copy(notice = message) } }
    fun report(message: String) { mutable.update { it.copy(error = message) } }
    fun refresh() {
        if (mutable.value.busy || mutable.value.loading || refreshJob?.isActive == true) return
        val current = ++generation
        refreshJob = viewModelScope.launch {
            try {
                if (mutable.value.account == null && api.hasSession()) {
                    val account = Account.parse(api.request("Auth/me"))
                    if (current == generation) mutable.update { it.copy(account = account) }
                }
                val data = Snapshot.parse(api.request("website"))
                if (current == generation) mutable.update { it.copy(records = data, error = "") }
            } catch (e: Exception) { if (current == generation) failed(e) }
        }
    }
    private fun clearIdentity() {
        generation++; refreshJob?.cancel(); api.clearSession(); history.clear(); submissions.clear()
        mutable.update { AppState(loading = false, screen = "account") }
    }
    private fun failed(e: Exception) {
        if (e is CancellationException) throw e
        if (e is ApiException && e.status == 401) clearIdentity()
        mutable.update { it.copy(error = friendly(e)) }
    }
    fun authenticate(values: Map<String, String>, register: Boolean, onSuccess: () -> Unit) {
        if (mutable.value.busy) return
        generation++; refreshJob?.cancel()
        mutable.update { it.copy(busy = true, error = "") }
        viewModelScope.launch {
            var registered = false
            try {
                if (register) {
                    api.request("Auth/register", "POST", json("fullName" to values["name"], "email" to values["email"]?.trim(), "phoneNumber" to values["phone"], "password" to values["password"], "confirmPassword" to values["confirmPassword"]))
                    registered = true
                }
                val user = Account.parse(api.request("Auth/login", "POST", json("email" to values["email"]?.trim(), "password" to values["password"])))
                history.clear()
                mutable.update { it.copy(account = user, records = Snapshot(), screen = if (user.isTeam) "workspace" else "home") }
                onSuccess()
                reloadAfterSave("Signed in.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // A rejected login is not an expired signed-in session: preserve the guest's cart and draft.
                mutable.update { it.copy(error = if (registered) "Your account was created, but sign-in could not finish. Choose Sign in and use your new account. ${friendly(e)}" else friendly(e)) }
            }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun logout() {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            var warning = ""
            try { api.request("Auth/logout", "POST") }
            catch (e: Exception) { if (e is CancellationException) throw e; warning = "Signed out on this device. The service could not confirm session revocation." }
            finally { clearIdentity(); mutable.update { it.copy(busy = false, error = warning, notice = "Signed out.") } }
        }
    }
    private suspend fun reloadAfterSave(message: String) {
        mutable.update { it.copy(notice = message) }
        try { mutable.update { it.copy(records = Snapshot.parse(api.request("website")), error = "") } }
        catch (e: Exception) {
            if (e is ApiException && e.status == 401) failed(e)
            else mutable.update { it.copy(error = "Your change was saved, but the latest records could not be loaded. Please refresh.") }
        }
    }
    fun action(path: String, method: String = "POST", body: JSONObject? = null, fields: Map<String, String>? = null,
               file: Uri? = null, proof: Boolean = false, message: String = "Changes saved.", onSuccess: () -> Unit = {}) {
        if (mutable.value.busy) return
        generation++; refreshJob?.cancel()
        mutable.update { it.copy(busy = true, error = "") }
        viewModelScope.launch {
            try {
                val upload = file?.let { withContext(Dispatchers.IO) { Upload.read(getApplication(), it, proof) } }
                api.request(path, method, body, fields, upload)
                onSuccess()
                reloadAfterSave(message)
            } catch (e: Exception) { failed(e) }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun saveProfile(name: String, phone: String) {
        if (mutable.value.busy) return
        generation++; refreshJob?.cancel()
        mutable.update { it.copy(busy = true, error = "") }
        viewModelScope.launch {
            try {
                val user = Account.parse(api.request("Auth/me", "PUT", json("name" to name.trim(), "phone" to phone.trim())))
                mutable.update { it.copy(account = user) }
                reloadAfterSave("Profile updated.")
            } catch (e: Exception) { failed(e) }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun add(product: Product) {
        if (!product.active) return
        mutable.update { it.copy(cart = it.cart + (product.id to ((it.cart[product.id] ?: 0) + 1).coerceAtMost(20)), notice = "${product.name} added to your cart.") }
    }
    fun quantity(id: Int, quantity: Int) { mutable.update { it.copy(cart = if (quantity <= 0) it.cart - id else it.cart + (id to quantity.coerceAtMost(20))) } }
    fun submission(kind: String): String = submissions.getOrPut(kind) { UUID.randomUUID().toString() }
    fun completeSubmission(kind: String) {
        submissions.remove(kind)
        if (kind == "order") mutable.update { it.copy(cart = emptyMap()) }
        navigate("orders")
    }
    fun openDocument(path: String, title: String) { mutable.update { it.copy(document = path, documentTitle = title) } }
    fun closeDocument() { mutable.update { it.copy(document = null, documentTitle = "") } }
    private fun friendly(e: Exception): String = when (e) {
        is ApiException -> e.message ?: "The request could not be completed."
        is IllegalArgumentException -> e.message ?: "Please check the entered details."
        is java.io.IOException -> e.message ?: "Check your connection and try again."
        else -> "The service could not complete your request. Please try again."
    }
}
