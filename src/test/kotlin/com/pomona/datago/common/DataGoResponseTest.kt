package com.pomona.datago.common

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

class DataGoResponseTest {

    /**
     * 봉투만 검증하려고 두는 최소 DTO. `Map<String, String>` 으로 받으면 값 타입의 null 허용 여부가
     * 제네릭 소거로 Jackson 에 전달되지 않아, 실제 응답에 섞인 null(`pkg_nm`)에서 터진다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class Row(val gds_sclsf_nm: String?, val pkg_nm: String?)

    private val mapper = jacksonObjectMapper()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/datago/$name")) { "픽스처 없음: $name" }.readText()

    private fun parse(name: String): DataGoResponse<Row> =
        mapper.readValue<DataGoResponse<Row>>(fixture(name))

    @Test
    fun `실제 응답에서 전체 건수를 읽는다`() {
        assertThat(parse("katsale-trades-2rows.json").totalCount).isEqualTo(1993)
    }

    @Test
    fun `실제 응답에서 아이템 목록을 읽는다`() {
        val response = parse("katsale-trades-2rows.json")

        assertThat(response.items).hasSize(2)
        assertThat(response.items.first().gds_sclsf_nm).isEqualTo("홍로")
    }

    @Test
    fun `결과 코드가 0이면 성공으로 판정한다`() {
        assertThat(parse("katsale-trades-2rows.json").isSuccess).isTrue()
    }
}
