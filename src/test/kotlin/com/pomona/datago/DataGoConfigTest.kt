package com.pomona.datago

import com.pomona.datago.katsale.KatSaleClient
import com.pomona.datago.perday.PerDayPriceClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class DataGoConfigTest {

    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration::class.java))
        .withUserConfiguration(DataGoConfig::class.java)
        .withPropertyValues("datago.service-key=TEST%2BKEY%3D")

    @Test
    fun `두 API 클라이언트를 빈으로 등록한다`() {
        runner.run { context ->
            assertThat(context).hasSingleBean(KatSaleClient::class.java)
            assertThat(context).hasSingleBean(PerDayPriceClient::class.java)
        }
    }

    @Test
    fun `설정에서 읽은 인증키가 실제 요청 URI 에 실린다`() {
        runner.run { context ->
            val uri = context.getBean(DataGoUriFactory::class.java)
                .build("katSale/trades", emptyMap())

            assertThat(uri.toString())
                .startsWith("https://apis.data.go.kr/B552845/katSale/trades")
                .contains("serviceKey=TEST%2BKEY%3D")
        }
    }

    @Test
    fun `인증키가 없으면 기동에 실패한다`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration::class.java))
            .withUserConfiguration(DataGoConfig::class.java)
            .run { context -> assertThat(context).hasFailed() }
    }
}
