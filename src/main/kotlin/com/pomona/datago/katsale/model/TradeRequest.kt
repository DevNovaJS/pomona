package com.pomona.datago.katsale.model

import com.pomona.datago.common.MAX_ROWS_PER_PAGE
import java.time.LocalDate

/**
 * 정산정보(katSale/trades) 요청 조건.
 *
 * 날짜는 `::EQ` 라 하루씩만 조회된다. 도매시장 코드도 필수라 시장 하나씩 돌아야 한다.
 */
data class TradeRequest(
    val date: LocalDate,
    val marketCode: String,
    val categoryCode: String? = null,
    val pageNo: Int = 1,
    val numOfRows: Int = MAX_ROWS_PER_PAGE,
) {
    fun toParams(): Map<String, String> = buildMap {
        put("cond[trd_clcln_ymd::EQ]", date.toString())
        put("cond[whsl_mrkt_cd::EQ]", marketCode)
        if (categoryCode != null) {
            put("cond[gds_lclsf_cd::EQ]", categoryCode)
        }
        put("pageNo", pageNo.toString())
        put("numOfRows", numOfRows.toString())
    }
}
