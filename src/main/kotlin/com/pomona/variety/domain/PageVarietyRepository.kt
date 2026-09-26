package com.pomona.variety.domain

import com.pomona.variety.model.OTHER_CODE
import com.pomona.variety.model.PageVariety
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * 품종 페이지를 만들 품종을 고른다. 조건은 기타가 아니고, [end] 까지 최근 12개월에
 * 거래일 [MIN_TRADE_DAYS] 일 이상 · 물량 [MIN_QTY_KG] kg 이상.
 *
 * 거래가 적은 품종은 오늘 도매가가 몇 달 전 값이고 막대가 비어 빈약한 페이지가 된다.
 * 12개월로 잡는 건 감홍처럼 제철이 짧은 품종도 한 철은 들어오게 하려는 것이다.
 */
@Repository
class PageVarietyRepository(private val jdbcTemplate: JdbcTemplate) {

    /** 품목 코드 순, 품목 안에서는 12개월 물량이 많은 순. 품목 페이지의 품종 목록이 이 순서를 그대로 쓴다. */
    fun findAll(end: LocalDate): List<PageVariety> =
        jdbcTemplate.query(SQL, { rs, _ ->
            PageVariety(
                id = rs.getLong("id"),
                lclsfCd = rs.getString("lclsf_cd"), lclsfNm = rs.getString("lclsf_nm"),
                mclsfCd = rs.getString("mclsf_cd"), mclsfNm = rs.getString("mclsf_nm"),
                sclsfCd = rs.getString("sclsf_cd"), sclsfNm = rs.getString("sclsf_nm"),
            )
        }, end.minusYears(1), end)
}

private const val MIN_TRADE_DAYS = 10
private const val MIN_QTY_KG = 1_000

/**
 * `group by v.id` 만 두고 `v` 의 다른 컬럼을 select 한다. id 가 PK 라 나머지 컬럼이 id 에 딸려 있다는 걸
 * PostgreSQL 이 알아서 허용한다. 바인딩은 (1년 전 날짜, 기준일) 이고 1년 전 날짜는 빼고 센다.
 */
private const val SQL = """
    select v.id, v.lclsf_cd, v.lclsf_nm, v.mclsf_cd, v.mclsf_nm, v.sclsf_cd, v.sclsf_nm
      from variety_master v
      join wholesale_daily d on d.variety_id = v.id
     where d.trd_clcln_ymd > ?
       and d.trd_clcln_ymd <= ?
       and v.mclsf_cd <> '$OTHER_CODE'
       and v.sclsf_cd <> '$OTHER_CODE'
     group by v.id
    having count(distinct d.trd_clcln_ymd) >= $MIN_TRADE_DAYS
       and sum(d.tot_qty) >= $MIN_QTY_KG
     order by v.lclsf_cd, v.mclsf_cd, sum(d.tot_qty) desc, v.id
"""
