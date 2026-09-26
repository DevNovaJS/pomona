package com.pomona.variety.domain

import com.pomona.variety.model.Item
import com.pomona.variety.model.OTHER_CODE
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate

/** 품목 목록. 품목 페이지는 품종 페이지와 달리 거래량 조건 없이 [end] 까지 최근 12개월에 거래가 있는 품목 전부를 만든다. */
@Repository
class ItemRepository(private val jdbc: JdbcTemplate) {

    /** 기타 품목(중분류 `99`)은 뺀다. 품목 코드 순. */
    fun findAll(end: LocalDate): List<Item> =
        jdbc.query(SQL, { rs, _ ->
            Item(
                lclsfCd = rs.getString("lclsf_cd"), lclsfNm = rs.getString("lclsf_nm"),
                mclsfCd = rs.getString("mclsf_cd"), mclsfNm = rs.getString("mclsf_nm"),
            )
        }, end.minusYears(1), end)
}

/**
 * 품목 이름은 품종 마스터의 각 품종 행에 같은 값이 들어 있어 `max` 로 하나만 뽑는다.
 * 바인딩은 (1년 전 날짜, 기준일) 이고 1년 전 날짜는 빼고 센다.
 */
private const val SQL = """
    select v.lclsf_cd, max(v.lclsf_nm) as lclsf_nm, v.mclsf_cd, max(v.mclsf_nm) as mclsf_nm
      from variety_master v
     where v.mclsf_cd <> '$OTHER_CODE'
       and exists (
           select 1 from wholesale_daily d
            where d.variety_id = v.id
              and d.trd_clcln_ymd > ? and d.trd_clcln_ymd <= ?
       )
     group by v.lclsf_cd, v.mclsf_cd
     order by v.lclsf_cd, v.mclsf_cd
"""
