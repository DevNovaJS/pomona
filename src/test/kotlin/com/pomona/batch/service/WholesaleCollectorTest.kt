package com.pomona.batch.service

import com.pomona.batch.domain.BatchRunRepository
import com.pomona.batch.domain.BatchStatus
import com.pomona.datago.DataGoUriFactory
import com.pomona.datago.katsale.KatSaleClient
import com.pomona.price.domain.WholesaleDailyWriteRepository
import com.pomona.price.model.WholesaleDailyRow
import com.pomona.variety.domain.VarietyUpsertRepository
import com.pomona.variety.model.VarietyUpsert
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.LocalDate

/**
 * API 는 MockRestServiceServer 로 흉내 내고(실제 응답 픽스처), 저장은 로컬 DB 에 실제로 한다.
 * 정산정보는 과실류(06)와 과일과채류(08)를 따로 호출하므로 요청이 두 번 나간다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(VarietyUpsertRepository::class, WholesaleDailyWriteRepository::class)
class WholesaleCollectorTest {

    @Autowired private lateinit var varietyUpsertRepository: VarietyUpsertRepository
    @Autowired private lateinit var wholesaleDailyWriteRepository: WholesaleDailyWriteRepository
    @Autowired private lateinit var batchRunRepository: BatchRunRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val restClientBuilder = RestClient.builder()
    private val mockRestServiceServer = MockRestServiceServer.bindTo(restClientBuilder).build()
    private val katSaleClient = KatSaleClient(
        restClientBuilder.build(),
        DataGoUriFactory(baseUrl = "https://apis.data.go.kr/B552845", serviceKey = "TEST%2BKEY%3D"),
    )
    private val wholesaleCollector by lazy { WholesaleCollector(katSaleClient, varietyUpsertRepository, wholesaleDailyWriteRepository, batchRunRepository) }

    /** 픽스처의 정산일자 */
    private val day = LocalDate.of(2026, 9, 7)

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/datago/$name")) { "픽스처 없음: $name" }.readText()

    /** 과실류(06)·과일과채류(08) 호출에 각각 돌려줄 응답 */
    private fun respondWith(fruit: String, fruitVegetable: String) {
        mockRestServiceServer.expect(requestTo(containsString("cond%5Bgds_lclsf_cd%3A%3AEQ%5D=06")))
            .andRespond(withSuccess(fixture(fruit), MediaType.APPLICATION_JSON))
        mockRestServiceServer.expect(requestTo(containsString("cond%5Bgds_lclsf_cd%3A%3AEQ%5D=08")))
            .andRespond(withSuccess(fixture(fruitVegetable), MediaType.APPLICATION_JSON))
    }

    private fun wholesaleRowCount() =
        jdbcTemplate.queryForObject("select count(*) from wholesale_daily where trd_clcln_ymd = ?", Int::class.java, day)

    /** 그 day에 이미 들어가 있던 행 하나 */
    private fun plantExistingRow() {
        val varietyId = varietyUpsertRepository.upsert(VarietyUpsert("ZZ", "시험", "ZZ", "시험", "01", "시험"))
        wholesaleDailyWriteRepository.replaceDay(
            day, "110001",
            listOf(
                WholesaleDailyRow(
                    trdClclnYmd = day, whslMrktCd = "110001", varietyId = varietyId,
                    trdSe = "경매", grdCd = "11", grdNm = "특", plorCd = "000000", plorNm = "옛 산지", unitNm = "kg",
                    totPrc = 1, totQty = BigDecimal.ONE,
                    lowPrcPerKg = BigDecimal.ONE, highPrcPerKg = BigDecimal.ONE, tradeCount = 1,
                ),
            ),
        )
    }

    @Test
    fun `정상 응답이면 집계해서 저장하고 SUCCESS 로 기록한다`() {
        respondWith(fruit = "katsale-trades-single-page.json", fruitVegetable = "katsale-trades-empty.json")

        val run = wholesaleCollector.collect(day)

        mockRestServiceServer.verify()
        assertThat(run.status).isEqualTo(BatchStatus.SUCCESS)
        assertThat(run.rowCount).isEqualTo(2)                 // 홍로 특급, 산지 2곳
        assertThat(wholesaleRowCount()).isEqualTo(2)
        assertThat(run.params).contains("2026-09-07")
        assertThat(run.finishedAt).isNotNull()
    }

    @Test
    fun `응답이 0행이면 그 날짜 기존 행을 지우고 EMPTY 로 기록한다`() {
        plantExistingRow()
        respondWith(fruit = "katsale-trades-empty.json", fruitVegetable = "katsale-trades-empty.json")

        val run = wholesaleCollector.collect(day)

        assertThat(run.status).isEqualTo(BatchStatus.EMPTY)
        assertThat(wholesaleRowCount()).isZero()
    }

    @Test
    fun `API 가 오류를 주면 기존 행을 건드리지 않고 FAILED 와 사유를 기록한다`() {
        plantExistingRow()
        mockRestServiceServer.expect(requestTo(containsString("cond%5Bgds_lclsf_cd%3A%3AEQ%5D=06")))
            .andRespond(withSuccess(fixture("katsale-trades-error.json"), MediaType.APPLICATION_JSON))

        val run = wholesaleCollector.collect(day)

        assertThat(run.status).isEqualTo(BatchStatus.FAILED)
        assertThat(run.message).contains("22")
        assertThat(wholesaleRowCount()).isEqualTo(1)
    }

    @Test
    fun `실행 기록이 DB 에 남는다`() {
        respondWith(fruit = "katsale-trades-single-page.json", fruitVegetable = "katsale-trades-empty.json")

        val run = wholesaleCollector.collect(day)

        val saved = batchRunRepository.findById(run.id!!).get()
        assertThat(saved.jobName).isEqualTo("wholesale-daily")
        assertThat(saved.targetDate).isEqualTo(day)
        assertThat(saved.status).isEqualTo(BatchStatus.SUCCESS)
    }
}
