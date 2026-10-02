package com.pomona.price.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * 품종 하나의 거래일 하루 도매가. 품종 페이지의 최근 30일 추이 꺾은선 한 점.
 * 거래가 없는 날은 행이 없다.
 */
data class DailyPrice(
    val varietyId: Long,
    val date: LocalDate,
    /** 등급을 합친 대표가. 그날 총액 합 ÷ 물량 합, kg당 원 */
    val perKg: BigDecimal,
    /** 그날 물량 합, kg */
    val qty: BigDecimal,
)
