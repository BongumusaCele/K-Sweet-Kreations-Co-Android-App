package com.example.ksweetkreationscoapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

data class Account(val id: Int, val name: String, val email: String, val phone: String, val role: String) {
    val isAdmin get() = role == "Admin"
    val isTeam get() = role == "Admin" || role == "Staff"
    companion object {
        fun parse(o: JSONObject) = Account(o.getInt("userId"), o.getString("fullName"), o.getString("email"), o.optString("phoneNumber"), o.getString("role"))
    }
}
data class Product(val id: Int, val name: String, val description: String, val category: String, val price: BigDecimal,
                   val active: Boolean, val size: String, val servings: String, val image: String)
data class CakeRequest(val id: Int, val owner: String, val name: String, val phone: String, val size: String,
                       val flavour: String, val filling: String, val comments: String, val date: String, val time: String,
                       val status: String, val amount: BigDecimal?, val notes: String, val expiresAt: String, val image: String)
data class OrderItem(val name: String, val size: String, val image: String, val quantity: Int, val price: BigDecimal)
data class Payment(val id: Int, val amount: BigDecimal, val method: String, val status: String, val reference: String, val proofUrl: String)
data class History(val status: String, val at: String)
data class Order(val id: Int, val reference: String, val owner: String, val name: String, val phone: String, val type: String,
                 val total: BigDecimal, val paid: BigDecimal, val status: String, val date: String, val time: String,
                 val slotApproved: Boolean, val cancelRequested: Boolean, val instructions: String,
                 val items: List<OrderItem>, val payments: List<Payment>, val history: List<History>)
data class Enquiry(val id: Int, val name: String, val email: String, val subject: String, val message: String, val status: String)
data class Review(val id: Int, val name: String, val rating: Int, val message: String, val status: String)
data class DirectoryAccount(val id: Int, val name: String, val email: String, val phone: String, val active: Boolean,
                            val createdAt: String, val orderCount: Int, val requestCount: Int)
data class Snapshot(val products: List<Product> = emptyList(), val requests: List<CakeRequest> = emptyList(),
                    val orders: List<Order> = emptyList(), val enquiries: List<Enquiry> = emptyList(),
                    val reviews: List<Review> = emptyList(), val customers: List<DirectoryAccount> = emptyList(),
                    val staff: List<DirectoryAccount> = emptyList(), val blocked: List<String> = emptyList()) {
    companion object {
        fun parse(o: JSONObject): Snapshot {
            for (key in listOf("products", "requests", "orders", "enquiries", "reviews", "customers", "staff", "blocked"))
                require(o.optJSONArray(key) != null) { "The bakery service returned incomplete records. Please refresh." }
            return Snapshot(
                o.getJSONArray("products").objects { Product(it.getInt("id"), it.getString("name"), it.text("description"), it.text("category"), it.amount("price"), it.optBoolean("active"), it.text("size"), it.text("servings"), it.text("image")) },
                o.getJSONArray("requests").objects { CakeRequest(it.getInt("id"), it.text("owner"), it.text("name"), it.text("phone"), it.text("size"), it.text("flavour"), it.text("filling"), it.text("comments"), it.text("date"), it.text("time"), it.text("status"), if (it.isNull("amount")) null else it.amount("amount"), it.text("notes"), it.text("expiresAt"), it.optJSONObject("image")?.text("url") ?: "") },
                o.getJSONArray("orders").objects { it -> Order(it.getInt("id"), it.text("reference"), it.text("owner"), it.text("name"), it.text("phone"), it.text("type"), it.amount("total"), it.amount("paid"), it.text("status"), it.text("date"), it.text("time"), it.optBoolean("slotApproved"), it.optBoolean("cancelRequested"), it.text("instructions"),
                    it.getJSONArray("items").objects { i -> OrderItem(i.text("name"), i.text("size"), i.text("image"), i.getInt("quantity"), i.amount("price")) },
                    it.getJSONArray("payments").objects { p -> Payment(p.getInt("id"), p.amount("amount"), p.text("method"), p.text("status"), p.text("reference"), p.text("proofUrl")) },
                    it.getJSONArray("history").objects { h -> History(h.text("status"), h.text("at")) }) },
                o.getJSONArray("enquiries").objects { Enquiry(it.getInt("id"), it.text("name"), it.text("email"), it.text("subject"), it.text("message"), it.text("status")) },
                o.getJSONArray("reviews").objects { Review(it.getInt("id"), it.text("name"), it.getInt("rating"), it.text("message"), it.text("status")) },
                o.getJSONArray("customers").objects(::directory), o.getJSONArray("staff").objects(::directory),
                o.getJSONArray("blocked").let { a -> (0 until a.length()).map { a.getString(it) } }
            )
        }
        private fun directory(o: JSONObject) = DirectoryAccount(o.getInt("id"), o.text("name"), o.text("email"), o.text("phone"), o.optBoolean("active"), o.text("createdAt"), o.optInt("orderCount"), o.optInt("requestCount"))
    }
}
fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key, "")
fun JSONObject.amount(key: String): BigDecimal = get(key).toString().toBigDecimal()
fun <T> JSONArray.objects(transform: (JSONObject) -> T): List<T> = (0 until length()).map { transform(getJSONObject(it)) }
fun json(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }
