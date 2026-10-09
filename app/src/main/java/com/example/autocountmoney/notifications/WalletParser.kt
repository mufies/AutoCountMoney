package com.example.autocountmoney.notifications

import java.text.Normalizer
import java.util.Locale

data class ParsedExpense(val provider: String, val amount: Long, val balance: Long?)

/** Wallet rules and dispatch to bank balance-change rules. See docs/bank-notifications.md. */
object WalletParser {
    val providers = mapOf(
        "com.mservice.momotransfer" to "MoMo",
        "vn.com.vng.zalopay" to "ZaloPay",
        "com.mbmobile" to "MB Bank",
        "com.tpb.mb.gprsandroid" to "TPBank",
        "com.VCB" to "Vietcombank"
    )
    private val money = Regex("(?<![\\d.,])([0-9]+(?:[.,][0-9]{3})*)\\s*(?:vnd|d|₫|dong)(?![a-z])")
    private val debit = Regex("(?:da\\s+)?(?:thanh toan|chuyen tien|chuyen khoan)(?:\\s+thanh cong)?(?:\\s+(?:voi\\s+)?so tien)?\\s*[:!,-]?\\s*")
    private val balance = Regex("so du(?: vi)?(?: con lai)?\\s*[:=-]?\\s*")

    fun parse(packageName: String, content: String): ParsedExpense? {
        val provider = providers[packageName] ?: return null
        if (packageName in BankParser.packages) return BankParser.parse(provider, content)
        val text = Normalizer.normalize(content.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace('đ', 'd')
        if (!text.contains("thanh cong") && !text.contains("da thanh toan") && !text.contains("da chuyen")) return null
        if (listOf("that bai", "khong thanh cong", "dang xu ly", "nhan tien", "nhan duoc", "hoan tien", "nap tien", "nap vi", "rut tien", "otp", "ma xac thuc").any(text::contains)) return null
        val debitMatches = debit.findAll(text).mapNotNull { match ->
            money.find(text, match.range.last + 1)?.takeIf {
                it.range.first == match.range.last + 1
            }
        }.toList()
        // Ambiguous or missing amount: never guess from cashback or balance.
        if (debitMatches.size != 1) return null
        val amount = toLong(debitMatches.single().groupValues[1]) ?: return null
        if (amount <= 0) return null
        val balanceAmount = balance.find(text)?.let { label ->
            money.find(text, label.range.last + 1)?.takeIf {
                it.range.first == label.range.last + 1
            }?.groupValues?.get(1)?.let(::toLong)
        }
        return ParsedExpense(provider, amount, balanceAmount)
    }

    private fun toLong(value: String) = value.replace(",", "").replace(".", "").toLongOrNull()
}
