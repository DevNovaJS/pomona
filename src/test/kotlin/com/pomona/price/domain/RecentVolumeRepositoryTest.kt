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
@Import(VarietyUpsertRepository::class, WholesaleDailyWriteRepository::class, RecentVolumeRepository::class)
class RecentVolumeRepositoryTest {

    @Autowired private lateinit var varietyUpsertRepository: VarietyUpsertRepository
    @Autowired private lateinit var wholesaleDailyWriteRepository: WholesaleDailyWriteRepository
    @Autowired private lateinit var recentVolumeRepository: RecentVolumeRepository

    private val garak = "110001"

    /** 최근 14일은 2099-06-17 ~ 06-30, 12개월은 2098-07-01 ~ 2099-06-30 */
    private val end = LocalDate.of(2099, 6, 30)

    private fun plantVariety(sclsfCd: String) =
        varietyUpsertRepository.upsert(VarietyUpsert("ZZ", "시험대분류", "ZZ", "시험중분류", sclsfCd, "시험품종$sclsfCd"))

    private fun row(varietyId: Long, date: LocalDate, qty: String, plorCd: String = "367000") = WholesaleDailyRow(
        trdClclnYmd = date, whslMrktCd = garak, varietyId = varietyId,
        trdSe = "경매", grdCd = "11", grdNm = "특", plorCd = plorCd, plorNm = null, unitNm = "kg",
        totPrc = 1_000, totQty = BigDecimal(qty),
        lowPrcPerKg = BigDecimal.ONE, highPrcPerKg = BigDecimal.ONE, tradeCount = 1,
    )

    /** 날짜별로 묶어 그 날짜를 채운다. 쓰기 레포가 날짜 단위로 교체하기 때문이다. */
    private fun plant(vararg rows: WholesaleDailyRow) {
        rows.groupBy { it.trdClclnYmd }.forEach { (date, sameDay) -> wholesaleDailyWriteRepository.replaceDay(date, garak, sameDay) }
    }

    private fun resultOf(varietyId: Long) = recentVolumeRepository.findAll(end).single { it.varietyId == varietyId }

    @Test
    fun `평소는 12개월 물량을 14일치로 줄인 값이고 배수는 최근 14일을 그것으로 나눈 값이다`() {
        val id = plantVariety("01")
        plant(
            row(id, LocalDate.of(2098, 9, 1), qty = "2650"),
            row(id, LocalDate.of(2099, 6, 20), qty = "1000"),
        )

        val result = resultOf(id)

        // 평소 = 3,650 × 14 ÷ 365 = 140, 배수 = 1,000 ÷ 140
        assertThat(result.recentQty).isEqualByComparingTo("1000")
        assertThat(result.usualQty).isEqualByComparingTo("140")
        assertThat(result.ratio).isEqualByComparingTo("7.1")
    }

    @Test
    fun `최근 14일은 기준일 포함 14일째까지이고 12개월보다 오래된 거래는 평소에 넣지 않는다`() {
        val id = plantVariety("02")
        plant(
            row(id, LocalDate.of(2098, 6, 30), qty = "99999"),  // 12개월 밖
            row(id, LocalDate.of(2099, 6, 16), qty = "500"),    // 15일째 — 평소에만 들어간다
            row(id, LocalDate.of(2099, 6, 17), qty = "100"),    // 14일째
        )

        val result = resultOf(id)

        assertThat(result.recentQty).isEqualByComparingTo("100")
        assertThat(result.usualQty).isEqualByComparingTo("23.014")  // 600 × 14 ÷ 365
    }

    @Test
    fun `수입도 국산과 같이 센다`() {
        val id = plantVariety("03")
        plant(
            row(id, LocalDate.of(2099, 6, 25), qty = "10"),
            row(id, LocalDate.of(2099, 6, 26), qty = "30", plorCd = "800CL"),
        )

        assertThat(resultOf(id).recentQty).isEqualByComparingTo("40")
    }

    @Test
    fun `최근 14일에 거래가 없는 품종은 빠진다`() {
        val id = plantVariety("04")
        plant(row(id, LocalDate.of(2099, 1, 10), qty = "100"))

        assertThat(recentVolumeRepository.findAll(end).map { it.varietyId }).doesNotContain(id)
    }
}
