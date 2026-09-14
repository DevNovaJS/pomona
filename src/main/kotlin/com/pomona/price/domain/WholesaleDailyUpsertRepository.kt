package com.pomona.price.domain

import com.pomona.price.model.WholesaleDailyUpsert
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Types

/**
 * 도매 집계 쓰기. 하루 600행 안팎을 한 번에 밀어 넣는다.
 *
 * 확정 지연 때문에 매일 D-1~D-5 를 다시 수집하므로 **같은 자연키가 반복해서 들어온다.**
 * 자연키 유니크 인덱스에 `ON CONFLICT DO UPDATE` 를 걸어 행이 늘지 않고 값만 정정되게 한다.
 * `updated_at` 이 움직이면 그 날짜의 값이 실제로 바뀌었다는 뜻이다.
 */
@Repository
class WholesaleDailyUpsertRepository(private val jdbc: JdbcTemplate) {

    fun upsertAll(rows: Collection<WholesaleDailyUpsert>): Int {
        if (rows.isEmpty()) return 0
        val args = rows.map {
            arrayOf<Any?>(
                it.trdClclnYmd, it.whslMrktCd, it.varietyId, it.trdSe, it.grdCd, it.grdNm,
                it.plorCd, it.plorNm, it.unitNm, it.totPrc, it.totQty,
                it.lowPrcPerKg, it.highPrcPerKg, it.tradeCount,
            )
        }
        return jdbc.batchUpdate(SQL, args, TYPES).sum()
    }
}

private val TYPES = intArrayOf(
    Types.DATE, Types.VARCHAR, Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
    Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BIGINT, Types.NUMERIC,
    Types.NUMERIC, Types.NUMERIC, Types.INTEGER,
)

private val SQL = """
    insert into wholesale_daily (
        trd_clcln_ymd, whsl_mrkt_cd, variety_id, trd_se, grd_cd, grd_nm,
        plor_cd, plor_nm, unit_nm, tot_prc, tot_qty, low_prc_per_kg, high_prc_per_kg, trade_count
    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    on conflict (trd_clcln_ymd, whsl_mrkt_cd, variety_id, trd_se, grd_cd, plor_cd, unit_nm) do update
       set grd_nm          = excluded.grd_nm,
           plor_nm         = excluded.plor_nm,
           tot_prc         = excluded.tot_prc,
           tot_qty         = excluded.tot_qty,
           low_prc_per_kg  = excluded.low_prc_per_kg,
           high_prc_per_kg = excluded.high_prc_per_kg,
           trade_count     = excluded.trade_count,
           updated_at      = now()
""".trimIndent()
