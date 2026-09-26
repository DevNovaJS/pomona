package com.pomona.price.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * 공개 빌드의 기준 기간. 기준일은 DB 의 마지막 거래일이다 — 새벽 수집이 실패해도 있는 데이터 끝에 맞춰져
 * "이번 주" 가 비지 않는다. 12개월은 기준일이 속한 달을 포함해 거꾸로 12달(기준일 2026-09-22 → 2025-10 ~ 2026-09).
 */
data class BuildPeriod(val baseDate: LocalDate) {
    val from: YearMonth
        get() = to.minusMonths(11)

    val to: YearMonth
        get() = YearMonth.from(baseDate)
}
