package com.pomona.variety.model

/** 품목. 품목 페이지(`/items/06-03`)와 셀렉트박스에 쓴다. */
data class Item(
    val lclsfCd: String, val lclsfNm: String,
    val mclsfCd: String, val mclsfNm: String,
)
