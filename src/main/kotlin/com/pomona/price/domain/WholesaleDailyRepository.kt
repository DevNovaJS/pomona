package com.pomona.price.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

/** 도매 집계 조회. 쓰기는 [WholesaleDailyWriteRepository] 가 맡는다. */
interface WholesaleDailyRepository : JpaRepository<WholesaleDaily, Long> {

    /** 품종 페이지의 시계열. `ix_wholesale_variety_date` 를 그대로 탄다. */
    fun findByVarietyIdAndTrdClclnYmdBetweenOrderByTrdClclnYmd(
        varietyId: Long, from: LocalDate, to: LocalDate,
    ): List<WholesaleDaily>

    fun findByTrdClclnYmd(trdClclnYmd: LocalDate): List<WholesaleDaily>

    fun countByTrdClclnYmd(trdClclnYmd: LocalDate): Long

    /** 마지막 거래일. 공개 빌드의 기준일이다. 유니크 제약 인덱스가 날짜로 시작해 인덱스 끝만 읽는다. */
    @Query("select max(w.trdClclnYmd) from WholesaleDaily w")
    fun findLastTradeDate(): LocalDate
}
