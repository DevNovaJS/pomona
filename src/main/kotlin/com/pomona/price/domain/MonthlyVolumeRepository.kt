package com.pomona.price.domain

import com.pomona.price.model.IMPORT_ORIGIN_PREFIX
import com.pomona.price.model.MonthlyVolume
import com.pomona.price.model.Origin
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate
import java.time.YearMonth

/**
 * 도매 집계에서 월별 물량을 뽑는다. 저장하지 않고 조회할 때마다 계산한다.
 *
 * 6개월치(11만 행)에서 잰 값: 품종 하나의 월별 물량 1.5ms, 한 달의 품종별 물량 6ms.
 * 공개면은 하루 한 번 빌드할 때만 부르므로 집계 테이블이나 머티리얼라이즈드 뷰가 필요 없다.
 */
@Repository
class MonthlyVolumeRepository(private val jdbc: JdbcTemplate) {

    /** 품종 페이지의 막대. [from]~[to] 달의 물량을 달·원산지별로. `ix_wholesale_variety_date` 를 탄다. */
    fun findByVariety(varietyId: Long, from: YearMonth, to: YearMonth): List<MonthlyVolume> =
        jdbc.query(BY_VARIETY, { rs, _ -> rs.toMonthlyVolume() }, from.firstDay(), to.nextFirstDay(), varietyId)

    /** 제철 캘린더. [month] 한 달의 (품종, 원산지)별 물량을 많은 순으로. */
    fun findByMonth(month: YearMonth): List<MonthlyVolume> =
        jdbc.query(BY_MONTH, { rs, _ -> rs.toMonthlyVolume() }, month.firstDay(), month.nextFirstDay())

    /**
     * [month] 에 시장이 거래한 날 수. 이번 달처럼 덜 찬 달을 화면이 알아보게 하려고 준다.
     * 달마다 거래일이 25~27일로 달라 합계만으로는 달끼리 비교가 어긋나는 것도 이걸로 보정할 수 있다.
     */
    fun countTradingDays(month: YearMonth): Int =
        jdbc.queryForObject<Int>(TRADING_DAYS, month.firstDay(), month.nextFirstDay())!!
}

private fun YearMonth.firstDay(): LocalDate = atDay(1)

/** 끝을 "다음 달 1일 미만" 으로 잡는다. 월말 날짜 계산(28~31일)을 신경 쓸 필요가 없다. */
private fun YearMonth.nextFirstDay(): LocalDate = plusMonths(1).atDay(1)

private fun ResultSet.toMonthlyVolume() = MonthlyVolume(
    varietyId = getLong("variety_id"),
    month = YearMonth.from(getObject("month", LocalDate::class.java)),
    origin = Origin.valueOf(getString("origin")),
    qty = getBigDecimal("qty"),
)

/** 원산지 구분식. 두 쿼리가 같은 규칙을 쓰도록 한 곳에서 만든다. */
private const val ORIGIN =
    "case when plor_cd like '$IMPORT_ORIGIN_PREFIX%' then 'IMPORT' else 'DOMESTIC' end"

/**
 * 월별 물량 쿼리의 공통 뼈대. 두 쿼리는 품종 조건이 있느냐와 정렬 순서만 다르다.
 * 날짜 범위를 앞에 두고 [condition] 을 뒤에 붙이므로 바인딩도 (시작일, 끝일, 추가 조건) 순서다.
 */
private fun monthlyVolumeSql(condition: String, orderBy: String) = """
    select variety_id,
           date_trunc('month', trd_clcln_ymd)::date as month,
           $ORIGIN as origin,
           sum(tot_qty) as qty
      from wholesale_daily
     where trd_clcln_ymd >= ? and trd_clcln_ymd < ?
       $condition
     group by 1, 2, 3
     order by $orderBy
"""

private val BY_VARIETY = monthlyVolumeSql(condition = "and variety_id = ?", orderBy = "month, origin")

private val BY_MONTH = monthlyVolumeSql(condition = "", orderBy = "qty desc, variety_id, origin")

private const val TRADING_DAYS = """
    select count(distinct trd_clcln_ymd)
      from wholesale_daily
     where trd_clcln_ymd >= ? and trd_clcln_ymd < ?
"""
