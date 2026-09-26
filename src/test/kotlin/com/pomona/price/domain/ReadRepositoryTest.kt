package com.pomona.price.domain

import com.pomona.price.model.WholesaleDailyRow
import com.pomona.variety.domain.VarietyRepository
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

/** JdbcTemplate 으로 쓴 것을 JPA 로 읽는다. 두 경로가 같은 테이블을 보는지 확인한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(VarietyUpsertRepository::class, WholesaleDailyWriteRepository::class)
class ReadRepositoryTest {

    @Autowired private lateinit var varietyUpsertRepository: VarietyUpsertRepository
    @Autowired private lateinit var wholesaleDailyWriteRepository: WholesaleDailyWriteRepository
    @Autowired private lateinit var varietyRepository: VarietyRepository
    @Autowired private lateinit var wholesaleDailyRepository: WholesaleDailyRepository

    private fun plantVariety() = varietyUpsertRepository.upsert(
        VarietyUpsert("ZZ", "시험대분류", "ZZ", "시험중분류", "01", "시험홍로"))

    private fun plantWholesale(varietyId: Long, date: LocalDate, qty: String) =
        wholesaleDailyWriteRepository.replaceDay(date, "110001", listOf(WholesaleDailyRow(
            trdClclnYmd = date, whslMrktCd = "110001", varietyId = varietyId,
            trdSe = "경매", grdCd = "11", grdNm = "특",
            plorCd = "367000", plorNm = "충청북도 괴산군", unitNm = "kg",
            totPrc = 62_058_000, totQty = BigDecimal(qty),
            lowPrcPerKg = BigDecimal("1800.00"), highPrcPerKg = BigDecimal("3800.00"),
            tradeCount = 17,
        )))

    @Test
    fun `자연키로 품종을 찾는다`() {
        plantVariety()

        val found = varietyRepository.findByLclsfCdAndMclsfCdAndSclsfCd("ZZ", "ZZ", "01")

        assertThat(found).isNotNull()
        assertThat(found!!.sclsfNm).isEqualTo("시험홍로")
    }

    @Test
    fun `대표가는 저장값이 아니라 계산으로 나온다`() {
        val varietyId = plantVariety()
        plantWholesale(varietyId, LocalDate.of(2099, 1, 4), "13005.000")

        val row = wholesaleDailyRepository.findByTrdClclnYmd(LocalDate.of(2099, 1, 4)).first()

        // 62,058,000원 ÷ 13,005kg = 4,771.86원/kg (실제 2025-09-22 괴산군 홍로 특급 값)
        assertThat(row.representativePricePerKg).isEqualByComparingTo(BigDecimal("4771.86"))
        assertThat(row.tradeCount).isEqualTo(17)
    }

    @Test
    fun `품종별 기간 조회가 날짜순으로 나온다`() {
        val varietyId = plantVariety()
        listOf(3, 1, 2).forEach { plantWholesale(varietyId, LocalDate.of(2099, 1, it), "100.000") }

        val rows = wholesaleDailyRepository.findByVarietyIdAndTrdClclnYmdBetweenOrderByTrdClclnYmd(
            varietyId, LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 31))

        assertThat(rows.map { it.trdClclnYmd.dayOfMonth }).containsExactly(1, 2, 3)
    }

    @Test
    fun `첫 등장일과 마지막 등장일을 컬럼 없이 뽑는다`() {
        val varietyId = plantVariety()
        listOf(4, 20, 11).forEach { plantWholesale(varietyId, LocalDate.of(2099, 1, it), "100.000") }

        val tradedDateRange = varietyRepository.findTradedDateRange(varietyId)

        assertThat(tradedDateRange.firstTradedOn).isEqualTo(LocalDate.of(2099, 1, 4))
        assertThat(tradedDateRange.lastTradedOn).isEqualTo(LocalDate.of(2099, 1, 20))
    }

    @Test
    fun `지연 로딩한 품종을 타고 이름까지 간다`() {
        val varietyId = plantVariety()
        plantWholesale(varietyId, LocalDate.of(2099, 1, 4), "100.000")

        val row = wholesaleDailyRepository.findByTrdClclnYmd(LocalDate.of(2099, 1, 4)).first()

        assertThat(row.variety.sclsfNm).isEqualTo("시험홍로")
    }
}
