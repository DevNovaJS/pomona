package com.pomona.variety.domain

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface VarietyRetailMappingRepository : JpaRepository<VarietyRetailMapping, Long> {

    fun findByVarietyId(varietyId: Long): VarietyRetailMapping?

    /** 매핑 전부. 응답에 양쪽 품종 이름을 붙이므로 같이 읽는다. 최근에 고친 순. */
    @EntityGraph(attributePaths = ["variety", "retailVariety"])
    fun findAllByOrderByUpdatedAtDesc(): List<VarietyRetailMapping>
}
