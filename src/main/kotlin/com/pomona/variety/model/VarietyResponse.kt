package com.pomona.variety.model

import com.pomona.variety.domain.VarietyMaster

/** 품종 마스터 한 줄. 백오피스에서 품종을 고를 때 쓴다 — 리뷰의 품종 연결, 품종 매핑의 정산 품종. */
data class VarietyResponse(
    val id: Long,
    val lclsfCd: String, val lclsfNm: String,
    val mclsfCd: String, val mclsfNm: String,
    val sclsfCd: String, val sclsfNm: String?,
)

fun VarietyMaster.toResponse() = VarietyResponse(id!!, lclsfCd, lclsfNm, mclsfCd, mclsfNm, sclsfCd, sclsfNm)
