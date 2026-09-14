package com.pomona.price.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

/** 도매 집계 조회. 쓰기는 [WholesaleDailyUpsertRepository] 가 맡는다. */
interface WholesaleDailyRepository : JpaRepository<WholesaleDaily, Long> {

    /** 품종 페이지의 시계열. `ix_wholesale_variety_date` 를 그대로 탄다. */
    fun findByVarietyIdAndTrdClclnYmdBetweenOrderByTrdClclnYmd(
        varietyId: Long, from: LocalDate, to: LocalDate,
    ): List<WholesaleDaily>

    fun findByTrdClclnYmd(trdClclnYmd: LocalDate): List<WholesaleDaily>

    fun countByTrdClclnYmd(trdClclnYmd: LocalDate): Long
}
