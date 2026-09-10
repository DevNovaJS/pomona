package com.pomona.datago

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DataGoUriFactoryTest {

    private val factory = DataGoUriFactory(
        baseUrl = "https://apis.data.go.kr/B552845",
        serviceKey = "TEST%2BKEY%3D",
    )

    @Test
    fun `cond 파라미터의 대괄호와 콜론을 퍼센트 인코딩한다`() {
        val uri = factory.build(
            path = "katSale/trades",
            params = mapOf("cond[trd_clcln_ymd::EQ]" to "2026-09-07"),
        )

        assertThat(uri.toString())
            .contains("cond%5Btrd_clcln_ymd%3A%3AEQ%5D=2026-09-07")
    }

    @Test
    fun `응답 형식을 항상 json 으로 요청한다`() {
        val uri = factory.build(
            path = "perDay/price",
            params = mapOf("pageNo" to "1"),
        )

        assertThat(uri.toString()).contains("returnType=json")
    }
}
