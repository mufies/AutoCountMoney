package com.example.autocountmoney.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun money(amount: Long): String = "${NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount)} ₫"
fun transactionTime(timestamp: Long): String = SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.forLanguageTag("vi-VN")).format(Date(timestamp))
