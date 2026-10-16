package com.example.ksweetkreationscoapp.domain

import com.example.ksweetkreationscoapp.data.Order
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

object BakeryRules {
    val zone: ZoneId = ZoneId.of("Africa/Johannesburg")
    fun today(): LocalDate = LocalDate.now(zone)
    fun money(value: BigDecimal): String = "R " + DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)).format(value)
    fun deposit(total: BigDecimal): BigDecimal = total.divide(BigDecimal(2), 2, RoundingMode.HALF_UP)
    fun outstanding(order: Order): BigDecimal = (order.total - order.paid).max(BigDecimal.ZERO)
    fun paymentState(order: Order): String = when {
        order.paid >= order.total -> "Fully paid"
        order.type == "Custom" && order.paid >= deposit(order.total) -> "Deposit paid"
        else -> "Awaiting payment"
    }
    fun slots(date: String): List<String> {
        val day = runCatching { LocalDate.parse(date).dayOfWeek }.getOrNull() ?: return emptyList()
        if (day == DayOfWeek.SUNDAY) return emptyList()
        val end = if (day == DayOfWeek.SATURDAY) 15 else 17
        return (18 until end * 2).map { "%02d:%02d".format(Locale.US, it / 2, (it % 2) * 30) }
    }
    fun collectionError(date: String, time: String, custom: Boolean, blocked: List<String>, now: LocalDate = today()): String? {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return "Choose a collection date."
        if (parsed < now.plusDays(if (custom) 7 else 1)) return if (custom) "Custom cakes require seven days’ notice." else "Choose a collection date from tomorrow."
        if (date in blocked || time !in slots(date)) return "Choose an available collection date and time. Sundays are closed."
        return null
    }
    fun canTransition(order: Order, next: String): Boolean {
        if (order.status in listOf("Cancelled", "Collected")) return false
        val expected = mapOf("Awaiting confirmation" to "Confirmed", "Confirmed" to "In progress", "In progress" to "Ready for collection", "Ready for collection" to "Collected")
        if (expected[order.status] != next) return false
        if (next == "Confirmed" && (!order.slotApproved || order.paid < if (order.type == "Custom") deposit(order.total) else order.total)) return false
        return next != "Collected" || order.paid >= order.total
    }
    fun expired(at: String): Boolean = runCatching { !Instant.parse(at).isAfter(Instant.now()) }.getOrDefault(true)
    fun timestamp(at: String): String = runCatching { Instant.parse(at).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK)) }.getOrDefault(at)
    fun phoneValid(value: String) = value.length in 7..20 && value.count(Char::isDigit) >= 7 && value.matches(Regex("\\+?[0-9 ()-]+"))
    fun emailValid(value: String) = value.length <= 150 && value.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
}
