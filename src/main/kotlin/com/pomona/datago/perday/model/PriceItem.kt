package com.pomona.datago.perday.model

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * 일별 도·소매 가격정보(perDay/price) 응답의 item 한 건.
 *
 * `exmnDdCnvsPrc`(kg환산가)는 `unit` 이 kg 일 때만 실제로 환산된 값이다.
 * '개' 단위 품목은 조사가를 그대로 복사해 온다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class PriceItem(
    val exmnYmd: String,
    val seCd: String,
    val seNm: String,
    val ctgryCd: String,
    val ctgryNm: String,
    val itemCd: String,
    val itemNm: String,
    val vrtyCd: String,
    val vrtyNm: String,
    val grdCd: String,
    val grdNm: String,
    val sggCd: String,
    val sggNm: String,
    val unit: String,
    val unitSz: String,
    val mrktCd: String,
    val mrktNm: String,
    val exmnDdPrc: String,
    val exmnDdCnvsPrc: String,
    val orgnlRegDt: String,
)
