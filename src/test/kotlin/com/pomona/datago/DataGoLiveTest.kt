package com.pomona.datago

import com.pomona.datago.katsale.KatSaleClient
import com.pomona.datago.katsale.model.TradeRequest
import com.pomona.datago.perday.PerDayPriceClient
import com.pomona.datago.perday.model.PriceRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

/**
 * 실제 공공데이터 API 를 호출한다. 평소에는 실행되지 않는다.
 *
 *   DATAGO_LIVE=true ./gradlew test --tests "*DataGoLiveTest"
 *
 * 픽스처 테스트가 못 잡는 것을 잡는 게 목적이다. 픽스처는 2행뿐이라 모든 필드가
 * 채워져 있지만, 실제 하루치 2천 행에는 값이 빠진 행이 섞여 있을 수 있다.
 * DTO 필드가 전부 non-null 이라 그런 행을 만나면 역직렬화가 터진다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DATAGO_LIVE", matches = "true")
class DataGoLiveTest {

    @Autowired
    private lateinit var katSaleClient: KatSaleClient

    @Autowired
    private lateinit var perDayPriceClient: PerDayPriceClient

    @Test
    fun `정산정보 하루치를 전부 받아 모든 행이 DTO 에 매핑된다`() {
        val items = katSaleClient.fetchAll(
            TradeRequest(
                date = LocalDate.of(2026, 8, 4),
                marketCode = "110001",
                categoryCode = "06",
            ),
        )

        assertThat(items).isNotEmpty()
        assertThat(items).allSatisfy {
            assertThat(it.gdsLclsfCd).isEqualTo("06")
            assertThat(it.trdClclnYmd).isEqualTo("2026-08-04")
            assertThat(it.totprc).isNotBlank()
            assertThat(it.unitTotQty).isNotBlank()
        }
        println("정산정보 수신 ${items.size}행 / 품종 ${items.map { it.gdsSclsfNm }.distinct().size}종")
    }

    @Test
    fun `가격 일주일치를 받아 모든 행이 DTO 에 매핑된다`() {
        val response = perDayPriceClient.fetchPage(
            PriceRequest(
                from = LocalDate.of(2026, 8, 1),
                to = LocalDate.of(2026, 8, 7),
                categoryCode = "400",
                itemCode = "411",
            ),
        )

        assertThat(response.items).isNotEmpty()
        assertThat(response.items).allSatisfy {
            assertThat(it.seCd).isEqualTo("01")
            assertThat(it.itemCd).isEqualTo("411")
            assertThat(it.exmnDdPrc).isNotBlank()
        }
        println("가격 수신 ${response.items.size}행 / 전체 ${response.totalCount}건")
    }
}
