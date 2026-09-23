package com.pomona.price.domain

import com.pomona.price.model.MonthlyVolume
import com.pomona.price.model.Origin
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
import java.time.YearMonth

/** 로컬 DB 에 실데이터(2026년)가 있으므로 겹치지 않게 2099년 날짜만 쓴다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(VarietyUpsertRepository::class, WholesaleDailyWriteRepository::class, MonthlyVolumeRepository::class)
class MonthlyVolumeRepositoryTest {

    @Autowired private lateinit var varieties: VarietyUpsertRepository
    @Autowired private lateinit var wholesale: WholesaleDailyWriteRepository
    @Autowired private lateinit var volumes: MonthlyVolumeRepository

    private val garak = "110001"
    private val domesticOrigin = "367000"   // 충북 괴산군
    private val importOrigin = "800CL"      // 칠레

    private fun plantVariety(sclsfCd: String) =
        varieties.upsert(VarietyUpsert("ZZ", "시험대분류", "ZZ", "시험중분류", sclsfCd, "시험품종$sclsfCd"))

    private fun row(varietyId: Long, date: LocalDate, plorCd: String, qty: String) = WholesaleDailyRow(
        trdClclnYmd = date, whslMrktCd = garak, varietyId = varietyId,
        trdSe = "경매", grdCd = "11", grdNm = "특", plorCd = plorCd, plorNm = null, unitNm = "kg",
        totPrc = 1_000, totQty = BigDecimal(qty),
        lowPrcPerKg = BigDecimal.ONE, highPrcPerKg = BigDecimal.ONE, tradeCount = 1,
    )

    /** 날짜별로 묶어 그 날짜를 채운다. 쓰기 레포가 날짜 단위로 교체하기 때문이다. */
    private fun plant(vararg rows: WholesaleDailyRow) {
        rows.groupBy { it.trdClclnYmd }.forEach { (date, sameDay) -> wholesale.replaceDay(date, garak, sameDay) }
    }

    private fun volume(varietyId: Long, month: String, origin: Origin, qty: String) =
        MonthlyVolume(varietyId, YearMonth.parse(month), origin, BigDecimal(qty))

    @Test
    fun `품종 하나의 월별 물량을 국산과 수입으로 나눠 준다`() {
        val id = plantVariety("01")
        plant(
            row(id, LocalDate.of(2099, 1, 5), domesticOrigin, "100.000"),
            row(id, LocalDate.of(2099, 1, 6), domesticOrigin, "50.500"),
            row(id, LocalDate.of(2099, 1, 6), importOrigin, "30.000"),
            row(id, LocalDate.of(2099, 2, 3), domesticOrigin, "70.000"),
        )

        val result = volumes.findByVariety(id, YearMonth.of(2099, 1), YearMonth.of(2099, 2))

        // 물량 컬럼이 numeric(14,3) 이라 합계도 소수 3자리로 온다
        assertThat(result).containsExactly(
            volume(id, "2099-01", Origin.DOMESTIC, "150.500"),
            volume(id, "2099-01", Origin.IMPORT, "30.000"),
            volume(id, "2099-02", Origin.DOMESTIC, "70.000"),
        )
    }

    @Test
    fun `조회 기간 밖의 달은 들어가지 않는다`() {
        val id = plantVariety("01")
        plant(
            row(id, LocalDate.of(2099, 1, 5), domesticOrigin, "100.000"),
            row(id, LocalDate.of(2099, 2, 3), domesticOrigin, "70.000"),
        )

        val result = volumes.findByVariety(id, YearMonth.of(2099, 1), YearMonth.of(2099, 1))

        assertThat(result.map { it.month }).containsOnly(YearMonth.of(2099, 1))
    }

    @Test
    fun `월말과 다음 달 첫날은 다른 달로 센다`() {
        val id = plantVariety("01")
        plant(
            row(id, LocalDate.of(2099, 1, 31), domesticOrigin, "10.000"),
            row(id, LocalDate.of(2099, 2, 1), domesticOrigin, "20.000"),
        )

        val result = volumes.findByVariety(id, YearMonth.of(2099, 1), YearMonth.of(2099, 2))

        assertThat(result.map { it.month to it.qty.toInt() })
            .containsExactly(YearMonth.of(2099, 1) to 10, YearMonth.of(2099, 2) to 20)
    }

    @Test
    fun `한 달의 품종별 물량을 많은 순으로 주고 국산과 수입은 따로 줄을 세운다`() {
        val a = plantVariety("01")
        val b = plantVariety("02")
        plant(
            row(a, LocalDate.of(2099, 1, 5), domesticOrigin, "150.000"),
            row(a, LocalDate.of(2099, 1, 5), importOrigin, "30.000"),
            row(b, LocalDate.of(2099, 1, 6), domesticOrigin, "200.000"),
        )

        val result = volumes.findByMonth(YearMonth.of(2099, 1))

        assertThat(result.map { Triple(it.varietyId, it.origin, it.qty.toInt()) }).containsExactly(
            Triple(b, Origin.DOMESTIC, 200),
            Triple(a, Origin.DOMESTIC, 150),
            Triple(a, Origin.IMPORT, 30),
        )
    }

    @Test
    fun `그 달의 시장 거래일 수를 센다`() {
        // 이번 달처럼 덜 찬 달을 화면이 알아보게 하려고 준다. 품종·원산지와 상관없는 시장 전체 값이다.
        val a = plantVariety("01")
        val b = plantVariety("02")
        plant(
            row(a, LocalDate.of(2099, 1, 5), domesticOrigin, "1.000"),
            row(b, LocalDate.of(2099, 1, 5), domesticOrigin, "1.000"),
            row(a, LocalDate.of(2099, 1, 6), importOrigin, "1.000"),
        )

        assertThat(volumes.countTradingDays(YearMonth.of(2099, 1))).isEqualTo(2)
    }
}
