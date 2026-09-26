package com.pomona.price.domain

import com.pomona.price.model.ItemMonthlyVolume
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

    @Autowired private lateinit var varietyUpsertRepository: VarietyUpsertRepository
    @Autowired private lateinit var wholesaleDailyWriteRepository: WholesaleDailyWriteRepository
    @Autowired private lateinit var monthlyVolumeRepository: MonthlyVolumeRepository

    private val garak = "110001"
    private val domesticOrigin = "367000"   // 충북 괴산군
    private val importOrigin = "800CL"      // 칠레

    private val jan = YearMonth.of(2099, 1)
    private val feb = YearMonth.of(2099, 2)

    private fun plantVariety(mclsfCd: String, sclsfCd: String) =
        varietyUpsertRepository.upsert(VarietyUpsert("ZZ", "시험대분류", mclsfCd, "시험중분류$mclsfCd", sclsfCd, "시험품종$sclsfCd"))

    private fun row(varietyId: Long, date: LocalDate, plorCd: String, qty: String) = WholesaleDailyRow(
        trdClclnYmd = date, whslMrktCd = garak, varietyId = varietyId,
        trdSe = "경매", grdCd = "11", grdNm = "특", plorCd = plorCd, plorNm = null, unitNm = "kg",
        totPrc = 1_000, totQty = BigDecimal(qty),
        lowPrcPerKg = BigDecimal.ONE, highPrcPerKg = BigDecimal.ONE, tradeCount = 1,
    )

    /** 날짜별로 묶어 그 날짜를 채운다. 쓰기 레포가 날짜 단위로 교체하므로 품종이 여럿이면 한 번에 넣어야 한다. */
    private fun plant(vararg rows: WholesaleDailyRow) {
        rows.groupBy { it.trdClclnYmd }.forEach { (date, sameDay) -> wholesaleDailyWriteRepository.replaceDay(date, garak, sameDay) }
    }

    private fun volume(varietyId: Long, month: YearMonth, origin: Origin, qty: String) =
        MonthlyVolume(varietyId, month, origin, BigDecimal(qty))

    private fun itemVolume(mclsfCd: String, month: YearMonth, origin: Origin, qty: String) =
        ItemMonthlyVolume("ZZ", mclsfCd, month, origin, BigDecimal(qty))

    @Test
    fun `품종 전부의 월별 물량을 국산과 수입으로 나눠 달 순 물량 많은 순으로 준다`() {
        val a = plantVariety("01", "01")
        val b = plantVariety("01", "02")
        plant(
            row(a, LocalDate.of(2099, 1, 5), domesticOrigin, "100.000"),
            row(a, LocalDate.of(2099, 1, 6), domesticOrigin, "50.500"),
            row(a, LocalDate.of(2099, 1, 6), importOrigin, "30.000"),
            row(b, LocalDate.of(2099, 1, 7), domesticOrigin, "200.000"),
            row(a, LocalDate.of(2099, 2, 3), domesticOrigin, "70.000"),
        )

        val result = monthlyVolumeRepository.findAll(jan, feb)

        // 물량 컬럼이 numeric(14,3) 이라 합계도 소수 3자리로 온다
        assertThat(result).containsExactly(
            volume(b, jan, Origin.DOMESTIC, "200.000"),
            volume(a, jan, Origin.DOMESTIC, "150.500"),
            volume(a, jan, Origin.IMPORT, "30.000"),
            volume(a, feb, Origin.DOMESTIC, "70.000"),
        )
    }

    @Test
    fun `조회 기간 밖의 달은 들어가지 않는다`() {
        val id = plantVariety("01", "01")
        plant(
            row(id, LocalDate.of(2099, 1, 5), domesticOrigin, "100.000"),
            row(id, LocalDate.of(2099, 2, 3), domesticOrigin, "70.000"),
        )

        assertThat(monthlyVolumeRepository.findAll(jan, jan).map { it.month }).containsOnly(jan)
    }

    @Test
    fun `월말과 다음 달 첫날은 다른 달로 센다`() {
        val id = plantVariety("01", "01")
        plant(
            row(id, LocalDate.of(2099, 1, 31), domesticOrigin, "10.000"),
            row(id, LocalDate.of(2099, 2, 1), domesticOrigin, "20.000"),
        )

        assertThat(monthlyVolumeRepository.findAll(jan, feb).map { it.month to it.qty.toInt() })
            .containsExactly(jan to 10, feb to 20)
    }

    @Test
    fun `품목 물량은 기타 품종까지 품목 안의 품종을 전부 합친다`() {
        val fuji = plantVariety("01", "01")
        val other = plantVariety("01", "99")
        val pear = plantVariety("02", "01")
        plant(
            row(fuji, LocalDate.of(2099, 1, 5), domesticOrigin, "100.000"),
            row(other, LocalDate.of(2099, 1, 5), domesticOrigin, "5.000"),
            row(fuji, LocalDate.of(2099, 1, 6), importOrigin, "30.000"),
            row(pear, LocalDate.of(2099, 1, 6), domesticOrigin, "40.000"),
            row(fuji, LocalDate.of(2099, 2, 3), domesticOrigin, "70.000"),
        )

        assertThat(monthlyVolumeRepository.findItems(jan, feb)).containsExactly(
            itemVolume("01", jan, Origin.DOMESTIC, "105.000"),
            itemVolume("01", jan, Origin.IMPORT, "30.000"),
            itemVolume("01", feb, Origin.DOMESTIC, "70.000"),
            itemVolume("02", jan, Origin.DOMESTIC, "40.000"),
        )
    }

    @Test
    fun `기타 품목은 품목 물량에서 빠진다`() {
        val otherItem = plantVariety("99", "98")
        plant(row(otherItem, LocalDate.of(2099, 1, 5), importOrigin, "100.000"))

        assertThat(monthlyVolumeRepository.findItems(jan, jan)).isEmpty()
    }

    @Test
    fun `달마다 시장 거래일 수를 센다`() {
        // 품종·원산지와 상관없는 시장 전체 값이다
        val a = plantVariety("01", "01")
        val b = plantVariety("01", "02")
        plant(
            row(a, LocalDate.of(2099, 1, 5), domesticOrigin, "1.000"),
            row(b, LocalDate.of(2099, 1, 5), domesticOrigin, "1.000"),
            row(a, LocalDate.of(2099, 1, 6), importOrigin, "1.000"),
            row(a, LocalDate.of(2099, 3, 2), domesticOrigin, "1.000"),
        )

        // 거래가 없는 2월은 들어가지 않는다
        assertThat(monthlyVolumeRepository.countTradingDays(jan, YearMonth.of(2099, 3)))
            .containsExactlyInAnyOrderEntriesOf(mapOf(jan to 2, YearMonth.of(2099, 3) to 1))
    }
}
