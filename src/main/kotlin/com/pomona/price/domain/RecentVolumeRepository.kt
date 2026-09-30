package com.pomona.price.domain

import com.pomona.price.model.RecentVolume
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * 메인의 "평소보다 많이 나오는 과일"용 물량. 품종별로 [end] 를 포함한 최근 14일 물량과, [end] 까지 12개월 물량을 14일치로 줄인 평소 물량을 낸다.
 * 저장하지 않고 조회할 때마다 계산한다.
 */
@Repository
class RecentVolumeRepository(private val jdbcTemplate: JdbcTemplate) {

    /** 최근 14일에 거래가 있는 품종 전부. */
    fun findAll(end: LocalDate): List<RecentVolume> =
        jdbcTemplate.query(ALL, { rs, _ ->
            RecentVolume(
                varietyId = rs.getLong("variety_id"),
                recentQty = rs.getBigDecimal("recent_qty"),
                usualQty = rs.getBigDecimal("usual_qty"),
            )
        }, end)
}

private const val RECENT_DAYS = 14
private const val USUAL_DAYS = 365

/**
 * 12개월을 한 번 읽고 `filter` 로 최근 14일 합계를 따로 낸다. 평소 물량은 12개월 합계를 날짜 수 비율로 줄인 것이다.
 * 기준일은 `w` 에 한 번만 바인딩하고 구간 경계는 SQL 안에서 날짜 - 정수로 계산한다([WeeklyPriceRepository] 와 같다).
 * 최근 14일에 거래가 없으면 `sum(...) filter` 가 null 이라 `having` 에서 빠진다.
 */
private const val ALL = """
    select d.variety_id,
           sum(d.tot_qty) filter (where d.trd_clcln_ymd > w.end_date - $RECENT_DAYS) as recent_qty,
           round(sum(d.tot_qty) * $RECENT_DAYS / $USUAL_DAYS, 3)                      as usual_qty
      from wholesale_daily d
     cross join (select ?::date as end_date) w
     where d.trd_clcln_ymd > w.end_date - $USUAL_DAYS
       and d.trd_clcln_ymd <= w.end_date
     group by d.variety_id
    having sum(d.tot_qty) filter (where d.trd_clcln_ymd > w.end_date - $RECENT_DAYS) is not null
     order by d.variety_id
"""
