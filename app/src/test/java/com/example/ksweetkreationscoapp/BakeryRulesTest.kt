package com.example.ksweetkreationscoapp

import com.example.ksweetkreationscoapp.data.Order
import com.example.ksweetkreationscoapp.domain.BakeryRules as Rules
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BakeryRulesTest {
    private fun order(type: String = "Custom", paid: String = "50.00", approved: Boolean = true, status: String = "Awaiting confirmation") = Order(1,"KSK-1","","","",type,BigDecimal("100.00"),BigDecimal(paid),status,"2030-01-10","09:00",approved,false,"", emptyList(),emptyList(),emptyList())
    @Test fun depositsRoundExactlyAndOutstandingNeverNegative() {
        assertEquals(BigDecimal("50.50"), Rules.deposit(BigDecimal("100.99")))
        assertEquals(BigDecimal.ZERO, Rules.outstanding(order(paid = "101.00")))
        assertEquals("R 1,234.50", Rules.money(BigDecimal("1234.5")))
    }
    @Test fun collectionSlotsRespectHoursLeadTimesAndBlockedDates() {
        val now = LocalDate.of(2030, 1, 7) // Monday
        assertNotNull(Rules.collectionError("2030-01-07","09:00",false,emptyList(),now))
        assertNull(Rules.collectionError("2030-01-08","16:30",false,emptyList(),now))
        assertNotNull(Rules.collectionError("2030-01-08","17:00",false,emptyList(),now))
        assertNotNull(Rules.collectionError("2030-01-08","09:00",false,listOf("2030-01-08"),now))
        assertNotNull(Rules.collectionError("2030-01-12","15:00",false,emptyList(),now))
        assertTrue(Rules.slots("2030-01-13").isEmpty())
        assertNotNull(Rules.collectionError("2030-01-12","09:00",true,emptyList(),now))
        assertNull(Rules.collectionError("2030-01-14","09:00",true,emptyList(),now))
    }
    @Test fun orderProgressionRequiresCorrectPaymentAndApproval() {
        assertTrue(Rules.canTransition(order(), "Confirmed"))
        assertFalse(Rules.canTransition(order(approved=false), "Confirmed"))
        assertFalse(Rules.canTransition(order(type="Standard"), "Confirmed"))
        assertFalse(Rules.canTransition(order(), "Collected"))
        assertFalse(Rules.canTransition(order(status="Ready for collection"), "Collected"))
        assertTrue(Rules.canTransition(order(paid="100.00",status="Ready for collection"), "Collected"))
        assertFalse(Rules.canTransition(order(status="Cancelled"), "Confirmed"))
    }
    @Test fun contactsRejectInvalidInput() {
        assertTrue(Rules.phoneValid("+27 82 123 4567")); assertFalse(Rules.phoneValid("abcdefghi"))
        assertFalse(Rules.phoneValid("123")); assertTrue(Rules.emailValid("customer@example.com")); assertFalse(Rules.emailValid("customer@"))
    }
}
