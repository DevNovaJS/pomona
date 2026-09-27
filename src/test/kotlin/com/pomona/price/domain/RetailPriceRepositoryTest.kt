package com.pomona.price.domain

import com.pomona.price.model.RetailDailyRow
import com.pomona.price.model.RetailPrice
import com.pomona.variety.domain.RetailVarietyRepository
import com.pomona.variety.domain.RetailVarietyUpsertRepository
import com.pomona.variety.domain.VarietyRepository
import com.pomona.variety.domain.VarietyRetailMapping
import com.pomona.variety.domain.VarietyRetailMappingRepository
import com.pomona.variety.domain.VarietyUpsertRepository
import com.pomona.variety.model.RetailVarietyUpsert
import com.pomona.variety.model.VarietyUpsert
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

/**
 * 로컬 DB 에 실데이터(2026년)가 있으므로 2099년 날짜만 쓴다. 7일은 소매 데이터 전체의 마지막 조사일부터 세므로
 * 2099-01-10 에 조사를 하나 넣으면 7일 구간이 2099-01-04 ~ 01-10 이 된다. 테스트가 끝나면 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(
    RetailDailyWriteRepository::class, RetailVarietyUpsertRepository::class,
    VarietyUpsertRepository::class, RetailPriceRepository::class,
)
class RetailPriceRepositoryTest {

    @Autowired private lateinit var retailDailyWriteRepository: RetailDailyWriteRepository
    @Autowired private lateinit var retailVarietyUpsertRepository: RetailVarietyUpsertRepository
    @Autowired private lateinit var retailVarietyRepository: RetailVarietyRepository
    @Autowired private lateinit var varietyUpsertRepository: VarietyUpsertRepository
    @Autowired private lateinit var varietyRepository: VarietyRepository
    @Autowired private lateinit var varietyRetailMappingRepository: VarietyRetailMappingRepository
    @Autowired private lateinit var retailPriceRepository: RetailPriceRepository

    private val ctgryCd = "999"

    /** 소매 품종 [itemCd] 를 만들고, 정산 품종 하나를 만들어 거기에 매핑한다. 정산 품종 id 를 돌려준다. */
    private fun mappedVariety(itemCd: String, sclsfCd: String = itemCd.takeLast(2)): Long {
        retailVarietyUpsertRepository.upsertAll(listOf(RetailVarietyUpsert(ctgryCd, "시험부류", itemCd, "시험품목$itemCd", "01", "시험품종")))
        val retailVariety = retailVarietyRepository.findAll().single { it.ctgryCd == ctgryCd && it.itemCd == itemCd }
        val varietyId = varietyUpsertRepository.upsert(VarietyUpsert("ZZ", "시험대분류", "01", "시험품목", sclsfCd, "시험품종$sclsfCd"))
        varietyRetailMappingRepository.save(VarietyRetailMapping(varietyRepository.getReferenceById(varietyId), retailVariety))
        return varietyId
    }

    private fun row(
        itemCd: String, day: Int, mrktCd: String, prc: Long, cnvsPrc: Long = prc,
        unit: String = "개", unitSz: String = "10", grdCd: String = "04", grdNm: String = "상품",
    ) = RetailDailyRow(
        exmnYmd = LocalDate.of(2099, 1, day), seCd = "01", seNm = "소매",
        ctgryCd = ctgryCd, ctgryNm = "시험부류", itemCd = itemCd, itemNm = "시험품목$itemCd",
        vrtyCd = "01", vrtyNm = "시험품종", grdCd = grdCd, grdNm = grdNm,
        sggCd = "9999", sggNm = "시험시", mrktCd = mrktCd, mrktNm = "점포$mrktCd",
        unit = unit, unitSz = unitSz, exmnDdPrc = prc, exmnDdCnvsPrc = cnvsPrc, orgnlRegDt = null,
    )

    /** 품목별로 묶어 1월 한 달을 채운다. 쓰기 레포가 품목 × 기간 단위로 지우고 넣는다. */
    private fun plant(vararg rows: RetailDailyRow) {
        rows.groupBy { it.itemCd }.forEach { (itemCd, sameItem) ->
            retailDailyWriteRepository.replaceRange("01", ctgryCd, itemCd, LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 31), sameItem)
        }
    }

    private fun priceOf(varietyId: Long): RetailPrice = retailPriceRepository.findAll().single { it.varietyId == varietyId }

    @Test
    fun `무게 단위로 조사된 품목은 kg당 환산가로 나온다`() {
        val banana = mappedVariety("901")
        val grape = mappedVariety("902")
        plant(
            row("901", day = 10, mrktCd = "0000001", prc = 330, cnvsPrc = 3_300, unit = "g", unitSz = "100"),
            row("902", day = 10, mrktCd = "0000001", prc = 36_000, cnvsPrc = 18_000, unit = "kg", unitSz = "2"),
        )

        assertThat(priceOf(banana).let { Triple(it.unit, it.unitSize, it.grades.single().price) }).isEqualTo(Triple("kg", 1, 3_300L))
        assertThat(priceOf(grape).let { Triple(it.unit, it.unitSize, it.grades.single().price) }).isEqualTo(Triple("kg", 1, 18_000L))
    }

    @Test
    fun `개수 단위로 조사된 품목은 조사 단위 그대로 나온다`() {
        val apple = mappedVariety("903")
        plant(row("903", day = 10, mrktCd = "0000001", prc = 22_600, unit = "개", unitSz = "10"))

        val price = priceOf(apple)

        assertThat(Triple(price.unit, price.unitSize, price.grades.single().price)).isEqualTo(Triple("개", 10, 22_600L))
    }

    @Test
    fun `자주 조사된 점포가 표를 더 가지지 않는다`() {
        val pear = mappedVariety("904")
        plant(
            // A 점포는 5일 모두 10,000원, B·C 점포는 하루씩 30,000원·20,000원
            *(6..10).map { row("904", day = it, mrktCd = "000000A", prc = 10_000) }.toTypedArray(),
            row("904", day = 10, mrktCd = "000000B", prc = 30_000),
            row("904", day = 10, mrktCd = "000000C", prc = 20_000),
        )

        val grade = priceOf(pear).grades.single()

        // 조사값 7개를 한 통에 넣으면 10,000원이 중앙값이 된다. 점포마다 먼저 줄이면 [10,000, 20,000, 30,000] 의 가운데
        assertThat(grade.price).isEqualTo(20_000L)
        assertThat(grade.storeCount).isEqualTo(3)
    }

    @Test
    fun `마지막 조사일 포함 7일째까지만 쓴다`() {
        val kiwi = mappedVariety("905")
        plant(
            row("905", day = 3, mrktCd = "000000A", prc = 90_000),   // 8일째 — 빠진다
            row("905", day = 4, mrktCd = "000000B", prc = 10_000),   // 7일째
            row("905", day = 10, mrktCd = "000000C", prc = 10_000),  // 마지막 조사일
        )

        val price = priceOf(kiwi)

        assertThat(price.from to price.to).isEqualTo(LocalDate.of(2099, 1, 4) to LocalDate.of(2099, 1, 10))
        assertThat(price.grades.single().storeCount).isEqualTo(2)
        assertThat(price.grades.single().price).isEqualTo(10_000L)
    }

    @Test
    fun `등급마다 따로 나온다`() {
        val peach = mappedVariety("906")
        plant(
            row("906", day = 10, mrktCd = "0000001", prc = 20_000, grdCd = "04", grdNm = "상품"),
            row("906", day = 10, mrktCd = "0000001", prc = 15_000, grdCd = "05", grdNm = "중품"),
        )

        assertThat(priceOf(peach).grades.map { Triple(it.grdCd, it.grdNm, it.price) })
            .containsExactly(Triple("04", "상품", 20_000L), Triple("05", "중품", 15_000L))
    }

    @Test
    fun `소매에 없음으로 매핑했거나 매핑이 없는 품종은 빠진다`() {
        val mapped = mappedVariety("907")
        val noRetail = varietyUpsertRepository.upsert(VarietyUpsert("ZZ", "시험대분류", "01", "시험품목", "71", "소매없음"))
        varietyRetailMappingRepository.save(VarietyRetailMapping(varietyRepository.getReferenceById(noRetail), null))
        val unmapped = varietyUpsertRepository.upsert(VarietyUpsert("ZZ", "시험대분류", "01", "시험품목", "72", "미매핑"))
        plant(row("907", day = 10, mrktCd = "0000001", prc = 10_000))

        val varietyIds = retailPriceRepository.findAll().map { it.varietyId }

        assertThat(varietyIds).contains(mapped).doesNotContain(noRetail, unmapped)
    }

    @Test
    fun `같은 소매 품종에 매핑된 정산 품종들은 같은 값을 받는다`() {
        val fuji = mappedVariety("908", sclsfCd = "81")
        val royalFuji = varietyUpsertRepository.upsert(VarietyUpsert("ZZ", "시험대분류", "01", "시험품목", "82", "로얄후지"))
        val retailFuji = retailVarietyRepository.findAll().single { it.ctgryCd == ctgryCd && it.itemCd == "908" }
        varietyRetailMappingRepository.save(VarietyRetailMapping(varietyRepository.getReferenceById(royalFuji), retailFuji))
        plant(row("908", day = 10, mrktCd = "0000001", prc = 30_000))

        assertThat(priceOf(fuji).grades).isEqualTo(priceOf(royalFuji).grades)
    }
}
