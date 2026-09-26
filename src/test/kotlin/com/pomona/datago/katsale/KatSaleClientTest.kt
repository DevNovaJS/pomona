package com.pomona.datago.katsale

import com.pomona.datago.DataGoUriFactory
import com.pomona.datago.katsale.model.TradeRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.LocalDate

class KatSaleClientTest {

    private val dataGoUriFactory = DataGoUriFactory(
        baseUrl = "https://apis.data.go.kr/B552845",
        serviceKey = "TEST%2BKEY%3D",
    )
    private val restClientBuilder = RestClient.builder()
    private val mockRestServiceServer = MockRestServiceServer.bindTo(restClientBuilder).build()
    private val katSaleClient = KatSaleClient(restClientBuilder.build(), dataGoUriFactory)

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/datago/$name")) { "픽스처 없음: $name" }.readText()

    @Test
    fun `인코딩된 조건으로 호출하고 응답을 파싱한다`() {
        mockRestServiceServer.expect(requestTo(containsString("cond%5Btrd_clcln_ymd%3A%3AEQ%5D=2026-09-07")))
            .andRespond(withSuccess(fixture("katsale-trades-2rows.json"), MediaType.APPLICATION_JSON))

        val response = katSaleClient.fetchPage(
            TradeRequest(date = LocalDate.of(2026, 9, 7), marketCode = "110001"),
        )

        assertThat(response.totalCount).isEqualTo(1993)
        assertThat(response.items.first().gdsSclsfNm).isEqualTo("홍로")
        mockRestServiceServer.verify()
    }

    @Test
    fun `전체 건수가 한 페이지를 넘으면 다음 페이지까지 이어서 받는다`() {
        // totalCount 1993, numOfRows 1000 -> 2 페이지
        mockRestServiceServer.expect(requestTo(containsString("pageNo=1")))
            .andRespond(withSuccess(fixture("katsale-trades-2rows.json"), MediaType.APPLICATION_JSON))
        mockRestServiceServer.expect(requestTo(containsString("pageNo=2")))
            .andRespond(withSuccess(fixture("katsale-trades-2rows.json"), MediaType.APPLICATION_JSON))

        val items = katSaleClient.fetchAll(
            TradeRequest(date = LocalDate.of(2026, 9, 7), marketCode = "110001"),
        )

        assertThat(items).hasSize(4)
        mockRestServiceServer.verify()
    }

    @Test
    fun `한 페이지로 끝나면 추가 호출을 하지 않는다`() {
        mockRestServiceServer.expect(requestTo(containsString("pageNo=1")))
            .andRespond(withSuccess(fixture("katsale-trades-single-page.json"), MediaType.APPLICATION_JSON))

        val items = katSaleClient.fetchAll(
            TradeRequest(date = LocalDate.of(2026, 9, 7), marketCode = "110001"),
        )

        assertThat(items).hasSize(2)
        mockRestServiceServer.verify()
    }

    @Test
    fun `결측이면 빈 목록을 돌려주고 추가 호출을 하지 않는다`() {
        mockRestServiceServer.expect(requestTo(containsString("pageNo=1")))
            .andRespond(withSuccess(fixture("katsale-trades-empty.json"), MediaType.APPLICATION_JSON))

        val items = katSaleClient.fetchAll(
            TradeRequest(date = LocalDate.of(2026, 9, 6), marketCode = "110001"),
        )

        assertThat(items).isEmpty()
        mockRestServiceServer.verify()
    }

    @Test
    fun `결과 코드가 0이 아니면 예외를 던진다`() {
        mockRestServiceServer.expect(requestTo(containsString("katSale/trades")))
            .andRespond(withSuccess(fixture("katsale-trades-error.json"), MediaType.APPLICATION_JSON))

        assertThatThrownBy {
            katSaleClient.fetchPage(TradeRequest(date = LocalDate.of(2026, 9, 7), marketCode = "110001"))
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR")
    }
}
