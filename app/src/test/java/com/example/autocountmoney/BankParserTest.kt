package com.example.autocountmoney

import com.example.autocountmoney.notifications.NotificationSamples
import com.example.autocountmoney.notifications.WalletParser
import org.junit.Assert.*
import org.junit.Test

/** Synthetic fixtures test supported grammar; real device captures are still required. */
class BankParserTest {
    private val banks = listOf("com.mbmobile", "com.tpb.mb.gprsandroid", "com.VCB")
    private fun parse(text: String) = WalletParser.parse(banks.first(), text)
    private fun reject(vararg texts: String) = texts.forEach { text ->
        banks.forEach { assertNull("$it: $text", WalletParser.parse(it, text)) }
    }

    @Test fun demosUseParserForEveryProvider() {
        WalletParser.providers.forEach { (pkg, provider) ->
            for (withBalance in listOf(true, false)) {
                val tx = WalletParser.parse(pkg, NotificationSamples.expense(provider, withBalance))!!
                assertEquals(provider, tx.provider)
                assertEquals(50000L, tx.amount)
                assertEquals(if (withBalance) 1200000L else null, tx.balance)
            }
        }
    }

    @Test fun mbSignedDebitAndAvailableBalance() {
        val tx = parse("Biến động số dư\nTK 000000000001 GD: -50,000VND lúc 09/10/2026 10:20; SD: 1,200,000VND; ND: an trua")!!
        assertEquals("MB Bank", tx.provider)
        assertEquals(50000L, tx.amount)
        assertEquals(1200000L, tx.balance)
    }

    @Test fun tpDecreaseWithAccents() {
        val tx = WalletParser.parse(banks[1], "Tài khoản 000000000002 giảm 50.000 VND. Số dư khả dụng: 0 VND")!!
        assertEquals("TPBank", tx.provider)
        assertEquals(50000L, tx.amount)
        assertEquals(0L, tx.balance)
    }

    @Test fun vcbLabeledTransactionAmount() {
        val tx = WalletParser.parse(banks[2], "Số dư tài khoản\nTK: 000000000003; Số tiền GD: -50,000 VND; Số dư: 1,200,000 VND. Nội dung: thanh toan")!!
        assertEquals("Vietcombank", tx.provider)
        assertEquals(50000L, tx.amount)
        assertEquals(1200000L, tx.balance)
    }

    @Test fun missingBalanceAndMaskedAccount() {
        assertNull(parse("TK ****0001 GD: -50000 VND")!!.balance)
        assertEquals(50000L, parse("Tai khoan xxx0001 ghi no: 50000 dong")!!.amount)
        assertEquals(50000L, parse("TK 0001 −50.000₫")!!.amount)
    }

    @Test fun incomingMoneyIsNeverAnExpense() {
        reject("TK 0001 GD: +50,000VND; SD: 100,000VND", "TK 0001 tăng số dư 50.000 VND",
            "TK 0001 ghi có 50.000 VND", "TK 0001 GD: +50,000VND; ND: GD: -50,000VND")
    }

    @Test fun ignoresOtpFailedPendingAndAds() {
        reject("OTP TK 0001 GD: -50,000VND", "Mã xác thực TK 0001 GD: -50,000VND",
            "TK 0001 GD: -50,000VND thất bại", "TK 0001 GD: -50,000VND không thành công",
            "TK 0001 GD: -50,000VND đang xử lý", "Ưu đãi TK 0001 GD: -50,000VND")
    }

    @Test fun rejectsExplicitInternalTransfersCashWithdrawalTopupsAndRefunds() {
        reject("TK 0001 GD: -50,000VND; ND: chuyển tiền nội bộ", "TK 0001 GD: -50,000VND; ND: rút tiền ATM",
            "TK 0001 GD: -50,000VND; ND: nạp ví", "TK 0001 GD: -50,000VND; ND: tiết kiệm",
            "TK 0001 GD: -50,000VND; ND: hoàn tiền")
    }

    @Test fun memoCannotInjectAnAmountOrBalance() {
        reject("TK 0001 SD: 100,000VND; ND: GD: -50,000VND")
        val tx = parse("TK 0001 GD: -50,000VND; ND: số dư: 9,000,000VND GD: -1,000VND")!!
        assertEquals(50000L, tx.amount)
        assertNull(tx.balance)
        assertEquals(50000L, parse("TK 0001 GD: -50,000VND; ND: tra tien OTP mua hang")!!.amount)
    }

    @Test fun balancesAccountNumbersAndFeesAreNotExpenses() {
        reject("TK 50000 SD: 50,000VND", "TK 0001 SD: -50,000VND",
            "TK 0001 Phí: -50,000VND", "TK 0001 SD: 1,000,000VND ND: giảm 50,000VND")
    }

    @Test fun requiresAccountAndExplicitDebitDirection() {
        reject("GD: -50,000VND", "TK 0001 Số tiền GD: 50,000VND", "TK 0001 Thanh toán 50,000VND")
    }

    @Test fun rejectsMultipleDebitsOrConflictingDirections() {
        reject("TK 0001 GD: -50,000VND; GD: -20,000VND", "TK 0001 GD: -50,000VND; GD: +20,000VND",
            "TK 0001 giảm 50,000VND; ghi nợ 20,000VND", "TK 0001 GD: -50,000VND; ghi nợ 50,000VND")
    }

    @Test fun rejectsZeroOverflowAndUnsupportedMoneyFormats() {
        reject("TK 0001 GD: -0VND", "TK 0001 GD: -999999999999999999999VND",
            "TK 0001 GD: -50,00VND", "TK 0001 GD: -50.00VND", "TK 0001 GD: -1,000.000VND",
            "TK 0001 GD: -50USD", "TK 0001 GD: -50,000", "TK 0001 GD: -50,000VND.50")
    }

    @Test fun unknownPackagesAndCaseChangedPackageAreIgnored() {
        assertNull(WalletParser.parse("com.vcb", "TK 0001 GD: -50,000VND"))
        assertNull(WalletParser.parse("other.bank", "TK 0001 GD: -50,000VND"))
    }

    @Test fun ambiguousBalancesAreNotSelectedArbitrarily() {
        assertNull(parse("TK 0001 GD: -50,000VND; SD: 100,000VND; Số dư khả dụng: 90,000VND")!!.balance)
    }
}
