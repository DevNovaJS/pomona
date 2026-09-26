package com.pomona.review.domain

import com.pomona.price.domain.toGradeTotal
import com.pomona.price.domain.toMarketPrice
import com.pomona.price.model.MarketPrice
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * 리뷰에 붙이는 그날 도매 시세. 먹은 날 당일 또는 그 전 마지막 거래일의 그 품종 도매가다.
 *
 * [LOOKBACK_DAYS] 일 안에서만 찾는다. 주말·명절 연휴는 넘기되, 제철이 끝나 몇 달 전에 끊긴 값을
 * "그날 시세" 로 붙이지 않으려는 것이다. 그 안에 거래가 없으면 시세가 없다.
 */
@Repository
class ReviewMarketPriceRepository(private val jdbcTemplate: JdbcTemplate) {

    /** 리뷰 전부의 시세를 한 번에. 리뷰 id → 시세. 시세가 없는 리뷰는 들어가지 않는다. */
    fun findForReviews(): Map<Long, MarketPrice> =
        jdbcTemplate.query(FOR_REVIEWS, { rs, _ -> rs.getLong("review_id") to rs.toGradeTotal() })
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, grades) -> grades.toMarketPrice() }

    /** 품종 하나와 날짜 하나. 리뷰를 쓰는 중 미리보기와 작성·수정 응답에 쓴다. */
    fun find(varietyId: Long, eatenDate: LocalDate): MarketPrice? =
        jdbcTemplate.query(FOR_ONE, { rs, _ -> rs.toGradeTotal() }, varietyId, eatenDate)
            .ifEmpty { null }
            ?.toMarketPrice()
}

/** 먹은 날 포함 7일. 먹은 날이 월요일이면 그 전 화요일까지 본다. */
private const val LOOKBACK_DAYS = 7

/**
 * [source] 의 행마다 (id, variety_id, eaten_date) 를 읽어 그날 도매가를 등급별로 합친다.
 *
 * 조인 조건 안의 서브쿼리가 행마다 "먹은 날 이하 마지막 거래일" 하나를 찾는다. `ix_wholesale_variety_date` 를
 * 뒤에서부터 한 칸만 읽고 끝난다. 7일 안에 거래가 없으면 서브쿼리가 null 이라 조인되는 행이 없다.
 */
private fun marketPriceSql(source: String) = """
    select r.id as review_id, d.trd_clcln_ymd, d.grd_cd, d.grd_nm,
           sum(d.tot_prc) as tot_prc, sum(d.tot_qty) as tot_qty
      from $source r
      join wholesale_daily d
        on d.variety_id = r.variety_id
       and d.trd_clcln_ymd = (
               select max(w.trd_clcln_ymd)
                 from wholesale_daily w
                where w.variety_id = r.variety_id
                  and w.trd_clcln_ymd <= r.eaten_date
                  and w.trd_clcln_ymd > r.eaten_date - $LOOKBACK_DAYS
           )
     group by r.id, d.trd_clcln_ymd, d.grd_cd, d.grd_nm
     order by r.id, d.grd_cd
"""

private val FOR_REVIEWS = marketPriceSql(source = "review")

/** 리뷰 테이블 대신 (품종, 날짜) 한 쌍을 한 행짜리 표로 만들어 같은 쿼리를 쓴다. 바인딩은 (품종 id, 날짜). */
private val FOR_ONE = marketPriceSql(source = "(select 0 as id, ?::bigint as variety_id, ?::date as eaten_date)")
