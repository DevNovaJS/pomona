package com.pomona.variety.service

import com.pomona.variety.UnknownRetailVarietyException
import com.pomona.variety.VarietyMappingNotFoundException
import com.pomona.variety.VarietyNotFoundException
import com.pomona.variety.domain.RetailVarietyRepository
import com.pomona.variety.domain.VarietyRepository
import com.pomona.variety.domain.VarietyRetailMapping
import com.pomona.variety.domain.VarietyRetailMappingRepository
import com.pomona.variety.model.RetailVarietyResponse
import com.pomona.variety.model.VarietyMappingResponse
import com.pomona.variety.model.toResponse
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 백오피스 품종 매핑. 정산 품종에 소매 품종을 짝지어 품종 페이지에 소매가를 붙인다.
 *
 * 화면이 양쪽 목록에서 하나씩 골라 연결하므로 후보를 계산하지 않는다. 매핑이 없는 품종은 소매 짝이 없는 것이다.
 */
@Service
@Transactional(readOnly = true)
class VarietyMappingService(
    private val varietyRepository: VarietyRepository,
    private val retailVarietyRepository: RetailVarietyRepository,
    private val varietyRetailMappingRepository: VarietyRetailMappingRepository,
) {

    fun retailVarieties(): List<RetailVarietyResponse> =
        retailVarietyRepository.findAllByOrderByCtgryCdAscItemCdAscVrtyCdAsc().map { it.toResponse() }

    fun mappings(): List<VarietyMappingResponse> =
        varietyRetailMappingRepository.findAllByOrderByUpdatedAtDesc().map { it.toResponse() }

    /** 짝을 짓는다. 정산 품종에 이미 짝이 있으면 새 소매 품종으로 바꾼다 — 정산 품종 하나에 짝은 하나뿐이다. */
    @Transactional
    fun map(varietyId: Long, retailVarietyId: Long): VarietyMappingResponse {
        val variety = varietyRepository.findByIdOrNull(varietyId) ?: throw VarietyNotFoundException(varietyId)
        val retailVariety = retailVarietyRepository.findByIdOrNull(retailVarietyId)
            ?: throw UnknownRetailVarietyException(retailVarietyId)
        val mapping = varietyRetailMappingRepository.findByVarietyId(varietyId)
        if (mapping == null) {
            return varietyRetailMappingRepository.save(VarietyRetailMapping(variety, retailVariety)).toResponse()
        }
        mapping.mapTo(retailVariety)
        return mapping.toResponse()
    }

    /** 짝을 푼다. 그 품종 페이지에서 소매가가 빠진다. */
    @Transactional
    fun unmap(varietyId: Long) {
        val mapping = varietyRetailMappingRepository.findByVarietyId(varietyId) ?: throw VarietyMappingNotFoundException(varietyId)
        varietyRetailMappingRepository.delete(mapping)
    }
}
