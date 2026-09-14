package com.pomona.variety.model

/**
 * 품종 upsert 입력. 엔티티가 아니라 값 덩어리라 `data class` 가 맞다 —
 * 식별자가 없고, 생성 후 바뀌지 않으며, 내용이 같으면 같은 것으로 봐도 문제가 없다.
 */
data class VarietyUpsert(
    val lclsfCd: String, val lclsfNm: String,
    val mclsfCd: String, val mclsfNm: String,
    val sclsfCd: String, val sclsfNm: String?,
)
