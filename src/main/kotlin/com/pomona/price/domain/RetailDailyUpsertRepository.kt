package com.pomona.price.domain

import com.pomona.price.model.RetailDailyUpsert
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Types

/**
 * 소매 조사 쓰기. 접지 않으므로 [가격] API 가 준 행 수 그대로 들어간다.
 *
 * 소매도 재수집 대상이라(확정 지연 여부는 미측정) 같은 방식으로 upsert 한다.
 * 자연키에 `se_cd` 가 들어 있어 나중에 중도매(02)를 추가해도 소매 행을 덮어쓰지 않는다.
 */
@Repository
class RetailDailyUpsertRepository(private val jdbc: JdbcTemplate) {

    fun upsertAll(rows: Collection<RetailDailyUpsert>): Int {
        if (rows.isEmpty()) return 0
        val args = rows.map {
            arrayOf<Any?>(
                it.exmnYmd, it.seCd, it.seNm, it.ctgryCd, it.ctgryNm, it.itemCd, it.itemNm,
                it.vrtyCd, it.vrtyNm, it.grdCd, it.grdNm, it.sggCd, it.sggNm,
                it.mrktCd, it.mrktNm, it.unit, it.unitSz,
                it.exmnDdPrc, it.exmnDdCnvsPrc, it.orgnlRegDt,
            )
        }
        return jdbc.batchUpdate(SQL, args, TYPES).sum()
    }
}

private val TYPES = intArrayOf(
    Types.DATE, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
    Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
    Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
    Types.BIGINT, Types.BIGINT, Types.TIMESTAMP_WITH_TIMEZONE,
)

private val SQL = """
    insert into retail_daily (
        exmn_ymd, se_cd, se_nm, ctgry_cd, ctgry_nm, item_cd, item_nm,
        vrty_cd, vrty_nm, grd_cd, grd_nm, sgg_cd, sgg_nm, mrkt_cd, mrkt_nm,
        unit, unit_sz, exmn_dd_prc, exmn_dd_cnvs_prc, orgnl_reg_dt
    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    on conflict (exmn_ymd, se_cd, ctgry_cd, item_cd, vrty_cd, grd_cd, sgg_cd, mrkt_cd) do update
       set exmn_dd_prc      = excluded.exmn_dd_prc,
           exmn_dd_cnvs_prc = excluded.exmn_dd_cnvs_prc,
           orgnl_reg_dt     = excluded.orgnl_reg_dt,
           updated_at       = now()
""".trimIndent()
