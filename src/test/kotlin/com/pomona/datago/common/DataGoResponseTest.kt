package com.pomona.datago.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

class DataGoResponseTest {

    private val mapper = jacksonObjectMapper()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/datago/$name")) { "픽스처 없음: $name" }.readText()

    @Test
    fun `실제 응답에서 전체 건수를 읽는다`() {
        val json = fixture("katsale-trades-2rows.json")

        val response = mapper.readValue<DataGoResponse<Map<String, String>>>(json)

        assertThat(response.totalCount).isEqualTo(1993)
    }

    @Test
    fun `실제 응답에서 아이템 목록을 읽는다`() {
        val json = fixture("katsale-trades-2rows.json")

        val response = mapper.readValue<DataGoResponse<Map<String, String>>>(json)

        assertThat(response.items).hasSize(2)
        assertThat(response.items.first()["gds_sclsf_nm"]).isEqualTo("홍로")
    }

    @Test
    fun `결과 코드가 0이면 성공으로 판정한다`() {
        val json = fixture("katsale-trades-2rows.json")

        val response = mapper.readValue<DataGoResponse<Map<String, String>>>(json)

        assertThat(response.isSuccess).isTrue()
    }
}
