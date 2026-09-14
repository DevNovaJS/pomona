package com.pomona.price.domain

import com.pomona.price.model.WholesaleDailyUpsert
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
@Import(VarietyUpsertRepository::class, WholesaleDailyUpsertRepository::class)
class ReadRepositoryTest {

    @Autowired private lateinit var varietyUpsert: VarietyUpsertRepository
    @Autowired private lateinit var wholesaleUpsert: WholesaleDailyUpsertRepository
    @Autowired private lateinit var varieties: VarietyRepository
    @Autowired private lateinit var wholesale: WholesaleDailyRepository

    private fun 품종심기() = varietyUpsert.upsert(
        VarietyUpsert("ZZ", "시험대분류", "ZZ", "시험중분류", "01", "시험홍로"))

    private fun 도매심기(varietyId: Long, date: LocalDate, qty: String) =
        wholesaleUpsert.upsertAll(listOf(WholesaleDailyUpsert(
            trdClclnYmd = date, whslMrktCd = "110001", varietyId = varietyId,
            trdSe = "경매", grdCd = "11", grdNm = "특",
            plorCd = "367000", plorNm = "충청북도 괴산군", unitNm = "kg",
            totPrc = 62_058_000, totQty = BigDecimal(qty),
            lowPrcPerKg = BigDecimal("1800.00"), highPrcPerKg = BigDecimal("3800.00"),
            tradeCount = 17,
        )))

    @Test
    fun `자연키로 품종을 찾는다`() {
        품종심기()

        val found = varieties.findByLclsfCdAndMclsfCdAndSclsfCd("ZZ", "ZZ", "01")

        assertThat(found).isNotNull()
        assertThat(found!!.sclsfNm).isEqualTo("시험홍로")
    }

    @Test
    fun `대표가는 저장값이 아니라 계산으로 나온다`() {
        val varietyId = 품종심기()
        도매심기(varietyId, LocalDate.of(2099, 1, 4), "13005.000")

        val row = wholesale.findByTrdClclnYmd(LocalDate.of(2099, 1, 4)).first()

        // 62,058,000원 ÷ 13,005kg = 4,771.86원/kg (실제 2025-09-22 괴산군 홍로 특급 값)
        assertThat(row.representativePricePerKg).isEqualByComparingTo(BigDecimal("4771.86"))
        assertThat(row.tradeCount).isEqualTo(17)
    }

    @Test
    fun `품종별 기간 조회가 날짜순으로 나온다`() {
        val varietyId = 품종심기()
        listOf(3, 1, 2).forEach { 도매심기(varietyId, LocalDate.of(2099, 1, it), "100.000") }

        val rows = wholesale.findByVarietyIdAndTrdClclnYmdBetweenOrderByTrdClclnYmd(
            varietyId, LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 31))

        assertThat(rows.map { it.trdClclnYmd.dayOfMonth }).containsExactly(1, 2, 3)
    }

    @Test
    fun `첫 등장일과 마지막 등장일을 컬럼 없이 뽑는다`() {
        val varietyId = 품종심기()
        listOf(4, 20, 11).forEach { 도매심기(varietyId, LocalDate.of(2099, 1, it), "100.000") }

        val range = varieties.findTradedDateRange(varietyId)

        assertThat(range.firstTradedOn).isEqualTo(LocalDate.of(2099, 1, 4))
        assertThat(range.lastTradedOn).isEqualTo(LocalDate.of(2099, 1, 20))
    }

    @Test
    fun `지연 로딩한 품종을 타고 이름까지 간다`() {
        val varietyId = 품종심기()
        도매심기(varietyId, LocalDate.of(2099, 1, 4), "100.000")

        val row = wholesale.findByTrdClclnYmd(LocalDate.of(2099, 1, 4)).first()

        assertThat(row.variety.sclsfNm).isEqualTo("시험홍로")
    }
}
