package com.arete.app.data.model

import java.time.LocalDate
import java.time.Period

/** Calendar months, not an approximation of 30 days per month. */
data class AnimalAge(val months: Long, val days: Int) {
    val label: String get() = "$months ${if (months == 1L) "mes" else "meses"} $days ${if (days == 1) "día" else "días"}"
}

fun animalAge(birthDate: LocalDate?, onDate: LocalDate): AnimalAge? {
    if (birthDate == null || birthDate.isAfter(onDate)) return null
    val period = Period.between(birthDate, onDate)
    return AnimalAge(period.toTotalMonths(), period.days)
}

data class AnimalDetail(
    val animal: AnimalRecord,
    val ranchName: String,
    val motherLabel: String,
    val fatherLabel: String,
    val canEdit: Boolean,
    val isDemo: Boolean,
    val birthWeightKg: Double? = null,
    val circumferenceDate: String? = null,
    val entryReasonLabel: String = animal.form.reason?.label.orEmpty()
)

/** Prefer a measured weight; birth weight is only a fallback. */
val AnimalDetail.displayWeightKg: Double? get() = animal.lastWeightKg?.takeIf { it.isFinite() && it > 0 }
    ?: birthWeightKg?.takeIf { it.isFinite() && it > 0 }
val AnimalDetail.usesBirthWeight: Boolean get() = animal.lastWeightKg?.takeIf { it.isFinite() && it > 0 } == null && displayWeightKg != null
