package com.pomona.datago.katsale

import com.pomona.datago.common.DataGoResponse
import com.pomona.datago.katsale.model.TradeItem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

class TradeItemTest {

    private val mapper = jacksonObjectMapper()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/datago/$name")) { "픽스처 없음: $name" }.readText()

    private fun firstItem(): TradeItem =
        mapper.readValue<DataGoResponse<TradeItem>>(fixture("katsale-trades-2rows.json"))
            .items.first()

    @Test
    fun `snake_case 응답 필드를 camelCase 프로퍼티로 읽는다`() {
        val item = firstItem()

        assertThat(item.gdsSclsfNm).isEqualTo("홍로")
        assertThat(item.trdClclnYmd).isEqualTo("2026-09-07")
        assertThat(item.whslMrktCd).isEqualTo("110001")
    }

    @Test
    fun `가격과 물량은 원본 그대로 문자열로 담는다`() {
        val item = firstItem()

        assertThat(item.totprc).isEqualTo("7395000.000")
        assertThat(item.unitTotQty).isEqualTo("810.000")
    }

    @Test
    fun `문서에 없는 null 이 와도 역직렬화가 터지지 않는다`() {
        // pkg_nm 은 실제 API 에서 모든 날짜에 null 로 온다. 여기서 터지면 배치가 통째로 죽는다.
        val item = firstItem()

        assertThat(item.pkgNm).isNull()
        assertThat(item.pkgCd).isEqualTo("101")
    }
}
