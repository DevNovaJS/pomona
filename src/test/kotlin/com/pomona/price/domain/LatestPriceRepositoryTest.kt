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
@Import(VarietyUpsertRepository::class, WholesaleDailyWriteRepository::class, LatestPriceRepository::class)
class LatestPriceRepositoryTest {

    @Autowired private lateinit var varieties: VarietyUpsertRepository
    @Autowired private lateinit var wholesale: WholesaleDailyWriteRepository
    @Autowired private lateinit var prices: LatestPriceRepository

    private val garak = "110001"

    /** 최근 12개월은 2099-01-01 ~ 2099-12-31 */
    private val end = LocalDate.of(2099, 12, 31)

    private fun plantVariety(sclsfCd: String) =
        varieties.upsert(VarietyUpsert("ZZ", "시험대분류", "ZZ", "시험중분류", sclsfCd, "시험품종$sclsfCd"))

    private fun row(
        varietyId: Long,
        date: LocalDate,
        totPrc: Long,
        qty: String,
        grdCd: String = "11",
        grdNm: String = "특",
        plorCd: String = "367000",
    ) = WholesaleDailyRow(
        trdClclnYmd = date, whslMrktCd = garak, varietyId = varietyId,
        trdSe = "경매", grdCd = grdCd, grdNm = grdNm, plorCd = plorCd, plorNm = null, unitNm = "kg",
        totPrc = totPrc, totQty = BigDecimal(qty),
        lowPrcPerKg = BigDecimal.ONE, highPrcPerKg = BigDecimal.ONE, tradeCount = 1,
    )

    /** 날짜별로 묶어 그 날짜를 채운다. 쓰기 레포가 날짜 단위로 교체하므로 품종이 여럿이면 한 번에 넣어야 한다. */
    private fun plant(vararg rows: WholesaleDailyRow) {
        rows.groupBy { it.trdClclnYmd }.forEach { (date, sameDay) -> wholesale.replaceDay(date, garak, sameDay) }
    }

    @Test
    fun `마지막 거래일의 가격만 쓴다`() {
        val id = plantVariety("01")
        plant(
            row(id, LocalDate.of(2099, 12, 1), totPrc = 90_000, qty = "10"),
            row(id, LocalDate.of(2099, 12, 20), totPrc = 10_000, qty = "10"),
        )

        val result = prices.findAll(end).single()

        assertThat(result.date).isEqualTo(LocalDate.of(2099, 12, 20))
        assertThat(result.perKg).isEqualByComparingTo("1000")
    }

    @Test
    fun `등급을 합친 대표가와 등급별 대표가를 같이 준다`() {
        val id = plantVariety("01")
        val day = LocalDate.of(2099, 12, 20)
        plant(
            row(id, day, totPrc = 6_000, qty = "5", grdCd = "11", grdNm = "특"),
            row(id, day, totPrc = 4_000, qty = "5", grdCd = "11", grdNm = "특", plorCd = "568000"),
            row(id, day, totPrc = 3_000, qty = "5", grdCd = "12", grdNm = "상"),
        )

        val result = prices.findAll(end).single()

        // 합산은 13,000 ÷ 15. 등급별 값의 평균(800)이 아니다
        assertThat(result.perKg).isEqualByComparingTo("866.67")
        // 같은 등급의 산지 두 줄은 한 등급으로 합친다
        assertThat(result.grades.map { Triple(it.grdCd, it.grdNm, it.perKg.toInt()) })
            .containsExactly(Triple("11", "특", 1000), Triple("12", "상", 600))
    }

    @Test
    fun `품종마다 자기 마지막 거래일을 쓴다`() {
        val a = plantVariety("01")
        val b = plantVariety("02")
        plant(
            row(a, LocalDate.of(2099, 12, 30), totPrc = 1_000, qty = "1"),
            row(b, LocalDate.of(2099, 12, 20), totPrc = 2_000, qty = "1"),
        )

        assertThat(prices.findAll(end).map { it.varietyId to it.date }).containsExactly(
            a to LocalDate.of(2099, 12, 30),
            b to LocalDate.of(2099, 12, 20),
        )
    }

    @Test
    fun `12개월 안에 거래가 없는 품종은 빠진다`() {
        val id = plantVariety("01")
        plant(row(id, LocalDate.of(2098, 12, 31), totPrc = 1_000, qty = "1"))

        assertThat(prices.findAll(end)).isEmpty()
    }
}
