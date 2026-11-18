package com.campusbite.domain

import com.campusbite.data.local.entity.*
import org.junit.Assert.*
import org.junit.Test

class OrderRulesTest {
    private fun item(price: Long, quantity: Int) = OrderItemSnapshot("a", "Lunch", price, quantity, null)
    @Test fun totalsUseExactIntegerVnd() { assertEquals(126000L, OrderRules.total(listOf(item(35000, 2), item(28000, 2)))) }
    @Test(expected = IllegalArgumentException::class) fun rejectEmptyCart() { OrderRules.total(emptyList()) }
    @Test(expected = IllegalArgumentException::class) fun rejectNegativePrices() { OrderRules.total(listOf(item(-1, 1))) }
    @Test(expected = IllegalArgumentException::class) fun rejectZeroQuantity() { OrderRules.total(listOf(item(10000, 0))) }
    @Test(expected = IllegalArgumentException::class) fun rejectUnboundedQuantity() { OrderRules.total(listOf(item(10000, 100))) }
    @Test(expected = ArithmeticException::class) fun rejectOverflow() { OrderRules.total(listOf(item(Long.MAX_VALUE, 2))) }
    @Test fun cancellationStopsAfterQueued() { OrderStatus.entries.forEach { assertEquals(it == OrderStatus.QUEUED, OrderRules.canCancel(it)) } }
    @Test fun capacityNeverGoesNegative() { assertEquals(0, OrderRules.remaining(20, 25)); assertEquals(4, OrderRules.remaining(20, 16)) }
}
