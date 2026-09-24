package com.pomona.variety.model

/**
 * 기타 코드. 소분류 `99` 는 품목마다 있는 '기타' 칸이고, 중분류 `99` 는 '기타' 품목이다.
 * 중분류 `99` 아래의 `과실류(수입)` 은 이름에 '기타' 가 없어 이름으로 거르면 빠져나가므로 코드로 가른다.
 */
const val OTHER_CODE = "99"

/** 품종 페이지를 만들 품종. 주소(`/varieties/06-03-36`)와 셀렉트박스에 코드와 이름을 쓴다. */
data class PageVariety(
    val id: Long,
    val lclsfCd: String, val lclsfNm: String,
    val mclsfCd: String, val mclsfNm: String,
    val sclsfCd: String, val sclsfNm: String?,
)
