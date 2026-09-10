package com.pomona

import com.pomona.datago.katsale.KatSaleClient
import com.pomona.datago.perday.PerDayPriceClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class PomonaApplicationTests {

    @Autowired
    private lateinit var katSaleClient: KatSaleClient

    @Autowired
    private lateinit var perDayPriceClient: PerDayPriceClient

    @Test
    fun `컴포넌트 스캔으로 공공데이터 클라이언트가 등록된다`() {
        assertThat(katSaleClient).isNotNull()
        assertThat(perDayPriceClient).isNotNull()
    }
}
