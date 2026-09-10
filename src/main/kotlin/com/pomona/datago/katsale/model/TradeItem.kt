package com.pomona.datago.katsale.model

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * 정산정보(katSale/trades) 응답의 item 한 건.
 *
 * 공공 API 는 모든 값을 문자열로 준다. 등급에 `.` `7등` 같은 비정형 값이 실제로 섞여 오므로
 * 여기서는 변환하지 않고 원본 그대로 담는다. 숫자·날짜 변환은 다음 계층에서 한다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class TradeItem(
    val trdClclnYmd: String,
    val whslMrktCd: String,
    val whslMrktNm: String,
    val corpCd: String,
    val corpNm: String,
    val gdsLclsfCd: String,
    val gdsLclsfNm: String,
    val gdsMclsfCd: String,
    val gdsMclsfNm: String,
    val gdsSclsfCd: String,
    val gdsSclsfNm: String,
    val trdSe: String,
    val unitCd: String,
    val unitNm: String,
    val unitQty: String,
    val unitTotQty: String,
    val pkgCd: String,
    val pkgNm: String,
    val szCd: String,
    val szNm: String,
    val grdCd: String,
    val grdNm: String,
    val plorCd: String,
    val plorNm: String,
    val totprc: String,
    val avgprc: String,
    val lwprc: String,
    val hgprc: String,
)
