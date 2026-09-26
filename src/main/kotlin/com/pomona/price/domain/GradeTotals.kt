package com.pomona.price.domain

import com.pomona.price.model.GradePrice
import com.pomona.price.model.MarketPrice
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.ResultSet
import java.time.LocalDate

/**
 * 쿼리 한 행: 한 거래일 한 등급의 총액·물량 합. 오늘 도매가와 리뷰의 그날 도매 시세가 같이 쓴다.
 * 합산 대표가는 등급별 가격을 평균 내지 않고 이 합들을 다시 더해 구한다.
 */
internal class GradeTotal(
    val date: LocalDate,
    val grdCd: String,
    val grdNm: String,
    val totPrc: BigDecimal,
    val totQty: BigDecimal,
)

/** 컬럼 `trd_clcln_ymd, grd_cd, grd_nm, tot_prc, tot_qty` 를 읽는다. */
internal fun ResultSet.toGradeTotal() = GradeTotal(
    date = getObject("trd_clcln_ymd", LocalDate::class.java),
    grdCd = getString("grd_cd"),
    grdNm = getString("grd_nm"),
    totPrc = getBigDecimal("tot_prc"),
    totQty = getBigDecimal("tot_qty"),
)

/** 같은 거래일의 등급 합계들을 합산 대표가와 등급별 대표가로 묶는다. */
internal fun List<GradeTotal>.toMarketPrice() = MarketPrice(
    date = first().date,
    perKg = perKg(sumOf { it.totPrc }, sumOf { it.totQty }),
    grades = map { GradePrice(it.grdCd, it.grdNm, perKg(it.totPrc, it.totQty)) },
)

private fun perKg(totPrc: BigDecimal, totQty: BigDecimal): BigDecimal = totPrc.divide(totQty, 2, RoundingMode.HALF_UP)
