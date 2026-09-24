package com.pomona.variety.model

/** 품종 페이지를 만들 품종. 주소(`/varieties/06-03-36`)와 셀렉트박스에 코드와 이름을 쓴다. */
data class PageVariety(
    val id: Long,
    val lclsfCd: String, val lclsfNm: String,
    val mclsfCd: String, val mclsfNm: String,
    val sclsfCd: String, val sclsfNm: String?,
)
