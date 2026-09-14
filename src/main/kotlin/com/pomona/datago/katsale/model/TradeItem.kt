package com.pomona.datago.katsale.model

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * 정산정보(katSale/trades) 응답의 item 한 건.
 *
 * 공공 API 는 모든 값을 문자열로 준다. 등급에 `.` `7등` 같은 비정형 값이 실제로 섞여 오므로
 * 여기서는 변환하지 않고 원본 그대로 담는다. 숫자·날짜 변환은 다음 계층에서 한다.
 *
 * **모든 필드가 nullable 이다.** 문서에 없는 null 이 실제로 온다 — `pkg_nm` 은 조회한 모든
 * 날짜(2025-09 ~ 2026-09)에서 100% null 이었다. 여기서 non-null 을 강요하면 역직렬화가
 * 통째로 터져 어느 행이 문제였는지도 남지 않는다. 수신은 관대하게 받고, 필요한 필드의
 * 검증은 엔티티로 옮기는 매핑 계층에서 행 단위로 한다.
 *
 * `plor_nm` 에는 꼬리 공백이 붙어 온다(`"충청북도 괴산군    "`). 원본 보존이 목적이라
 * 여기서 trim 하지 않는다. 역시 매핑 계층의 몫이다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class TradeItem(
    val trdClclnYmd: String?,
    val whslMrktCd: String?,
    val whslMrktNm: String?,
    val corpCd: String?,
    val corpNm: String?,
    val gdsLclsfCd: String?,
    val gdsLclsfNm: String?,
    val gdsMclsfCd: String?,
    val gdsMclsfNm: String?,
    val gdsSclsfCd: String?,
    val gdsSclsfNm: String?,
    val trdSe: String?,
    val unitCd: String?,
    val unitNm: String?,
    val unitQty: String?,
    val unitTotQty: String?,
    val pkgCd: String?,
    val pkgNm: String?,
    val szCd: String?,
    val szNm: String?,
    val grdCd: String?,
    val grdNm: String?,
    val plorCd: String?,
    val plorNm: String?,
    val totprc: String?,
    val avgprc: String?,
    val lwprc: String?,
    val hgprc: String?,
)
