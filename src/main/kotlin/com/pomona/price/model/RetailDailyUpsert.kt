package com.pomona.price.model

import java.time.LocalDate
import java.time.OffsetDateTime

/** 소매 조사 한 건. 접지 않으므로 [가격] API 한 행과 1:1 이다. */
data class RetailDailyUpsert(
    val exmnYmd: LocalDate,
    val seCd: String, val seNm: String,
    val ctgryCd: String, val ctgryNm: String,
    val itemCd: String, val itemNm: String,
    val vrtyCd: String, val vrtyNm: String,
    val grdCd: String, val grdNm: String,
    val sggCd: String, val sggNm: String,
    val mrktCd: String, val mrktNm: String,
    val unit: String, val unitSz: String,
    val exmnDdPrc: Long, val exmnDdCnvsPrc: Long,
    val orgnlRegDt: OffsetDateTime?,
)
