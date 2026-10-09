package com.example.autocountmoney

import com.example.autocountmoney.notifications.WalletParser
import org.junit.Assert.*
import org.junit.Test

class WalletParserTest {
    private val momo = "com.mservice.momotransfer"

    @Test fun expenseWithBalance() {
        val tx = WalletParser.parse(momo, "Thanh toán thành công 50.000đ. Số dư ví: 1.200.000đ")!!
        assertEquals(50000L, tx.amount)
        assertEquals(1200000L, tx.balance)
    }
    @Test fun missingAndZeroBalance() {
        assertNull(WalletParser.parse(momo, "Bạn đã thanh toán 50,000 VND")!!.balance)
        assertEquals(0L, WalletParser.parse(momo, "Thanh toán thành công 50.000đ. Số dư: 0đ")!!.balance)
    }
    @Test fun ignoresOtherSourcesAndNonExpenses() {
        assertNull(WalletParser.parse("other.app", "Thanh toán thành công 50.000đ"))
        listOf("Thanh toán thất bại 50.000đ", "Nhận tiền thành công 50.000đ",
            "Nạp ví thành công 50.000đ", "Hoàn tiền thanh toán thành công 50.000đ",
            "Thanh toán không thành công 50.000đ", "Thanh toán thành công, nhận được ưu đãi 50.000đ")
            .forEach { assertNull(it, WalletParser.parse(momo, it)) }
    }
    @Test fun doesNotTreatCashbackAsAmount() {
        assertNull(WalletParser.parse(momo, "Thanh toán thành công! Ưu đãi hoàn tiền 50.000đ"))
        assertNull(WalletParser.parse(momo, "Thanh toán thành công! Số dư ví: 50.000đ"))
    }
    @Test fun zalopayAndOverflow() {
        assertEquals("ZaloPay", WalletParser.parse("vn.com.vng.zalopay", "Chuyển tiền thành công 20.000đ")!!.provider)
        assertNull(WalletParser.parse(momo, "Thanh toán thành công 999999999999999999999 VND"))
    }
    @Test fun doesNotInventWalletBalanceFromLinkedBankAccount() {
        assertNull(WalletParser.parse(momo, "Thanh toán thành công 50.000đ. Số dư tài khoản ngân hàng: 1.200.000đ")!!.balance)
    }
}
