package com.pomona.price.model

import java.math.BigDecimal
import java.time.LocalDate

/** 도매 집계 한 줄. 배치가 [정산] 원본 여러 행을 접어서 만든 결과. */
data class WholesaleDailyUpsert(
    val trdClclnYmd: LocalDate,
    val whslMrktCd: String,
    val varietyId: Long,
    val trdSe: String,
    val grdCd: String,
    val grdNm: String,
    val plorCd: String,
    val plorNm: String?,
    val unitNm: String,
    val totPrc: Long,
    val totQty: BigDecimal,
    val lowPrcPerKg: BigDecimal,
    val highPrcPerKg: BigDecimal,
    val tradeCount: Int,
)
