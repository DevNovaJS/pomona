package com.pomona.price.domain

import com.pomona.price.model.IMPORT_ORIGIN_PREFIX
import com.pomona.price.model.ItemMonthlyVolume
import com.pomona.price.model.MonthlyVolume
import com.pomona.price.model.Origin
import com.pomona.variety.model.OTHER_CODE
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate
import java.time.YearMonth

/**
 * 도매 집계에서 월별 물량을 뽑는다. 저장하지 않고 조회할 때마다 계산한다.
 *
 * 공개면 빌드가 하루 한 번 부르므로 품종·품목 하나씩이 아니라 **[from]~[to] 달 전부를 한 번에** 준다.
 * 하나씩 부르면 빌드 한 번에 품종 279번 · 품목 수십 번이 된다.
 */
@Repository
class MonthlyVolumeRepository(private val jdbc: JdbcTemplate) {

    /**
     * 품종 전부의 월별 물량을 달·원산지별로. 달 순, 달 안에서는 물량이 많은 순.
     * 품종 페이지 막대는 품종으로 묶어 쓰고, 제철 캘린더는 이 순서 그대로 달로 묶어 쓴다.
     */
    fun findAll(from: YearMonth, to: YearMonth): List<MonthlyVolume> =
        jdbc.query(BY_VARIETY, { rs, _ ->
            MonthlyVolume(
                varietyId = rs.getLong("variety_id"),
                month = rs.getMonth(),
                origin = rs.getOrigin(),
                qty = rs.getBigDecimal("qty"),
            )
        }, from.firstDay(), to.nextFirstDay())

    /** 품목 전부의 월별 물량을 달·원산지별로. 품목 코드 순, 달 순. 기타 품목(중분류 `99`)은 뺀다. */
    fun findItems(from: YearMonth, to: YearMonth): List<ItemMonthlyVolume> =
        jdbc.query(BY_ITEM, { rs, _ ->
            ItemMonthlyVolume(
                lclsfCd = rs.getString("lclsf_cd"),
                mclsfCd = rs.getString("mclsf_cd"),
                month = rs.getMonth(),
                origin = rs.getOrigin(),
                qty = rs.getBigDecimal("qty"),
            )
        }, from.firstDay(), to.nextFirstDay())

    /**
     * 달마다 시장이 거래한 날 수. 이번 달처럼 덜 찬 달을 화면이 알아보게 하려고 준다.
     * 달마다 거래일이 25~27일로 달라 합계만으로는 달끼리 비교가 어긋나는 것도 이걸로 보정할 수 있다.
     * 거래가 하나도 없는 달은 들어가지 않는다.
     */
    fun countTradingDays(from: YearMonth, to: YearMonth): Map<YearMonth, Int> =
        jdbc.query(TRADING_DAYS, { rs, _ -> rs.getMonth() to rs.getInt("days") }, from.firstDay(), to.nextFirstDay())
            .toMap()
}

private fun ResultSet.getMonth(): YearMonth = YearMonth.from(getObject("month", LocalDate::class.java))

private fun ResultSet.getOrigin(): Origin = Origin.valueOf(getString("origin"))

/** 원산지 구분식. 두 쿼리가 같은 규칙을 쓰도록 한 곳에서 만든다. */
private const val ORIGIN =
    "case when d.plor_cd like '$IMPORT_ORIGIN_PREFIX%' then 'IMPORT' else 'DOMESTIC' end"

private const val MONTH = "date_trunc('month', d.trd_clcln_ymd)::date"

private const val BY_VARIETY = """
    select d.variety_id, $MONTH as month, $ORIGIN as origin, sum(d.tot_qty) as qty
      from wholesale_daily d
     where d.trd_clcln_ymd >= ? and d.trd_clcln_ymd < ?
     group by 1, 2, 3
     order by month, qty desc, d.variety_id, origin
"""

private const val BY_ITEM = """
    select v.lclsf_cd, v.mclsf_cd, $MONTH as month, $ORIGIN as origin, sum(d.tot_qty) as qty
      from wholesale_daily d
      join variety_master v on v.id = d.variety_id
     where d.trd_clcln_ymd >= ? and d.trd_clcln_ymd < ?
       and v.mclsf_cd <> '$OTHER_CODE'
     group by 1, 2, 3, 4
     order by 1, 2, 3, 4
"""

private const val TRADING_DAYS = """
    select $MONTH as month, count(distinct d.trd_clcln_ymd) as days
      from wholesale_daily d
     where d.trd_clcln_ymd >= ? and d.trd_clcln_ymd < ?
     group by 1
"""
