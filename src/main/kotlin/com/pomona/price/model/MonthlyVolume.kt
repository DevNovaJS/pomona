package com.pomona.price.model

import java.math.BigDecimal
import java.time.YearMonth

/**
 * 원산지 코드가 이 값으로 시작하면 수입이다. `800` + 국가코드 (`800CL` 칠레, `800US` 미국).
 * 국내 산지는 시·군 코드 6자리다. 품종명의 '(수입)' 으로 판별하면 안 된다 —
 * 파인애플 골드·오렌지 네블처럼 이름에 없는 수입 품종이 있다.
 */
const val IMPORT_ORIGIN_PREFIX = "800"

enum class Origin { DOMESTIC, IMPORT }

/**
 * 품종 하나의 한 달 물량을 국산·수입으로 나눈 것.
 *
 * 품종 페이지의 12개월 막대와 제철 캘린더가 같은 이 집계를 쓴다.
 * 제철 캘린더는 국산만 제철로 표시하고, 수입은 따로 목록을 세운다.
 */
data class MonthlyVolume(
    val varietyId: Long,
    val month: YearMonth,
    val origin: Origin,
    /** 그 달 거래 물량 합계(kg) */
    val qty: BigDecimal,
)

/**
 * 품목 하나의 한 달 물량을 국산·수입으로 나눈 것. 품목 페이지의 12개월 막대(국산·수입 쌓기)에 쓴다.
 * 품목 안의 기타·소량 품종까지 전부 합친다.
 */
data class ItemMonthlyVolume(
    val lclsfCd: String,
    val mclsfCd: String,
    val month: YearMonth,
    val origin: Origin,
    /** 그 달 거래 물량 합계(kg) */
    val qty: BigDecimal,
)
