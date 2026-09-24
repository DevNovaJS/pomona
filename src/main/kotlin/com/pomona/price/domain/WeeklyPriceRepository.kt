package com.pomona.price.domain

import com.pomona.price.model.WeeklyPrice
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * 도매 집계에서 품종별 주 평균가를 뽑는다. 저장하지 않고 조회할 때마다 계산한다.
 *
 * 이번 주는 [end] 를 포함한 최근 7일, 작년 같은 주는 그 7일에서 364일(52주) 전이다.
 * 364일이라 요일이 맞는다. 달력 주(월~일)로 자르면 월요일에는 하루치로만 평균을 내게 되므로 7일 구간을 굴린다.
 */
@Repository
class WeeklyPriceRepository(private val jdbc: JdbcTemplate) {

    /** 이번 주에 거래가 있는 품종 전부. 메인과 품종 페이지가 같은 결과를 나눠 쓴다. */
    fun findAll(end: LocalDate): List<WeeklyPrice> =
        jdbc.query(ALL, { rs, _ ->
            WeeklyPrice(
                varietyId = rs.getLong("variety_id"),
                thisWeekPerKg = rs.getBigDecimal("this_week"),
                lastYearPerKg = rs.getBigDecimal("last_year"),
            )
        }, end)
}

/**
 * 두 7일 구간을 한 번에 읽고 `filter` 로 구간별 합계를 나눠 낸다.
 * 작년 구간에 거래가 없으면 `sum(...) filter` 가 null 이라 나눗셈 결과도 null 이 된다.
 * 기준일은 `w` 에 한 번만 바인딩하고 구간 경계는 SQL 안에서 날짜 - 정수로 계산한다.
 */
private const val ALL = """
    select d.variety_id,
           round(sum(d.tot_prc) filter (where d.trd_clcln_ymd > w.end_date - 7)
               / sum(d.tot_qty) filter (where d.trd_clcln_ymd > w.end_date - 7), 2) as this_week,
           round(sum(d.tot_prc) filter (where d.trd_clcln_ymd <= w.end_date - 364)
               / sum(d.tot_qty) filter (where d.trd_clcln_ymd <= w.end_date - 364), 2) as last_year
      from wholesale_daily d
     cross join (select ?::date as end_date) w
     where (d.trd_clcln_ymd > w.end_date - 7 and d.trd_clcln_ymd <= w.end_date)
        or (d.trd_clcln_ymd > w.end_date - 371 and d.trd_clcln_ymd <= w.end_date - 364)
     group by d.variety_id
    having sum(d.tot_qty) filter (where d.trd_clcln_ymd > w.end_date - 7) is not null
     order by d.variety_id
"""
