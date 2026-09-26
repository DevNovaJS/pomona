package com.pomona.price.domain

import com.pomona.price.model.LatestPrice
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * 품종별 마지막 거래일의 도매가. 저장하지 않고 조회할 때마다 계산한다.
 *
 * 마지막 거래일은 [end] 까지 최근 12개월 안에서 찾는다. 품종 페이지 조건과 같은 기간이라
 * 페이지가 있는 품종은 반드시 값이 있다.
 */
@Repository
class LatestPriceRepository(private val jdbcTemplate: JdbcTemplate) {

    /** 12개월 안에 거래가 있는 품종 전부. 품종 id 순. */
    fun findAll(end: LocalDate): List<LatestPrice> =
        jdbcTemplate.query(SQL, { rs, _ -> rs.getLong("variety_id") to rs.toGradeTotal() }, end.minusYears(1), end)
            .groupBy({ it.first }, { it.second })
            .map { (varietyId, grades) ->
                val marketPrice = grades.toMarketPrice()
                LatestPrice(varietyId, marketPrice.date, marketPrice.perKg, marketPrice.grades)
            }
}

/**
 * `last` 에서 품종별 마지막 거래일을 찾고, 그날 행만 다시 읽어 등급별로 합친다.
 * 바인딩은 (1년 전 날짜, 기준일) 이고 1년 전 날짜는 빼고 센다.
 */
private const val SQL = """
    with last as (
        select variety_id, max(trd_clcln_ymd) as trd_date
          from wholesale_daily
         where trd_clcln_ymd > ? and trd_clcln_ymd <= ?
         group by variety_id
    )
    select d.variety_id, d.trd_clcln_ymd, d.grd_cd, d.grd_nm,
           sum(d.tot_prc) as tot_prc, sum(d.tot_qty) as tot_qty
      from wholesale_daily d
      join last l on l.variety_id = d.variety_id and l.trd_date = d.trd_clcln_ymd
     group by d.variety_id, d.trd_clcln_ymd, d.grd_cd, d.grd_nm
     order by d.variety_id, d.grd_cd
"""
