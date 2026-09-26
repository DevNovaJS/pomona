package com.pomona.price.domain

import com.pomona.price.model.WholesaleDailyRow
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Types
import java.time.LocalDate

/**
 * 도매 집계 쓰기. JPA 가 아니라 `JdbcTemplate` 로 하루치를 한 번에 밀어 넣는다.
 *
 * 확정 지연 때문에 매일 D-1~D-5 를 다시 수집하는데, **날짜 하나를 통째로 지우고 다시 넣는다.**
 * upsert 는 재수집 결과에서 사라진 키(예: 등급이 정정돼 '상' 행이 없어진 경우)를 건드리지 못해
 * 옛 행이 남고 그날 물량이 두 번 잡힌다. 지우고 넣으면 "이 날짜의 데이터 = API 가 지금 주는 것" 이 된다.
 *
 * 1년치(29만 행)에서 잰 값: 날짜 하나(844행) 지우고 넣기 약 6.6ms, 같은 행 upsert 11.8ms.
 */
@Repository
class WholesaleDailyWriteRepository(private val jdbcTemplate: JdbcTemplate) {

    /**
     * [date]·[marketCode] 의 행을 전부 지우고 [rows] 로 채운다. 한 트랜잭션이라
     * 커밋 전까지 다른 조회는 옛 데이터를 본다 — 비어 있는 순간이 밖에 보이지 않는다.
     * [rows] 가 비어 있으면 그 날짜가 비워진다. API 가 0행을 줬다는 건 그날 데이터가 없다는 뜻이다.
     */
    @Transactional
    fun replaceDay(date: LocalDate, marketCode: String, rows: Collection<WholesaleDailyRow>): Int {
        jdbcTemplate.update(DELETE, date, marketCode)
        if (rows.isEmpty()) {
            return 0
        }

        val args = rows.map {
            arrayOf<Any?>(
                it.trdClclnYmd, it.whslMrktCd, it.varietyId, it.trdSe, it.grdCd, it.grdNm,
                it.plorCd, it.plorNm, it.unitNm, it.totPrc, it.totQty,
                it.lowPrcPerKg, it.highPrcPerKg, it.tradeCount,
            )
        }
        return jdbcTemplate.batchUpdate(INSERT, args, TYPES).sum()
    }
}

/** 유니크 인덱스 `uq_wholesale_daily` 가 (날짜, 시장, …) 로 시작해 이 조건을 그대로 탄다. */
private const val DELETE = "delete from wholesale_daily where trd_clcln_ymd = ? and whsl_mrkt_cd = ?"

private val INSERT = """
    insert into wholesale_daily (
        trd_clcln_ymd, whsl_mrkt_cd, variety_id, trd_se, grd_cd, grd_nm,
        plor_cd, plor_nm, unit_nm, tot_prc, tot_qty, low_prc_per_kg, high_prc_per_kg, trade_count
    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val TYPES = intArrayOf(
    Types.DATE, Types.VARCHAR, Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
    Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BIGINT, Types.NUMERIC,
    Types.NUMERIC, Types.NUMERIC, Types.INTEGER,
)
