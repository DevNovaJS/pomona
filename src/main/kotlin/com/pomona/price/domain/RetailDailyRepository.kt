package com.pomona.price.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

/** 소매 조사 조회. 쓰기는 [RetailDailyWriteRepository] 가 맡는다. */
interface RetailDailyRepository : JpaRepository<RetailDaily, Long> {

    fun findByItemCdAndVrtyCdAndExmnYmdBetweenOrderByExmnYmd(
        itemCd: String, vrtyCd: String, from: LocalDate, to: LocalDate,
    ): List<RetailDaily>

    fun countByExmnYmd(exmnYmd: LocalDate): Long
}
