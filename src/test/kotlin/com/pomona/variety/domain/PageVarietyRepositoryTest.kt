package com.pomona.variety.domain

import com.pomona.price.domain.WholesaleDailyWriteRepository
import com.pomona.price.model.WholesaleDailyRow
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
@Import(VarietyUpsertRepository::class, WholesaleDailyWriteRepository::class, PageVarietyRepository::class)
class PageVarietyRepositoryTest {

    @Autowired private lateinit var varieties: VarietyUpsertRepository
    @Autowired private lateinit var wholesale: WholesaleDailyWriteRepository
    @Autowired private lateinit var pages: PageVarietyRepository

    private val garak = "110001"

    /** 최근 12개월은 2099-01-01 ~ 2099-12-31 */
    private val end = LocalDate.of(2099, 12, 31)
    private val firstDay = LocalDate.of(2099, 1, 1)

    private fun plantVariety(mclsfCd: String, sclsfCd: String) =
        varieties.upsert(VarietyUpsert("ZZ", "시험대분류", mclsfCd, "시험중분류$mclsfCd", sclsfCd, "시험품종$sclsfCd"))

    private fun row(varietyId: Long, date: LocalDate, qty: String) = WholesaleDailyRow(
        trdClclnYmd = date, whslMrktCd = garak, varietyId = varietyId,
        trdSe = "경매", grdCd = "11", grdNm = "특", plorCd = "367000", plorNm = null, unitNm = "kg",
        totPrc = 1_000, totQty = BigDecimal(qty),
        lowPrcPerKg = BigDecimal.ONE, highPrcPerKg = BigDecimal.ONE, tradeCount = 1,
    )

    /** [from] 부터 하루씩 [days] 일 동안 매일 [qty] kg 씩 거래한 행 */
    private fun daily(varietyId: Long, days: Int, qty: String, from: LocalDate = firstDay) =
        (0 until days).map { row(varietyId, from.plusDays(it.toLong()), qty) }

    /** 날짜별로 묶어 그 날짜를 채운다. 쓰기 레포가 날짜 단위로 교체하므로 품종이 여럿이면 한 번에 넣어야 한다. */
    private fun plant(rows: List<WholesaleDailyRow>) {
        rows.groupBy { it.trdClclnYmd }.forEach { (date, sameDay) -> wholesale.replaceDay(date, garak, sameDay) }
    }

    private fun pageIds() = pages.findAll(end).map { it.id }

    @Test
    fun `거래일 10일 물량 1톤이면 들어간다`() {
        val id = plantVariety("01", "01")
        plant(daily(id, days = 10, qty = "100"))

        assertThat(pageIds()).containsExactly(id)
    }

    @Test
    fun `거래일이 9일이면 물량이 많아도 빠진다`() {
        val id = plantVariety("01", "01")
        plant(daily(id, days = 9, qty = "5000"))

        assertThat(pageIds()).isEmpty()
    }

    @Test
    fun `물량이 1톤 미만이면 거래일이 많아도 빠진다`() {
        val id = plantVariety("01", "01")
        plant(daily(id, days = 30, qty = "33.3"))  // 999kg

        assertThat(pageIds()).isEmpty()
    }

    @Test
    fun `소분류 99 와 중분류 99 는 기타라 빠진다`() {
        val otherInItem = plantVariety("01", "99")
        val otherItem = plantVariety("99", "98")
        plant(daily(otherInItem, days = 10, qty = "100") + daily(otherItem, days = 10, qty = "100"))

        assertThat(pageIds()).isEmpty()
    }

    @Test
    fun `12개월 전 같은 날의 거래는 세지 않는다`() {
        val inside = plantVariety("01", "01")
        val outside = plantVariety("01", "02")
        plant(
            daily(inside, days = 9, qty = "200") + row(inside, firstDay.plusDays(9), "200") +
                daily(outside, days = 9, qty = "200") + row(outside, LocalDate.of(2098, 12, 31), "200"),
        )

        assertThat(pageIds()).containsExactly(inside)
    }

    @Test
    fun `품목 순으로 주고 품목 안에서는 물량이 많은 순이다`() {
        val appleSmall = plantVariety("01", "01")
        val appleBig = plantVariety("01", "02")
        val pear = plantVariety("02", "01")
        plant(
            daily(pear, days = 10, qty = "900") +
                daily(appleSmall, days = 10, qty = "100") +
                daily(appleBig, days = 10, qty = "500"),
        )

        assertThat(pageIds()).containsExactly(appleBig, appleSmall, pear)
    }
}
