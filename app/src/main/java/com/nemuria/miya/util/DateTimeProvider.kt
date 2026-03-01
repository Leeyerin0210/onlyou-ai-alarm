package com.nemuria.miya.util

import java.time.LocalDate
import java.time.LocalDateTime

interface DateTimeProvider {
    fun nowLDT(): LocalDateTime
    fun nowLocalDate(): LocalDate
}

class DefaultDateTimeProvider : DateTimeProvider {
    override fun nowLDT(): LocalDateTime = LocalDateTime.now()
    override fun nowLocalDate(): LocalDate = LocalDate.now()
}
