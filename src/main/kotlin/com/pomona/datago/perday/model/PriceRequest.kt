package com.pomona.datago.perday.model

import com.pomona.datago.common.MAX_ROWS_PER_PAGE
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class PriceRequest(
    val from: LocalDate,
    val to: LocalDate,
    val categoryCode: String,
    val itemCode: String,
    val varietyCode: String? = null,
    val pageNo: Int = 1,
    val numOfRows: Int = MAX_ROWS_PER_PAGE,
) {
    fun toParams(): Map<String, String> = buildMap {
        put("cond[exmn_ymd::GTE]", from.format(YMD))
        put("cond[exmn_ymd::LTE]", to.format(YMD))
        put("cond[se_cd::EQ]", RETAIL)
        put("cond[ctgry_cd::EQ]", categoryCode)
        put("cond[item_cd::EQ]", itemCode)
        if (varietyCode != null) {
            put("cond[vrty_cd::EQ]", varietyCode)
        }
        put("pageNo", pageNo.toString())
        put("numOfRows", numOfRows.toString())
    }
}

/** 조사 구분. 01 소매 / 02 중도매 / 03 친환경. 이 프로젝트는 소매만 수집한다. */
private const val RETAIL = "01"

/** 가격 API 는 구분자 없는 YYYYMMDD 를 쓴다. 정산정보의 YYYY-MM-DD 와 다르다. */
private val YMD: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
