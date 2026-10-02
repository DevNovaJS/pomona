package com.pomona.price.domain

import com.pomona.price.model.DailyPrice
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * 품종별 최근 30일 거래일마다의 도매가. 저장하지 않고 조회할 때마다 계산한다.
 *
 * 30일은 [end] 를 포함해 센다. 품종마다 마지막 거래일이 아니라 공통 기준일에서 세므로,
 * 30일 안에 거래가 없는 품종은 결과에 없다.
 */
@Repository
class DailyPriceRepository(private val jdbcTemplate: JdbcTemplate) {

    /** 품종 id, 날짜 순. */
    fun findAll(end: LocalDate): List<DailyPrice> =
        jdbcTemplate.query(SQL, { rs, _ ->
            DailyPrice(
                varietyId = rs.getLong("variety_id"),
                date = rs.getObject("trd_clcln_ymd", LocalDate::class.java),
                perKg = rs.getBigDecimal("per_kg"),
                qty = rs.getBigDecimal("qty"),
            )
        }, end.minusDays(DAYS), end)
}

private const val DAYS = 30L

/** 바인딩은 (30일 전 날짜, 기준일) 이고 30일 전 날짜는 빼고 센다. 등급·산지·거래 방식을 전부 합친다. */
private const val SQL = """
    select variety_id, trd_clcln_ymd,
           round(sum(tot_prc) / sum(tot_qty), 2) as per_kg,
           sum(tot_qty) as qty
      from wholesale_daily
     where trd_clcln_ymd > ? and trd_clcln_ymd <= ?
     group by variety_id, trd_clcln_ymd
     order by variety_id, trd_clcln_ymd
"""
