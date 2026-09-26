package com.pomona.review.domain

import com.pomona.price.domain.WholesaleDailyWriteRepository
import com.pomona.price.model.WholesaleDailyRow
import com.pomona.variety.domain.VarietyRepository
import com.pomona.variety.domain.VarietyUpsertRepository
import com.pomona.variety.model.VarietyUpsert
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
@Import(VarietyUpsertRepository::class, WholesaleDailyWriteRepository::class, ReviewMarketPriceRepository::class)
class ReviewMarketPriceRepositoryTest {

    @Autowired private lateinit var varietyUpsertRepository: VarietyUpsertRepository
    @Autowired private lateinit var varietyRepository: VarietyRepository
    @Autowired private lateinit var wholesaleDailyWriteRepository: WholesaleDailyWriteRepository
    @Autowired private lateinit var reviewRepository: ReviewRepository
    @Autowired private lateinit var reviewMarketPriceRepository: ReviewMarketPriceRepository

    private val garak = "110001"
    private var varietyId = 0L

    /** 먹은 날. 7일 구간은 2099-01-04 ~ 01-10 */
    private val eatenDate = LocalDate.of(2099, 1, 10)

    @BeforeEach
    fun plantVariety() {
        varietyId = varietyUpsertRepository.upsert(VarietyUpsert("ZZ", "시험대분류", "01", "시험품목", "01", "시험품종"))
    }

    private fun row(day: Int, totPrc: Long, qty: String, grdCd: String = "11", grdNm: String = "특") = WholesaleDailyRow(
        trdClclnYmd = LocalDate.of(2099, 1, day), whslMrktCd = garak, varietyId = varietyId,
        trdSe = "경매", grdCd = grdCd, grdNm = grdNm, plorCd = "367000", plorNm = null, unitNm = "kg",
        totPrc = totPrc, totQty = BigDecimal(qty),
        lowPrcPerKg = BigDecimal.ONE, highPrcPerKg = BigDecimal.ONE, tradeCount = 1,
    )

    /** 날짜별로 묶어 그 날짜를 채운다. 쓰기 레포가 날짜 단위로 교체하기 때문이다. */
    private fun plant(vararg rows: WholesaleDailyRow) {
        rows.groupBy { it.trdClclnYmd }.forEach { (date, sameDay) -> wholesaleDailyWriteRepository.replaceDay(date, garak, sameDay) }
    }

    private fun tradeDate() = reviewMarketPriceRepository.find(varietyId, eatenDate)?.date

    @Test
    fun `먹은 날에 거래가 있으면 그날 값을 쓴다`() {
        plant(row(9, totPrc = 1_000, qty = "1"), row(10, totPrc = 2_000, qty = "1"))

        val marketPrice = reviewMarketPriceRepository.find(varietyId, eatenDate)!!

        assertThat(marketPrice.date).isEqualTo(eatenDate)
        assertThat(marketPrice.perKg).isEqualByComparingTo("2000")
    }

    @Test
    fun `먹은 날에 경매가 없으면 그 전 마지막 거래일 값을 쓴다`() {
        plant(row(7, totPrc = 1_000, qty = "1"), row(8, totPrc = 1_000, qty = "1"))

        assertThat(tradeDate()).isEqualTo(LocalDate.of(2099, 1, 8))
    }

    @Test
    fun `먹은 날 뒤의 거래는 쓰지 않는다`() {
        plant(row(9, totPrc = 1_000, qty = "1"), row(11, totPrc = 1_000, qty = "1"))

        assertThat(tradeDate()).isEqualTo(LocalDate.of(2099, 1, 9))
    }

    @Test
    fun `먹은 날 포함 7일째까지는 거슬러 올라간다`() {
        plant(row(4, totPrc = 1_000, qty = "1"))

        assertThat(tradeDate()).isEqualTo(LocalDate.of(2099, 1, 4))
    }

    @Test
    fun `7일 안에 거래가 없으면 시세가 없다`() {
        plant(row(3, totPrc = 1_000, qty = "1"))

        assertThat(tradeDate()).isNull()
    }

    @Test
    fun `등급을 합친 대표가와 등급별 대표가를 같이 준다`() {
        plant(
            row(10, totPrc = 10_000, qty = "10", grdCd = "11", grdNm = "특"),
            row(10, totPrc = 3_000, qty = "5", grdCd = "12", grdNm = "상"),
        )

        val marketPrice = reviewMarketPriceRepository.find(varietyId, eatenDate)!!

        // 13,000 ÷ 15. 등급별 값의 평균(800)이 아니다
        assertThat(marketPrice.perKg).isEqualByComparingTo("866.67")
        assertThat(marketPrice.grades.map { it.grdNm to it.perKg.toInt() }).containsExactly("특" to 1000, "상" to 600)
    }

    @Test
    fun `리뷰 전부의 시세를 리뷰 id 로 모아 주고 시세가 없는 리뷰는 빠진다`() {
        plant(row(10, totPrc = 2_000, qty = "1"))
        val variety = varietyRepository.getReferenceById(varietyId)
        fun review(eaten: LocalDate) = reviewRepository.save(
            Review(variety, eaten, "제목", "마트", null, 1_000, null, 3, "본문"),
        )
        val withPrice = review(eatenDate)
        val tooLate = review(LocalDate.of(2099, 1, 30))

        val marketPrices = reviewMarketPriceRepository.findForReviews()

        assertThat(marketPrices[withPrice.id]?.date).isEqualTo(eatenDate)
        assertThat(marketPrices).doesNotContainKey(tooLate.id)
    }
}
