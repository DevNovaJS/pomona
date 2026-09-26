package com.pomona.price.model

import java.math.BigDecimal
import java.time.LocalDate

/** 한 품종의 어느 거래일 도매가. 리뷰 상세의 "그날 도매 시세". */
data class MarketPrice(
    /** 거래일. 먹은 날이 경매가 없는 날이면 그 전 마지막 거래일이다 */
    val date: LocalDate,
    /** 등급을 합친 대표가. 그날 총액 합 ÷ 물량 합, kg당 원 */
    val perKg: BigDecimal,
    /** 등급별 대표가. 등급 코드 순 */
    val grades: List<GradePrice>,
)
