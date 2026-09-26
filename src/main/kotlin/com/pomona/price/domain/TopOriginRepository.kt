package com.pomona.price.domain

import com.pomona.price.model.ItemTopOrigins
import com.pomona.price.model.OriginVolume
import com.pomona.price.model.TopOrigins
import com.pomona.variety.model.OTHER_CODE
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate
import java.time.YearMonth

/**
 * 도매 집계에서 주요 산지를 뽑는다. 저장하지 않고 조회할 때마다 계산한다.
 * 월별 물량처럼 품종·품목 하나씩이 아니라 [from]~[to] 달 전부를 한 번에 준다.
 */
@Repository
class TopOriginRepository(private val jdbcTemplate: JdbcTemplate) {

    /** 품종 전부. 품종 id 순. */
    fun findAll(from: YearMonth, to: YearMonth): List<TopOrigins> =
        jdbcTemplate.query(BY_VARIETY, { rs, _ -> rs.getLong("variety_id") to rs.toRankedOrigin() }, from.firstDay(), to.nextFirstDay())
            .groupBy({ it.first }, { it.second })
            .map { (varietyId, origins) -> TopOrigins(varietyId, origins.total(), origins.byMonth()) }

    /** 품목 전부. 품목 코드 순. 기타 품목(중분류 `99`)은 뺀다. */
    fun findItems(from: YearMonth, to: YearMonth): List<ItemTopOrigins> =
        jdbcTemplate.query(BY_ITEM, { rs, _ -> (rs.getString("lclsf_cd") to rs.getString("mclsf_cd")) to rs.toRankedOrigin() },
            from.firstDay(), to.nextFirstDay())
            .groupBy({ it.first }, { it.second })
            .map { (item, origins) -> ItemTopOrigins(item.first, item.second, origins.total(), origins.byMonth()) }
}

/** 쿼리 한 행. [month] 가 null 이면 기간 전체 합계의 순위다. */
private class RankedOrigin(val month: YearMonth?, val origin: OriginVolume)

private fun ResultSet.toRankedOrigin() = RankedOrigin(
    month = getObject("month", LocalDate::class.java)?.let { YearMonth.from(it) },
    origin = OriginVolume(getString("plor_cd"), getString("plor_nm"), getBigDecimal("qty")),
)

private fun List<RankedOrigin>.total(): List<OriginVolume> = filter { it.month == null }.map { it.origin }

private fun List<RankedOrigin>.byMonth(): Map<YearMonth, List<OriginVolume>> =
    mapNotNull { ranked -> ranked.month?.let { it to ranked.origin } }.groupBy({ it.first }, { it.second })

private const val TOP = 5

/**
 * 주요 산지 쿼리의 공통 뼈대. 품종별과 품목별은 무엇으로 묶느냐만 다르다.
 *
 * - `base`: 조회 기간의 행에 달을 붙인다. [keySelect] 로 묶음 키를 뽑는다
 * - `summed`: `grouping sets` 로 (키, 산지) 기간 합계와 (키, 달, 산지) 달별 합계를 한 번에 낸다.
 *   기간 합계 쪽은 달로 묶지 않았으므로 `month` 가 null 로 나온다
 * - `ranked`: (키, 달) 마다 물량 순으로 번호를 매긴다. 기간 합계는 month 가 null 인 한 묶음으로 순위가 매겨진다
 */
private fun topOriginsSql(keySelect: String, keyNames: String, join: String, condition: String) = """
    with base as (
        select $keySelect, date_trunc('month', d.trd_clcln_ymd)::date as month, d.plor_cd, d.plor_nm, d.tot_qty
          from wholesale_daily d
          $join
         where d.trd_clcln_ymd >= ? and d.trd_clcln_ymd < ?
           $condition
    ),
    summed as (
        select $keyNames, month, plor_cd, max(plor_nm) as plor_nm, sum(tot_qty) as qty
          from base
         group by grouping sets (($keyNames, plor_cd), ($keyNames, month, plor_cd))
    ),
    ranked as (
        select *, row_number() over (partition by $keyNames, month order by qty desc, plor_cd) as rn
          from summed
    )
    select * from ranked where rn <= $TOP order by $keyNames, month nulls first, rn
"""

private val BY_VARIETY = topOriginsSql(
    keySelect = "d.variety_id", keyNames = "variety_id",
    join = "", condition = "",
)

private val BY_ITEM = topOriginsSql(
    keySelect = "v.lclsf_cd, v.mclsf_cd", keyNames = "lclsf_cd, mclsf_cd",
    join = "join variety_master v on v.id = d.variety_id", condition = "and v.mclsf_cd <> '$OTHER_CODE'",
)
