package com.pomona.variety.model

import com.pomona.variety.domain.RetailVariety
import com.pomona.variety.domain.VarietyRetailMapping
import java.time.OffsetDateTime

/** 매핑 요청. 정산 품종은 주소로 받는다. */
data class VarietyMappingRequest(val retailVarietyId: Long)

data class RetailVarietyResponse(
    val id: Long,
    val itemCd: String,
    val itemNm: String,
    val vrtyCd: String,
    val vrtyNm: String,
)

fun RetailVariety.toResponse() = RetailVarietyResponse(id!!, itemCd, itemNm, vrtyCd, vrtyNm)

data class VarietyMappingResponse(
    val varietyId: Long,
    val itemName: String,
    val varietyName: String?,
    val retailVariety: RetailVarietyResponse,
    val updatedAt: OffsetDateTime,
)

fun VarietyRetailMapping.toResponse() = VarietyMappingResponse(
    varietyId = variety.id!!, itemName = variety.mclsfNm, varietyName = variety.sclsfNm,
    retailVariety = retailVariety.toResponse(), updatedAt = updatedAt,
)
