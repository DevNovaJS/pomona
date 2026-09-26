package com.pomona.price.domain

import com.pomona.price.model.OriginVolume
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
@Import(VarietyUpsertRepository::class, WholesaleDailyWriteRepository::class, TopOriginRepository::class)
class TopOriginRepositoryTest {

    @Autowired private lateinit var varietyUpsertRepository: VarietyUpsertRepository
    @Autowired private lateinit var wholesaleDailyWriteRepository: WholesaleDailyWriteRepository
    @Autowired private lateinit var topOriginRepository: TopOriginRepository

    private val garak = "110001"
    private val jan = YearMonth.of(2099, 1)
    private val feb = YearMonth.of(2099, 2)
    private val mar = YearMonth.of(2099, 3)

    private fun plantVariety(mclsfCd: String, sclsfCd: String) =
        varietyUpsertRepository.upsert(VarietyUpsert("ZZ", "시험대분류", mclsfCd, "시험중분류$mclsfCd", sclsfCd, "시험품종$sclsfCd"))

    private fun row(varietyId: Long, date: LocalDate, plorCd: String, qty: String) = WholesaleDailyRow(
        trdClclnYmd = date, whslMrktCd = garak, varietyId = varietyId,
        trdSe = "경매", grdCd = "11", grdNm = "특", plorCd = plorCd, plorNm = "산지$plorCd", unitNm = "kg",
        totPrc = 1_000, totQty = BigDecimal(qty),
        lowPrcPerKg = BigDecimal.ONE, highPrcPerKg = BigDecimal.ONE, tradeCount = 1,
    )

    /** 날짜별로 묶어 그 날짜를 채운다. 쓰기 레포가 날짜 단위로 교체하므로 품종이 여럿이면 한 번에 넣어야 한다. */
    private fun plant(vararg rows: WholesaleDailyRow) {
        rows.groupBy { it.trdClclnYmd }.forEach { (date, sameDay) -> wholesaleDailyWriteRepository.replaceDay(date, garak, sameDay) }
    }

    private fun List<OriginVolume>.codesAndQty() = map { it.plorCd to it.qty.toInt() }

    @Test
    fun `기간 합계는 물량이 많은 5곳만 준다`() {
        val id = plantVariety("01", "01")
        val day = LocalDate.of(2099, 1, 5)
        plant(
            row(id, day, "100001", "10"),
            row(id, day, "100002", "60"),
            row(id, day, "100003", "30"),
            row(id, day, "100004", "50"),
            row(id, day, "100005", "20"),
            row(id, day, "100006", "40"),
        )

        val result = topOriginRepository.findAll(jan, jan).single()

        assertThat(result.total.codesAndQty()).containsExactly(
            "100002" to 60, "100004" to 50, "100006" to 40, "100003" to 30, "100005" to 20,
        )
        assertThat(result.total.first().plorNm).isEqualTo("산지100002")
    }

    @Test
    fun `달마다 따로 순위를 매기고 기간 합계는 달을 합쳐 매긴다`() {
        // 1월엔 남쪽(100001), 2월엔 북쪽(100002)이 많다. 합계는 북쪽이 앞선다
        val id = plantVariety("01", "01")
        plant(
            row(id, LocalDate.of(2099, 1, 5), "100001", "50"),
            row(id, LocalDate.of(2099, 1, 5), "100002", "10"),
            row(id, LocalDate.of(2099, 2, 3), "100001", "5"),
            row(id, LocalDate.of(2099, 2, 3), "100002", "70"),
        )

        val result = topOriginRepository.findAll(jan, mar).single()

        assertThat(result.total.codesAndQty()).containsExactly("100002" to 80, "100001" to 55)
        assertThat(result.byMonth.getValue(jan).codesAndQty()).containsExactly("100001" to 50, "100002" to 10)
        assertThat(result.byMonth.getValue(feb).codesAndQty()).containsExactly("100002" to 70, "100001" to 5)
        // 거래가 없는 3월은 들어가지 않는다
        assertThat(result.byMonth.keys).containsExactly(jan, feb)
    }

    @Test
    fun `품종마다 따로 뽑는다`() {
        val a = plantVariety("01", "01")
        val b = plantVariety("01", "02")
        val day = LocalDate.of(2099, 1, 5)
        plant(row(a, day, "100001", "10"), row(b, day, "100002", "20"))

        val result = topOriginRepository.findAll(jan, jan)

        assertThat(result.map { it.varietyId to it.total.single().plorCd })
            .containsExactly(a to "100001", b to "100002")
    }

    @Test
    fun `품목 산지는 기타 품종까지 품목 안의 품종을 합쳐 뽑는다`() {
        val fuji = plantVariety("01", "01")
        val other = plantVariety("01", "99")
        val pear = plantVariety("02", "01")
        val day = LocalDate.of(2099, 1, 5)
        plant(
            row(fuji, day, "100001", "30"),
            row(other, day, "100002", "20"),
            row(fuji, day, "100002", "20"),
            row(pear, day, "100003", "5"),
        )

        val result = topOriginRepository.findItems(jan, jan)

        assertThat(result.map { it.mclsfCd }).containsExactly("01", "02")
        assertThat(result.first().total.codesAndQty()).containsExactly("100002" to 40, "100001" to 30)
    }

    @Test
    fun `기타 품목은 품목 산지에서 빠진다`() {
        val otherItem = plantVariety("99", "98")
        plant(row(otherItem, LocalDate.of(2099, 1, 5), "800CL", "10"))

        assertThat(topOriginRepository.findItems(jan, jan)).isEmpty()
    }
}
