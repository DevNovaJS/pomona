package com.pomona.datago.perday

import com.pomona.datago.perday.model.PriceRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PriceRequestTest {

    @Test
    fun `날짜 범위를 구분자 없는 YYYYMMDD 로 조립한다`() {
        val request = PriceRequest(
            from = LocalDate.of(2026, 9, 1),
            to = LocalDate.of(2026, 9, 7),
            categoryCode = "400",
            itemCode = "411",
        )

        val params = request.toParams()

        assertThat(params)
            .containsEntry("cond[exmn_ymd::GTE]", "20260901")
            .containsEntry("cond[exmn_ymd::LTE]", "20260907")
    }

    @Test
    fun `부류와 품목과 소매 구분을 조건에 담는다`() {
        val params = request().toParams()

        assertThat(params)
            .containsEntry("cond[ctgry_cd::EQ]", "400")
            .containsEntry("cond[item_cd::EQ]", "411")
            .containsEntry("cond[se_cd::EQ]", "01")
    }

    @Test
    fun `품종을 지정하지 않으면 해당 파라미터를 아예 보내지 않는다`() {
        val params = request(varietyCode = null).toParams()

        assertThat(params).doesNotContainKey("cond[vrty_cd::EQ]")
    }

    @Test
    fun `품종을 지정하면 파라미터에 포함한다`() {
        val params = request(varietyCode = "07").toParams()

        assertThat(params).containsEntry("cond[vrty_cd::EQ]", "07")
    }

    @Test
    fun `페이징 파라미터를 문자열로 담는다`() {
        val params = request(pageNo = 3).toParams()

        assertThat(params)
            .containsEntry("pageNo", "3")
            .containsEntry("numOfRows", "1000")
    }

    private fun request(varietyCode: String? = null, pageNo: Int = 1) = PriceRequest(
        from = LocalDate.of(2026, 9, 1),
        to = LocalDate.of(2026, 9, 7),
        categoryCode = "400",
        itemCode = "411",
        varietyCode = varietyCode,
        pageNo = pageNo,
    )
}
