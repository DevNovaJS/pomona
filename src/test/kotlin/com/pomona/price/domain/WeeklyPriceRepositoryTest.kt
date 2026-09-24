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

/** 로컬 DB 에 실데이터(2026년)가 있으므로 겹치지 않게 2098~2099년 날짜만 쓴다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(VarietyUpsertRepository::class, WholesaleDailyWriteRepository::class, WeeklyPriceRepository::class)
class WeeklyPriceRepositoryTest {

    @Autowired private lateinit var varieties: VarietyUpsertRepository
    @Autowired private lateinit var wholesale: WholesaleDailyWriteRepository
    @Autowired private lateinit var prices: WeeklyPriceRepository

    private val garak = "110001"

    /** 이번 주는 2099-01-04 ~ 01-10, 작년 같은 주는 364일 전인 2098-01-05 ~ 01-11 */
    private val end = LocalDate.of(2099, 1, 10)

    private fun plantVariety(sclsfCd: String) =
        varieties.upsert(VarietyUpsert("ZZ", "시험대분류", "ZZ", "시험중분류", sclsfCd, "시험품종$sclsfCd"))

    private fun row(varietyId: Long, date: LocalDate, totPrc: Long, qty: String) = WholesaleDailyRow(
        trdClclnYmd = date, whslMrktCd = garak, varietyId = varietyId,
        trdSe = "경매", grdCd = "11", grdNm = "특", plorCd = "367000", plorNm = null, unitNm = "kg",
        totPrc = totPrc, totQty = BigDecimal(qty),
        lowPrcPerKg = BigDecimal.ONE, highPrcPerKg = BigDecimal.ONE, tradeCount = 1,
    )

    /** 날짜별로 묶어 그 날짜를 채운다. 쓰기 레포가 날짜 단위로 교체하기 때문이다. */
    private fun plant(vararg rows: WholesaleDailyRow) {
        rows.groupBy { it.trdClclnYmd }.forEach { (date, sameDay) -> wholesale.replaceDay(date, garak, sameDay) }
    }

    @Test
    fun `주 평균은 7일간 총액 합을 물량 합으로 나눈 값이다`() {
        val id = plantVariety("01")
        plant(
            row(id, LocalDate.of(2099, 1, 5), totPrc = 10_000, qty = "10"),  // kg당 1,000
            row(id, LocalDate.of(2099, 1, 6), totPrc = 4_000, qty = "2"),    // kg당 2,000
        )

        val result = prices.findAll(end).single()

        // 하루 가격의 단순 평균(1,500)이 아니라 14,000 ÷ 12
        assertThat(result.thisWeekPerKg).isEqualByComparingTo("1166.67")
    }

    @Test
    fun `작년 같은 주 평균과 나란히 준다`() {
        val id = plantVariety("01")
        plant(
            row(id, LocalDate.of(2099, 1, 5), totPrc = 12_000, qty = "10"),
            row(id, LocalDate.of(2098, 1, 6), totPrc = 5_000, qty = "5"),
        )

        val result = prices.findAll(end).single()

        assertThat(result.thisWeekPerKg).isEqualByComparingTo("1200")
        assertThat(result.lastYearPerKg).isEqualByComparingTo("1000")
        assertThat(result.changeRate).isEqualByComparingTo("20.0")
    }

    @Test
    fun `7일 구간 경계 밖의 날은 들어가지 않는다`() {
        val id = plantVariety("01")
        plant(
            row(id, LocalDate.of(2099, 1, 3), totPrc = 900_000, qty = "1"),  // 이번 주 하루 전
            row(id, LocalDate.of(2099, 1, 4), totPrc = 1_000, qty = "1"),    // 이번 주 첫날
            row(id, LocalDate.of(2099, 1, 10), totPrc = 1_000, qty = "1"),   // 이번 주 마지막 날
            row(id, LocalDate.of(2099, 1, 11), totPrc = 900_000, qty = "1"), // 기준일 다음 날
            row(id, LocalDate.of(2098, 1, 4), totPrc = 900_000, qty = "1"),  // 작년 주 하루 전
            row(id, LocalDate.of(2098, 1, 5), totPrc = 2_000, qty = "1"),    // 작년 주 첫날
            row(id, LocalDate.of(2098, 1, 11), totPrc = 2_000, qty = "1"),   // 작년 주 마지막 날
            row(id, LocalDate.of(2098, 1, 12), totPrc = 900_000, qty = "1"), // 작년 주 다음 날
        )

        val result = prices.findAll(end).single()

        assertThat(result.thisWeekPerKg).isEqualByComparingTo("1000")
        assertThat(result.lastYearPerKg).isEqualByComparingTo("2000")
    }

    @Test
    fun `작년 같은 주에 거래가 없으면 작년 값과 등락률이 비어 있다`() {
        val id = plantVariety("01")
        plant(row(id, LocalDate.of(2099, 1, 5), totPrc = 1_000, qty = "1"))

        val result = prices.findAll(end).single()

        assertThat(result.lastYearPerKg).isNull()
        assertThat(result.changeRate).isNull()
    }

    @Test
    fun `이번 주에 거래가 없는 품종은 빠진다`() {
        val traded = plantVariety("01")
        val lastYearOnly = plantVariety("02")
        plant(
            row(traded, LocalDate.of(2099, 1, 5), totPrc = 1_000, qty = "1"),
            row(lastYearOnly, LocalDate.of(2098, 1, 6), totPrc = 1_000, qty = "1"),
        )

        assertThat(prices.findAll(end).map { it.varietyId }).containsExactly(traded)
    }
}
