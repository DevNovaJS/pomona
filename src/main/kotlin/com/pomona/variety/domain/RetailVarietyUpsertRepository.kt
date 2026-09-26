package com.pomona.variety.domain

import com.pomona.variety.model.RetailVarietyUpsert
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/** 소매 품종 쓰기. `JdbcTemplate` 인 이유는 [VarietyUpsertRepository] 와 같다 — `ON CONFLICT` 가 필요하다. */
@Repository
class RetailVarietyUpsertRepository(private val jdbcTemplate: JdbcTemplate) {

    /** 없으면 넣고 있으면 이름만 갱신한다. 한 번 수집에 품종이 1~3개라 한 건씩 보낸다. */
    fun upsertAll(varieties: Collection<RetailVarietyUpsert>) {
        varieties.forEach {
            jdbcTemplate.update(SQL, it.ctgryCd, it.ctgryNm, it.itemCd, it.itemNm, it.vrtyCd, it.vrtyNm)
        }
    }
}

private const val SQL = """
    insert into retail_variety (ctgry_cd, ctgry_nm, item_cd, item_nm, vrty_cd, vrty_nm)
    values (?, ?, ?, ?, ?, ?)
    on conflict (ctgry_cd, item_cd, vrty_cd) do update
       set ctgry_nm   = excluded.ctgry_nm,
           item_nm    = excluded.item_nm,
           vrty_nm    = excluded.vrty_nm,
           updated_at = now()
"""
