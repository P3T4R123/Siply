package com.playground.siply

import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private val hrvatskiLocale = Locale("hr", "HR")

fun formatCurrency(cents: Int): String {
    val formatter = NumberFormat.getCurrencyInstance(hrvatskiLocale).apply {
        currency = Currency.getInstance("EUR")
    }
    return formatter.format(cents / 100.0)
}

fun formatCsvEuro(cents: Int): String = DecimalFormat("0.00").format(cents / 100.0)

fun currentDayKey(
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = now.atZone(zoneId).toLocalDate().toString()

fun startOfDayMillis(
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long = now.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()

fun formatReceiptNumber(dayKey: String, sequence: Int): String =
    "${dayKey.replace("-", "")}-${sequence.toString().padStart(3, '0')}"

fun receiptSequenceFrom(receiptNumber: String): Int =
    receiptNumber.substringAfterLast('-', "0").toIntOrNull() ?: 0

fun formatTime(
    epochMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = Instant.ofEpochMilli(epochMs)
    .atZone(zoneId)
    .format(DateTimeFormatter.ofPattern("HH:mm", hrvatskiLocale))

fun formatDateTime(
    epochMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = Instant.ofEpochMilli(epochMs)
    .atZone(zoneId)
    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", hrvatskiLocale))

fun formatResetLabel(
    anchorMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    if (anchorMs <= 0L) {
        return "Od početka dana"
    }

    val anchorDate = Instant.ofEpochMilli(anchorMs).atZone(zoneId).toLocalDate()
    val today = Instant.now().atZone(zoneId).toLocalDate()

    return if (anchorDate == today) {
        "Od ${formatTime(anchorMs, zoneId)}"
    } else {
        "Od ${formatDateTime(anchorMs, zoneId)}"
    }
}

fun exportFileName(
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = "prodaja_${now.atZone(zoneId).toLocalDate()}.csv"

fun priceListFileName(
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = "cjenik_${now.atZone(zoneId).toLocalDate()}.csv"
