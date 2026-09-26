package com.pomona.variety.model

import com.pomona.variety.domain.RetailVariety
import com.pomona.variety.domain.VarietyRetailMapping
import java.time.OffsetDateTime

/** 매핑 요청. [retailVarietyId] 가 null 이면 "확인했는데 소매에 없음" 으로 저장한다. */
data class VarietyMappingRequest(val retailVarietyId: Long?)

data class RetailVarietyResponse(
    val id: Long,
    val itemCd: String,
    val itemNm: String,
    val vrtyCd: String,
    val vrtyNm: String,
)

fun RetailVariety.toResponse() = RetailVarietyResponse(id!!, itemCd, itemNm, vrtyCd, vrtyNm)

/** 미연결 품종 하나와 짝 후보. 후보는 품종 이름이 겹치는 것부터 온다. */
data class UnmappedVarietyResponse(
    val variety: PageVariety,
    val candidates: List<RetailVarietyResponse>,
)

data class VarietyMappingResponse(
    val varietyId: Long,
    val itemName: String,
    val varietyName: String?,
    /** null 이면 확인했는데 소매에 없음 */
    val retailVariety: RetailVarietyResponse?,
    val updatedAt: OffsetDateTime,
)

fun VarietyRetailMapping.toResponse() = VarietyMappingResponse(
    varietyId = variety.id!!, itemName = variety.mclsfNm, varietyName = variety.sclsfNm,
    retailVariety = retailVariety?.toResponse(), updatedAt = updatedAt,
)
