package com.pomona.batch.service

import com.pomona.batch.domain.BatchRunRepository
import com.pomona.batch.domain.BatchStatus
import com.pomona.datago.DataGoUriFactory
import com.pomona.datago.perday.PerDayPriceClient
import com.pomona.price.domain.RetailDailyWriteRepository
import com.pomona.price.model.RetailDailyRow
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
import java.time.LocalDate

/** 소매는 품목 하나 × 기간이 한 번의 수집이다. API 호출도 한 번(여러 페이지면 이어 받기). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(RetailDailyWriteRepository::class)
class RetailCollectorTest {

    @Autowired private lateinit var retail: RetailDailyWriteRepository
    @Autowired private lateinit var runs: BatchRunRepository
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = PerDayPriceClient(
        builder.build(),
        DataGoUriFactory(baseUrl = "https://apis.data.go.kr/B552845", serviceKey = "TEST%2BKEY%3D"),
    )
    private val collector by lazy { RetailCollector(client, retail, runs) }

    private val 사과 = RetailItem(ctgryCd = "400", itemCd = "411", name = "사과")
    private val 배 = RetailItem(ctgryCd = "400", itemCd = "412", name = "배")

    /** 픽스처의 조사일자 */
    private val 날짜 = LocalDate.of(2026, 9, 7)
    private val 시작 = 날짜.minusDays(4)

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/datago/$name")) { "픽스처 없음: $name" }.readText()

    private fun 응답(name: String) {
        server.expect(requestTo(containsString("cond%5Bitem_cd%3A%3AEQ%5D=411")))
            .andRespond(withSuccess(fixture(name), MediaType.APPLICATION_JSON))
    }

    private fun 소매행수(itemCd: String) = jdbc.queryForObject(
        "select count(*) from retail_daily where item_cd = ? and exmn_ymd between ? and ?",
        Int::class.java, itemCd, 시작, 날짜,
    )

    private fun 기존행심기(item: RetailItem) {
        retail.replaceRange(
            "01", item.ctgryCd, item.itemCd, 시작, 날짜,
            listOf(
                RetailDailyRow(
                    exmnYmd = 날짜, seCd = "01", seNm = "소매",
                    ctgryCd = item.ctgryCd, ctgryNm = "과일류", itemCd = item.itemCd, itemNm = item.name,
                    vrtyCd = "99", vrtyNm = "옛품종", grdCd = "04", grdNm = "상품",
                    sggCd = "9999", sggNm = "시험시", mrktCd = "9999999", mrktNm = "시험점포",
                    unit = "개", unitSz = "10", exmnDdPrc = 1, exmnDdCnvsPrc = 1, orgnlRegDt = null,
                ),
            ),
        )
    }

    @Test
    fun `정상 응답이면 그대로 저장하고 SUCCESS 로 기록한다`() {
        응답("perday-price-2rows.json")

        val run = collector.collect(사과, 시작, 날짜)

        server.verify()
        assertThat(run.status).isEqualTo(BatchStatus.SUCCESS)
        assertThat(run.rowCount).isEqualTo(2)        // 소매는 접지 않으므로 응답 행 수 그대로
        assertThat(소매행수("411")).isEqualTo(2)
        assertThat(run.params).contains("411")
    }

    @Test
    fun `조사가 없으면 그 품목 기간의 기존 행을 지우고 EMPTY 로 기록한다`() {
        기존행심기(사과)
        응답("perday-price-empty.json")

        val run = collector.collect(사과, 시작, 날짜)

        assertThat(run.status).isEqualTo(BatchStatus.EMPTY)
        assertThat(소매행수("411")).isZero()
    }

    @Test
    fun `API 가 오류를 주면 기존 행을 건드리지 않고 FAILED 와 사유를 기록한다`() {
        기존행심기(사과)
        응답("perday-price-error.json")

        val run = collector.collect(사과, 시작, 날짜)

        assertThat(run.status).isEqualTo(BatchStatus.FAILED)
        assertThat(run.message).contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR")
        assertThat(소매행수("411")).isEqualTo(1)
    }

    @Test
    fun `다른 품목은 건드리지 않는다`() {
        기존행심기(배)
        응답("perday-price-empty.json")

        collector.collect(사과, 시작, 날짜)

        assertThat(소매행수("412")).isEqualTo(1)
    }

    @Test
    fun `실행 기록에 품목과 기간이 남는다`() {
        응답("perday-price-2rows.json")

        val run = collector.collect(사과, 시작, 날짜)

        val saved = runs.findById(run.id!!).get()
        assertThat(saved.jobName).isEqualTo("retail-daily")
        assertThat(saved.targetDate).isEqualTo(날짜)          // 기간의 마지막 날
        assertThat(saved.params).contains("2026-09-03").contains("2026-09-07")
    }

    @Test
    fun `수집할 품목 21개가 정해져 있다`() {
        assertThat(RETAIL_ITEMS).hasSize(21)
        assertThat(RETAIL_ITEMS.map { it.itemCd }).contains("411", "226", "257")
        assertThat(RETAIL_ITEMS.filter { it.ctgryCd == "200" }).hasSize(5)   // 딸기 수박 참외 토마토 멜론
    }
}
