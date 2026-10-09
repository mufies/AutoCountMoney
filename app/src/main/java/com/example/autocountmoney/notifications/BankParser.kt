package com.example.autocountmoney.notifications

import java.text.Normalizer
import java.util.Locale

/** Explicit VND debits only; fixtures are synthetic, not verified live bank push messages. */
internal object BankParser {
    val packages = setOf("com.mbmobile", "com.tpb.mb.gprsandroid", "com.VCB")
    private const val number = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,3}(?:\\.[0-9]{3})+|[0-9]+)"
    private const val currency = "(?:vnd|vnđ|d|₫|dong)(?![a-z0-9])"
    private const val value = "($number)\\s*$currency(?![.,][0-9])"
    private val memo = Regex("(?:\\b(?:nd|noi dung|dien giai|description|content)\\s*[:=])")
    private val account = Regex("\\b(?:tk|tai khoan|account)(?:\\s+(?:so|thanh toan))?\\s*[:=]?\\s*[0-9*x]{3,}")
    private val signed = Regex("(?<![a-z0-9.,])([+-])\\s*$value")
    private val unsignedDebit = Regex("\\b(?:so tien\\s*(?:ghi no|trich no)|ghi no|trich no|giam(?:\\s+(?:so tien|so du))?)\\s*[:=]?\\s*$value")
    private val balance = Regex("\\b(?:so du(?:\\s+(?:tk|tai khoan|kha dung|con lai))?|sd(?:\\s+(?:tk|kha dung))?|balance|available balance)\\s*[:=]?\\s*$value")
    private val rejection = Regex("\\b(?:otp|ma (?:xac thuc|bao mat)|that bai|khong thanh cong|dang xu ly|cho xu ly|pending|failed|unsuccessful|khuyen mai|uu dai|quang cao)\\b")
    private val nonExpense = Regex("\\b(?:rut tien|rut tm|atm|nap vi|nap tien|chuyen (?:tien )?noi bo|chuyen giua|tiet kiem|hoan tien|refund|reversal|ghi co|tang so du)\\b")

    fun parse(provider: String, content: String): ParsedExpense? {
        val text = Normalizer.normalize(content.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace('đ', 'd')
            .replace('−', '-').replace('\u00a0', ' ')
        // Free-form transfer descriptions must never supply amounts, balances or debit keywords.
        val header = memo.find(text)?.let { text.substring(0, it.range.first) } ?: text
        if (!account.containsMatchIn(header) || rejection.containsMatchIn(header)) return null
        if (nonExpense.containsMatchIn(text)) return null
        val changes = signed.findAll(header).toList()
        val debits = unsignedDebit.findAll(header).toList()
        // Multiple changes (including mixed credit/debit), or conflicting labels: do not guess.
        val amountText = when {
            changes.size == 1 && debits.isEmpty() && changes.single().groupValues[1] == "-" -> {
                val change = changes.single()
                val prefix = header.substring(0, change.range.first)
                // A negative balance/fee is not a transaction amount.
                if (!Regex("(?:\\b(?:gd|giao dich|so tien(?: gd| giao dich)?|phat sinh)\\s*[:=]?\\s*|" +
                        "\\b(?:tk|tai khoan|account)(?:\\s+so)?\\s*[:=]?\\s*[0-9*x]{3,}\\s*(?:[:=]|,)?\\s*)$")
                        .containsMatchIn(prefix)) return null
                change.groupValues[2]
            }
            changes.isEmpty() && debits.size == 1 -> debits.single().groupValues[1]
            else -> return null
        }
        val amount = money(amountText)?.takeIf { it > 0 } ?: return null
        val balances = balance.findAll(header).toList()
        val remaining = balances.singleOrNull()?.groupValues?.get(1)?.let(::money)
        return ParsedExpense(provider, amount, remaining)
    }

    private fun money(value: String): Long? = value.replace(",", "").replace(".", "").toLongOrNull()
}
