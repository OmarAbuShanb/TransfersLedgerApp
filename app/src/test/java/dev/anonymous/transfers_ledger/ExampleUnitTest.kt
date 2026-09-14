package dev.anonymous.transfers_ledger

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testPayToFriendWithWalletIsIncoming() {
        val text = "تحويل دفع لصديق: maha abdalla fathi al haj ahmad - WALLET, بمبلغ 49.00 ILS"
        val parsed = dev.anonymous.transfers_ledger.core.notification.PaymentNotificationParser.parse(
            dev.anonymous.transfers_ledger.core.PaymentSources.PALPAY_PACKAGE,
            text
        )
        assertNotNull(parsed)
        assertEquals("maha abdalla fathi al haj ahmad - WALLET", parsed!!.sender)
        assertEquals("49.00", parsed.amount)
        assertEquals(dev.anonymous.transfers_ledger.domain.model.TransactionDirection.INCOMING, parsed.direction)
        assertEquals(dev.anonymous.transfers_ledger.domain.model.DirectionSource.DEFAULT, parsed.directionSource)
    }
}