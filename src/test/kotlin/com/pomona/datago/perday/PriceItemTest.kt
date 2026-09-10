package com.pomona.datago.perday

import com.pomona.datago.common.DataGoResponse
import com.pomona.datago.perday.model.PriceItem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

class PriceItemTest {

    private val mapper = jacksonObjectMapper()

    private fun firstItem(): PriceItem {
        val json = checkNotNull(javaClass.getResource("/datago/perday-price-2rows.json")) {
            "픽스처 없음"
        }.readText()
        return mapper.readValue<DataGoResponse<PriceItem>>(json).items.first()
    }

    @Test
    fun `snake_case 응답 필드를 camelCase 프로퍼티로 읽는다`() {
        val item = firstItem()

        assertThat(item.exmnYmd).isEqualTo("20260907")
        assertThat(item.vrtyNm).isEqualTo("쓰가루(아오리)")
        assertThat(item.sggNm).isEqualTo("대전")
    }

    @Test
    fun `조사가와 kg환산가를 원본 문자열로 담는다`() {
        val item = firstItem()

        assertThat(item.exmnDdPrc).isEqualTo("19900")
        assertThat(item.exmnDdCnvsPrc).isEqualTo("19900")
        assertThat(item.unit).isEqualTo("개")
        assertThat(item.unitSz).isEqualTo("10")
    }
}
