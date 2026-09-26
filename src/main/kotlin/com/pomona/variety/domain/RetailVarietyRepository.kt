package com.pomona.variety.domain

import org.springframework.data.jpa.repository.JpaRepository

/** 소매 품종 조회. 쓰기는 [RetailVarietyUpsertRepository] 가 맡는다. */
interface RetailVarietyRepository : JpaRepository<RetailVariety, Long> {

    fun findAllByOrderByCtgryCdAscItemCdAscVrtyCdAsc(): List<RetailVariety>
}
