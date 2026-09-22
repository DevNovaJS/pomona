package com.pomona.datago.perday

import com.pomona.datago.perday.model.PriceItem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class PriceItemConversionTest {

    private fun 조사(exmnDdPrc: String? = "25000", orgnlRegDt: String? = "2026-09-07T15:03:15Z") = PriceItem(
        exmnYmd = "20260907", seCd = "01", seNm = "소매",
        ctgryCd = "400", ctgryNm = "과일류", itemCd = "411", itemNm = "사과",
        vrtyCd = "06", vrtyNm = "쓰가루(아오리)", grdCd = "04", grdNm = "상품",
        sggCd = "3111", sggNm = "수원", unit = "개", unitSz = "10",
        mrktCd = "0310001", mrktNm = "지동",
        exmnDdPrc = exmnDdPrc, exmnDdCnvsPrc = exmnDdPrc, orgnlRegDt = orgnlRegDt,
    )

    @Test
    fun `조사일자와 가격 문자열을 날짜와 숫자로 바꾼다`() {
        val row = 조사().toRow()

        assertThat(row.exmnYmd).isEqualTo(LocalDate.of(2026, 9, 7))   // [가격] API 는 YYYYMMDD
        assertThat(row.exmnDdPrc).isEqualTo(25_000L)
        assertThat(row.itemCd).isEqualTo("411")
        assertThat(row.unit).isEqualTo("개")
        assertThat(row.unitSz).isEqualTo("10")
    }

    @Test
    fun `원본등록일시를 시각으로 바꾼다`() {
        assertThat(조사().toRow().orgnlRegDt)
            .isEqualTo(OffsetDateTime.of(2026, 9, 7, 15, 3, 15, 0, ZoneOffset.UTC))
    }

    @Test
    fun `원본등록일시가 없으면 null 로 둔다`() {
        assertThat(조사(orgnlRegDt = null).toRow().orgnlRegDt).isNull()
    }

    @Test
    fun `필수값이 비어 있으면 예외를 던진다`() {
        assertThatThrownBy { 조사(exmnDdPrc = null).toRow() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("exmn_dd_prc")
    }
}
