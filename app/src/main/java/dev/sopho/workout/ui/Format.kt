package dev.sopho.workout.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** "2026-07-11" -> "7月11日 周六" */
fun dateLabel(iso: String): String {
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    return "${date.monthValue}月${date.dayOfMonth}日 ${weekdayCn(date)}"
}

/** "2026-07-11" -> "07-11 周六" */
fun dateLabelShort(iso: String): String {
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    return "%02d-%02d %s".format(date.monthValue, date.dayOfMonth, weekdayCn(date))
}

fun weekdayCn(date: LocalDate): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[date.dayOfWeek.value - 1]

/** "今天" / "昨天" / "" */
fun relativeLabel(iso: String, today: LocalDate): String = when (iso) {
    today.toString() -> "今天"
    today.minusDays(1).toString() -> "昨天"
    else -> ""
}

/** 0.63 -> "63%" */
fun percent(ratio: Float): String = "${Math.round(ratio * 100)}%"

/* ------------------------------------------------------------ timestamps */

/** epoch millis -> "08:12" in the device's current time zone */
fun timeLabel(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(clockFormat)

/** "08:12 – 08:36" */
fun timeRangeLabel(from: Long, to: Long): String = "${timeLabel(from)} – ${timeLabel(to)}"

/** duration to a human string; only ever called with millis that are > 0 */
fun durationLabel(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> "不到 1 分钟"
        minutes < 60 -> "$minutes 分钟"
        else -> "${minutes / 60} 小时 ${minutes % 60} 分钟"
    }
}
