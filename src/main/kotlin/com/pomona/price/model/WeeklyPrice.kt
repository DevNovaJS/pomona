package com.pomona.price.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 품종 하나의 최근 7일 도매 평균가와 작년 같은 7일 평균가. 둘 다 kg당 원.
 *
 * 평균은 7일간 총액 합 ÷ 물량 합이다. 하루 가격을 다시 평균 내면 물량이 적은 날이 많은 날과 같은 무게를 갖는다.
 */
data class WeeklyPrice(
    val varietyId: Long,
    val thisWeekPerKg: BigDecimal,
    /** 작년 같은 7일에 거래가 없으면 null */
    val lastYearPerKg: BigDecimal?,
) {
    /** 작년 같은 주 대비 등락률(%), 소수 1자리. 작년 거래가 없으면 null */
    val changeRate: BigDecimal?
        get() = lastYearPerKg?.let { (thisWeekPerKg - it).multiply(HUNDRED).divide(it, 1, RoundingMode.HALF_UP) }
}

private val HUNDRED = BigDecimal(100)
