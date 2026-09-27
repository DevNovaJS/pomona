package com.pomona.variety.service

import com.pomona.variety.domain.VarietyRepository
import com.pomona.variety.model.VarietyResponse
import com.pomona.variety.model.toResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 백오피스용 품종 목록. 공개 빌드용 [PublicVarietyService] 는 페이지를 만들 품종만 주지만, 백오피스는 사 먹은 과일이나
 * 소매 짝을 고르는 것이라 페이지가 없는 소량 품종까지 전부 준다.
 */
@Service
@Transactional(readOnly = true)
class AdminVarietyService(private val varietyRepository: VarietyRepository) {

    /** 품종 마스터 전부. 수백 종이라 한 번에 주고 찾기는 화면에서 한다. */
    fun varieties(): List<VarietyResponse> =
        varietyRepository.findAllByOrderByLclsfCdAscMclsfCdAscSclsfCdAsc().map { it.toResponse() }
}
