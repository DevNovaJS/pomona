package com.pomona.price.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * 품종 하나의 마지막 거래일 도매가. 메인과 품종 페이지의 "오늘 도매가".
 * 품종마다 마지막 거래일이 달라 '오늘' 이 아니므로 [date] 를 같이 보여준다.
 */
data class LatestPrice(
    val varietyId: Long,
    val date: LocalDate,
    /** 등급을 합친 대표가. 그날 총액 합 ÷ 물량 합, kg당 원 */
    val perKg: BigDecimal,
    /** 등급별 대표가. 등급 코드 순(특 → 상 → 중 → … → 등외). 실측 품종의 80% 는 마지막 거래일에 등급이 하나뿐이다 */
    val grades: List<GradePrice>,
)

data class GradePrice(
    val grdCd: String,
    val grdNm: String,
    /** 그 등급의 총액 합 ÷ 물량 합, kg당 원 */
    val perKg: BigDecimal,
)
