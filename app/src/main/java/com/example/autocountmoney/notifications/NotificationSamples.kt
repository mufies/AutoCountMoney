package com.example.autocountmoney.notifications

/** Synthetic grammar examples for demos/tests. These are not captures from bank apps. */
object NotificationSamples {
    fun expense(provider: String, withBalance: Boolean): String {
        val text = when (provider) {
            "MB Bank" -> "TK 000000000001 GD: -50,000VND"
            "TPBank" -> "TK 000000000002 giảm 50.000 VND"
            "Vietcombank" -> "TK 000000000003 GD: -50,000 VND"
            else -> "Thanh toán thành công 50.000đ"
        }
        return text + if (withBalance) {
            if (provider in listOf("MB Bank", "TPBank", "Vietcombank")) "; Số dư: 1.200.000 VND"
            else ". Số dư ví: 1.200.000đ"
        } else ""
    }
}
