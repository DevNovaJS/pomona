package com.pomona.variety.model

/** 소매 품종 upsert 입력. [VarietyUpsert] 의 소매 쪽. */
data class RetailVarietyUpsert(
    val ctgryCd: String, val ctgryNm: String,
    val itemCd: String, val itemNm: String,
    val vrtyCd: String, val vrtyNm: String,
)
