package com.pomona.variety.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

/** 품종이 거래에 등장한 구간. 컬럼이 아니라 조회 결과다. */
interface TradedDateRange {
    val firstTradedOn: LocalDate?
    val lastTradedOn: LocalDate?
}

/** 품종 조회. 쓰기는 [VarietyUpsertRepository] 가 맡는다. */
interface VarietyRepository : JpaRepository<VarietyMaster, Long> {

    fun findByLclsfCdAndMclsfCdAndSclsfCd(lclsfCd: String, mclsfCd: String, sclsfCd: String): VarietyMaster?

    fun findAllByLclsfCdOrderByMclsfCdAscSclsfCdAsc(lclsfCd: String): List<VarietyMaster>

    /**
     * 이 품종이 거래에 처음·마지막으로 등장한 날. 컬럼으로 들고 있지 않고 여기서 뽑는다 —
     * `ix_wholesale_variety_date` 인덱스의 양 끝만 읽으므로 테이블을 훑지 않는다.
     */
    @Query(
        """
        select min(w.trdClclnYmd) as firstTradedOn,
               max(w.trdClclnYmd) as lastTradedOn
          from WholesaleDaily w
         where w.variety.id = :varietyId
        """
    )
    fun findTradedDateRange(varietyId: Long): TradedDateRange
}
