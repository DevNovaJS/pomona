package com.pomona.variety.domain

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface VarietyRetailMappingRepository : JpaRepository<VarietyRetailMapping, Long> {

    fun findByVarietyId(varietyId: Long): VarietyRetailMapping?

    /** 매핑 전부. 응답에 양쪽 품종 이름을 붙이므로 같이 읽는다. 최근에 고친 순. */
    @EntityGraph(attributePaths = ["variety", "retailVariety"])
    fun findAllByOrderByUpdatedAtDesc(): List<VarietyRetailMapping>

    /** 한 번이라도 본 품종. "소매에 없음" 으로 확인한 것도 들어간다. */
    @Query("select m.variety.id from VarietyRetailMapping m")
    fun findReviewedVarietyIds(): Set<Long>
}
