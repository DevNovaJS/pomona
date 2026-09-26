package com.pomona.variety.domain

import com.pomona.variety.model.VarietyUpsert
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * 품종 쓰기. JPA 가 아니라 `JdbcTemplate` 인 이유:
 *
 * Hibernate 에 `ON CONFLICT` 문법이 없다. `save()` 는 SELECT 후 INSERT 또는 UPDATE 라
 * 왕복이 두 배고 SELECT 와 INSERT 사이가 벌어진다. 게다가 우리는 **넣은 뒤 id 가 필요하다** —
 * 그 id 로 `wholesale_daily` 를 채우기 때문이다. `RETURNING` 은 INSERT 든 UPDATE 든 id 를
 * 한 번에 돌려준다. MySQL 에는 없는 기능이고, 이 프로젝트가 PostgreSQL 인 이유 중 하나다.
 */
@Repository
class VarietyUpsertRepository(private val jdbcTemplate: JdbcTemplate) {

    /** 넣거나 갱신하고 **어느 쪽이든 id 를 돌려준다.** */
    fun upsert(variety: VarietyUpsert): Long = jdbcTemplate.queryForObject(
        SQL, Long::class.java,
        variety.lclsfCd, variety.lclsfNm,
        variety.mclsfCd, variety.mclsfNm,
        variety.sclsfCd, variety.sclsfNm,
    )!!

    /** 하루치 품종을 한 번에. 자연키 → id 로 돌려줘 [정산] 행을 id 로 바꾸는 데 쓴다. */
    fun upsertAll(varieties: Collection<VarietyUpsert>): Map<Triple<String, String, String>, Long> =
        varieties.associate { Triple(it.lclsfCd, it.mclsfCd, it.sclsfCd) to upsert(it) }
}

private val SQL = """
    insert into variety_master (lclsf_cd, lclsf_nm, mclsf_cd, mclsf_nm, sclsf_cd, sclsf_nm)
    values (?, ?, ?, ?, ?, ?)
    on conflict (lclsf_cd, mclsf_cd, sclsf_cd) do update
       set lclsf_nm   = excluded.lclsf_nm,
           mclsf_nm   = excluded.mclsf_nm,
           sclsf_nm   = excluded.sclsf_nm,
           updated_at = now()
    returning id
""".trimIndent()
