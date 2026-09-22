package com.pomona.datago.perday

import com.pomona.datago.DataGoUriFactory
import com.pomona.datago.perday.model.PriceRequest
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

class PerDayPriceClientTest {

    private val uriFactory = DataGoUriFactory(
        baseUrl = "https://apis.data.go.kr/B552845",
        serviceKey = "TEST%2BKEY%3D",
    )
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = PerDayPriceClient(builder.build(), uriFactory)

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/datago/$name")) { "픽스처 없음: $name" }.readText()

    private fun request() = PriceRequest(
        from = LocalDate.of(2026, 9, 7),
        to = LocalDate.of(2026, 9, 7),
        categoryCode = "400",
        itemCode = "411",
    )

    @Test
    fun `인코딩된 조건으로 호출하고 응답을 파싱한다`() {
        server.expect(requestTo(containsString("cond%5Bexmn_ymd%3A%3AGTE%5D=20260907")))
            .andRespond(withSuccess(fixture("perday-price-2rows.json"), MediaType.APPLICATION_JSON))

        val response = client.fetchPage(request())

        assertThat(response.totalCount).isEqualTo(108)
        assertThat(response.items.first().vrtyNm).isEqualTo("쓰가루(아오리)")
        server.verify()
    }

    @Test
    fun `전체 건수가 한 페이지를 넘으면 다음 페이지까지 이어서 받는다`() {
        // totalCount 108, numOfRows 100 -> 2 페이지
        server.expect(requestTo(containsString("pageNo=1")))
            .andRespond(withSuccess(fixture("perday-price-2rows.json"), MediaType.APPLICATION_JSON))
        server.expect(requestTo(containsString("pageNo=2")))
            .andRespond(withSuccess(fixture("perday-price-2rows.json"), MediaType.APPLICATION_JSON))

        val items = client.fetchAll(request().copy(numOfRows = 100))

        assertThat(items).hasSize(4)
        server.verify()
    }

    @Test
    fun `한 페이지로 끝나면 추가 호출을 하지 않는다`() {
        // totalCount 108, numOfRows 1000 -> 1 페이지
        server.expect(requestTo(containsString("pageNo=1")))
            .andRespond(withSuccess(fixture("perday-price-2rows.json"), MediaType.APPLICATION_JSON))

        val items = client.fetchAll(request())

        assertThat(items).hasSize(2)
        server.verify()
    }

    @Test
    fun `조사가 없으면 빈 목록을 돌려주고 추가 호출을 하지 않는다`() {
        // 실제 응답: 2026-09-04 딸기 소매 0행
        server.expect(requestTo(containsString("pageNo=1")))
            .andRespond(withSuccess(fixture("perday-price-empty.json"), MediaType.APPLICATION_JSON))

        val items = client.fetchAll(request())

        assertThat(items).isEmpty()
        server.verify()
    }

    @Test
    fun `결과 코드가 0이 아니면 예외를 던진다`() {
        server.expect(requestTo(containsString("perDay/price")))
            .andRespond(withSuccess(fixture("perday-price-error.json"), MediaType.APPLICATION_JSON))

        assertThatThrownBy { client.fetchPage(request()) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR")
    }
}
