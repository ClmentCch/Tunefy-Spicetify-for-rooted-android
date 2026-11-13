package app.tunefy.android.util

import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object Formatters {

    private val frenchLocale = Locale.FRENCH

    fun bytes(value: Long): String {
        if (value <= 0L) return "—"
        val units = arrayOf("o", "Ko", "Mo", "Go", "To")
        val exponent = (ln(value.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val size = value / 1024.0.pow(exponent.toDouble())
        return if (exponent == 0) "${value} o"
        else String.format(frenchLocale, "%.1f %s", size, units[exponent])
    }

    fun speed(bytesPerSecond: Long): String =
        if (bytesPerSecond <= 0L) "—" else "${bytes(bytesPerSecond)}/s"

    fun eta(seconds: Long?): String {
        if (seconds == null || seconds <= 0L) return "—"
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return when {
            minutes >= 60 -> "${minutes / 60} h ${minutes % 60} min"
            minutes > 0 -> "$minutes min ${remainingSeconds.toString().padStart(2, '0')} s"
            else -> "$remainingSeconds s"
        }
    }

    fun shortDate(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return "—"
        return try {
            val date = LocalDate.parse(isoDate)
            date.format(DateTimeFormatter.ofPattern("d MMM yyyy", frenchLocale))
        } catch (_: Exception) {
            isoDate
        }
    }

    fun timestamp(millis: Long): String {
        if (millis <= 0L) return "—"
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, frenchLocale)
            .format(java.util.Date(millis))
    }

    fun percent(progress: Float): String = "${(progress * 100).toInt().coerceIn(0, 100)} %"

    fun androidVersion(api: Int): String {
        val name = when {
            api >= 36 -> "Android 16"
            api >= 35 -> "Android 15"
            api >= 34 -> "Android 14"
            api >= 33 -> "Android 13"
            api >= 32 -> "Android 12L"
            api >= 31 -> "Android 12"
            api >= 30 -> "Android 11"
            api >= 29 -> "Android 10"
            else -> "Android $api"
        }
        return "$name (API $api)"
    }
}
