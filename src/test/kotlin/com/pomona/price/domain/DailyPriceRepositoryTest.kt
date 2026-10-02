package com.pomona.price.domain

import com.pomona.price.model.WholesaleDailyRow
import com.pomona.variety.domain.VarietyUpsertRepository
import com.pomona.variety.model.VarietyUpsert
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDate

/** 로컬 DB 에 실데이터(2026년)가 있으므로 겹치지 않게 2099년 날짜만 쓴다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(VarietyUpsertRepository::class, WholesaleDailyWriteRepository::class, DailyPriceRepository::class)
class DailyPriceRepositoryTest {

    @Autowired private lateinit var varietyUpsertRepository: VarietyUpsertRepository
    @Autowired private lateinit var wholesaleDailyWriteRepository: WholesaleDailyWriteRepository
    @Autowired private lateinit var dailyPriceRepository: DailyPriceRepository

    private val garak = "110001"

    /** 30일은 2099-01-02 ~ 01-31 */
    private val end = LocalDate.of(2099, 1, 31)

    private fun plantVariety(sclsfCd: String) =
        varietyUpsertRepository.upsert(VarietyUpsert("ZZ", "시험대분류", "ZZ", "시험중분류", sclsfCd, "시험품종$sclsfCd"))

    private fun row(varietyId: Long, date: LocalDate, grdCd: String, totPrc: Long, qty: String) = WholesaleDailyRow(
        trdClclnYmd = date, whslMrktCd = garak, varietyId = varietyId,
        trdSe = "경매", grdCd = grdCd, grdNm = "등급$grdCd", plorCd = "367000", plorNm = null, unitNm = "kg",
        totPrc = totPrc, totQty = BigDecimal(qty),
        lowPrcPerKg = BigDecimal.ONE, highPrcPerKg = BigDecimal.ONE, tradeCount = 1,
    )

    /** 날짜별로 묶어 그 날짜를 채운다. 쓰기 레포가 날짜 단위로 교체하기 때문이다. */
    private fun plant(vararg rows: WholesaleDailyRow) {
        rows.groupBy { it.trdClclnYmd }.forEach { (date, sameDay) -> wholesaleDailyWriteRepository.replaceDay(date, garak, sameDay) }
    }

    @Test
    fun `하루 가격은 등급을 합친 총액 합을 물량 합으로 나눈 값이다`() {
        val id = plantVariety("01")
        plant(
            row(id, LocalDate.of(2099, 1, 20), grdCd = "11", totPrc = 10_000, qty = "10"), // kg당 1,000
            row(id, LocalDate.of(2099, 1, 20), grdCd = "12", totPrc = 4_000, qty = "2"),   // kg당 2,000
        )

        val result = dailyPriceRepository.findAll(end).single()

        // 등급 가격의 단순 평균(1,500)이 아니라 14,000 ÷ 12
        assertThat(result.perKg).isEqualByComparingTo("1166.67")
        assertThat(result.qty).isEqualByComparingTo("12")
    }

    @Test
    fun `기준일 포함 30일 안의 거래일만 날짜 순으로 준다`() {
        val id = plantVariety("01")
        plant(
            row(id, LocalDate.of(2099, 1, 1), grdCd = "11", totPrc = 1_000, qty = "1"),  // 30일 하루 전
            row(id, LocalDate.of(2099, 1, 15), grdCd = "11", totPrc = 1_000, qty = "1"),
            row(id, LocalDate.of(2099, 1, 2), grdCd = "11", totPrc = 1_000, qty = "1"),  // 30일 첫날
            row(id, LocalDate.of(2099, 1, 31), grdCd = "11", totPrc = 1_000, qty = "1"), // 기준일
            row(id, LocalDate.of(2099, 2, 1), grdCd = "11", totPrc = 1_000, qty = "1"),  // 기준일 다음 날
        )

        assertThat(dailyPriceRepository.findAll(end).map { it.date }).containsExactly(
            LocalDate.of(2099, 1, 2), LocalDate.of(2099, 1, 15), LocalDate.of(2099, 1, 31),
        )
    }

    @Test
    fun `30일 안에 거래가 없는 품종은 빠진다`() {
        val traded = plantVariety("01")
        val stale = plantVariety("02")
        plant(
            row(traded, LocalDate.of(2099, 1, 20), grdCd = "11", totPrc = 1_000, qty = "1"),
            row(stale, LocalDate.of(2099, 1, 1), grdCd = "11", totPrc = 1_000, qty = "1"),
        )

        assertThat(dailyPriceRepository.findAll(end).map { it.varietyId }).containsExactly(traded)
    }
}
