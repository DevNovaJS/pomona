package com.pomona.variety.service

import com.pomona.price.service.BuildPeriodService
import com.pomona.variety.UnknownRetailVarietyException
import com.pomona.variety.VarietyMappingNotFoundException
import com.pomona.variety.VarietyNotFoundException
import com.pomona.variety.domain.PageVarietyRepository
import com.pomona.variety.domain.RetailVariety
import com.pomona.variety.domain.RetailVarietyRepository
import com.pomona.variety.domain.VarietyRepository
import com.pomona.variety.domain.VarietyRetailMapping
import com.pomona.variety.domain.VarietyRetailMappingRepository
import com.pomona.variety.model.PageVariety
import com.pomona.variety.model.RetailVarietyResponse
import com.pomona.variety.model.UnmappedVarietyResponse
import com.pomona.variety.model.VarietyMappingResponse
import com.pomona.variety.model.toResponse
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 백오피스 품종 매핑. 정산 품종에 소매 품종을 짝지어 품종 페이지에 소매가를 붙인다.
 *
 * 미연결 목록에는 품종 페이지 대상만 띄운다. 페이지가 없는 소량 품종은 소매가를 붙여도 보여줄 곳이 없다.
 * 그 품종도 매핑 자체는 할 수 있다.
 */
@Service
@Transactional(readOnly = true)
class VarietyMappingService(
    private val buildPeriodService: BuildPeriodService,
    private val pageVarietyRepository: PageVarietyRepository,
    private val varietyRepository: VarietyRepository,
    private val retailVarietyRepository: RetailVarietyRepository,
    private val varietyRetailMappingRepository: VarietyRetailMappingRepository,
) {

    /** 품종 페이지 대상 중 아직 한 번도 안 본 품종과 각각의 짝 후보. */
    fun unmapped(): List<UnmappedVarietyResponse> {
        val reviewed = varietyRetailMappingRepository.findReviewedVarietyIds()
        val retailVarieties = retailVarietyRepository.findAllByOrderByCtgryCdAscItemCdAscVrtyCdAsc()
        return pageVarietyRepository.findAll(buildPeriodService.current().baseDate)
            .filter { it.id !in reviewed }
            .map { variety -> UnmappedVarietyResponse(variety, candidatesFor(variety, retailVarieties).map { it.toResponse() }) }
    }

    fun retailVarieties(): List<RetailVarietyResponse> =
        retailVarietyRepository.findAllByOrderByCtgryCdAscItemCdAscVrtyCdAsc().map { it.toResponse() }

    fun mappings(): List<VarietyMappingResponse> =
        varietyRetailMappingRepository.findAllByOrderByUpdatedAtDesc().map { it.toResponse() }

    /** 짝을 짓거나 바꾼다. [retailVarietyId] 가 null 이면 "소매에 없음" 으로 저장한다. */
    @Transactional
    fun map(varietyId: Long, retailVarietyId: Long?): VarietyMappingResponse {
        val variety = varietyRepository.findByIdOrNull(varietyId) ?: throw VarietyNotFoundException(varietyId)
        val retailVariety = retailVarietyId?.let {
            retailVarietyRepository.findByIdOrNull(it) ?: throw UnknownRetailVarietyException(it)
        }
        val mapping = varietyRetailMappingRepository.findByVarietyId(varietyId)
        if (mapping == null) {
            return varietyRetailMappingRepository.save(VarietyRetailMapping(variety, retailVariety)).toResponse()
        }
        mapping.mapTo(retailVariety)
        return mapping.toResponse()
    }

    /** 매핑을 지워 다시 미연결로 돌린다. */
    @Transactional
    fun unmap(varietyId: Long) {
        val mapping = varietyRetailMappingRepository.findByVarietyId(varietyId) ?: throw VarietyMappingNotFoundException(varietyId)
        varietyRetailMappingRepository.delete(mapping)
    }
}

/**
 * 정산 품종 하나의 짝 후보. 품목 이름이 겹치는 소매 품종 전부이고, 품종 이름이 겹치는 것부터 앞에 온다.
 *
 * 소매 품종이 품목당 1~3개뿐이라 품목 안의 것을 다 보여주면 충분하다. "샤인마스캇 / 샤인머스켓" 같은 표기 차이는
 * 이름이 안 겹쳐도 같은 포도 품목 안에 보이니 사람이 고른다. 참다래(키위)처럼 품목 이름에 두 이름이 들어 있으면
 * 소매의 참다래·키위 둘 다 후보가 된다.
 */
internal fun candidatesFor(variety: PageVariety, retailVarieties: List<RetailVariety>): List<RetailVariety> =
    retailVarieties
        .filter { overlaps(variety.mclsfNm, it.itemNm) }
        .sortedByDescending { retail -> variety.sclsfNm?.let { overlaps(it, retail.vrtyNm) } ?: false }

/** 한쪽 이름이 다른 쪽에 들어 있으면 겹친다고 본다. 로얄후지 ⊃ 후지, 쓰가루(아오리) ⊃ 아오리. */
private fun overlaps(a: String, b: String): Boolean = a.contains(b) || b.contains(a)
