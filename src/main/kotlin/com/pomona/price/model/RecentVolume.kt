package com.pomona.price.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 품종 하나의 최근 14일 물량과 평소 14일 물량. 둘 다 kg, 국산·수입 합계.
 *
 * 평소는 최근 12개월 물량을 14일치로 줄인 값이다(12개월 합 × 14 ÷ 365). 최근 물량이 평소의 몇 배인지([ratio])로
 * 메인의 "평소보다 많이 나오는 과일"을 고른다 — 물량이 작은 품종도 자기 평소와 비교하므로 잡히고,
 * 1년 내내 고르게 나오는 품종은 1배 안팎이라 빠진다. 몇 배부터 싣는지는 프론트가 정한다(지금 2배).
 */
data class RecentVolume(
    val varietyId: Long,
    val recentQty: BigDecimal,
    val usualQty: BigDecimal,
) {
    /** 최근 14일이 평소 14일의 몇 배인지, 소수 1자리 */
    val ratio: BigDecimal
        get() = recentQty.divide(usualQty, 1, RoundingMode.HALF_UP)
}
