package com.pomona.datago.katsale

import com.pomona.datago.katsale.model.TradeRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TradeRequestTest {

    @Test
    fun `필수 조건을 API 파라미터 이름으로 조립한다`() {
        val request = TradeRequest(
            date = LocalDate.of(2026, 9, 7),
            marketCode = "110001",
        )

        val params = request.toParams()

        assertThat(params)
            .containsEntry("cond[trd_clcln_ymd::EQ]", "2026-09-07")
            .containsEntry("cond[whsl_mrkt_cd::EQ]", "110001")
    }

    @Test
    fun `대분류를 지정하지 않으면 해당 파라미터를 아예 보내지 않는다`() {
        val request = TradeRequest(
            date = LocalDate.of(2026, 9, 7),
            marketCode = "110001",
            categoryCode = null,
        )

        val params = request.toParams()

        assertThat(params).doesNotContainKey("cond[gds_lclsf_cd::EQ]")
    }

    @Test
    fun `대분류를 지정하면 파라미터에 포함한다`() {
        val request = TradeRequest(
            date = LocalDate.of(2026, 9, 7),
            marketCode = "110001",
            categoryCode = "06",
        )

        val params = request.toParams()

        assertThat(params).containsEntry("cond[gds_lclsf_cd::EQ]", "06")
    }

    @Test
    fun `페이징 파라미터를 문자열로 담는다`() {
        val request = TradeRequest(
            date = LocalDate.of(2026, 9, 7),
            marketCode = "110001",
            pageNo = 3,
        )

        val params = request.toParams()

        assertThat(params)
            .containsEntry("pageNo", "3")
            .containsEntry("numOfRows", "1000")
    }
}
