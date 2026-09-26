package com.pomona.price.domain

import com.pomona.price.model.RetailDailyRow
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Types
import java.time.LocalDate

/**
 * 소매 조사 쓰기. 접지 않으므로 [가격] API 가 준 행 수 그대로 들어간다.
 *
 * 소매는 품목 하나를 기간으로 한 번에 받아오므로 **품목 × 기간 단위로 지우고 다시 넣는다.**
 * 재수집에서 사라진 점포 행도 함께 정리된다. 21만 행에서 품목 1개 × 5일(627행) 지우기 0.3ms.
 */
@Repository
class RetailDailyWriteRepository(private val jdbcTemplate: JdbcTemplate) {

    /**
     * [seCd]·[ctgryCd]·[itemCd] 의 [from]~[to] 행을 전부 지우고 [rows] 로 채운다.
     * [rows] 가 비어 있으면 그 범위가 비워진다.
     */
    @Transactional
    fun replaceRange(
        seCd: String, ctgryCd: String, itemCd: String,
        from: LocalDate, to: LocalDate,
        rows: Collection<RetailDailyRow>,
    ): Int {
        jdbcTemplate.update(DELETE, seCd, ctgryCd, itemCd, from, to)
        if (rows.isEmpty()) {
            return 0
        }

        val args = rows.map {
            arrayOf<Any?>(
                it.exmnYmd, it.seCd, it.seNm, it.ctgryCd, it.ctgryNm, it.itemCd, it.itemNm,
                it.vrtyCd, it.vrtyNm, it.grdCd, it.grdNm, it.sggCd, it.sggNm,
                it.mrktCd, it.mrktNm, it.unit, it.unitSz,
                it.exmnDdPrc, it.exmnDdCnvsPrc, it.orgnlRegDt,
            )
        }
        return jdbcTemplate.batchUpdate(INSERT, args, TYPES).sum()
    }
}

/** `ix_retail_item_date (item_cd, vrty_cd, exmn_ymd)` 를 탄다. */
private val DELETE = """
    delete from retail_daily
     where se_cd = ? and ctgry_cd = ? and item_cd = ?
       and exmn_ymd between ? and ?
""".trimIndent()

private val INSERT = """
    insert into retail_daily (
        exmn_ymd, se_cd, se_nm, ctgry_cd, ctgry_nm, item_cd, item_nm,
        vrty_cd, vrty_nm, grd_cd, grd_nm, sgg_cd, sgg_nm, mrkt_cd, mrkt_nm,
        unit, unit_sz, exmn_dd_prc, exmn_dd_cnvs_prc, orgnl_reg_dt
    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""".trimIndent()

private val TYPES = intArrayOf(
    Types.DATE, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
    Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
    Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
    Types.BIGINT, Types.BIGINT, Types.TIMESTAMP_WITH_TIMEZONE,
)
