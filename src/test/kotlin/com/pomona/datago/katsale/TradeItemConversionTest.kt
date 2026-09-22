package com.pomona.datago.katsale

import com.pomona.datago.katsale.model.TradeItem
import com.pomona.datago.katsale.model.toVarieties
import com.pomona.datago.katsale.model.toWholesaleRows
import com.pomona.variety.model.VarietyUpsert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TradeItemConversionTest {

    private val 홍로 = Triple("06", "01", "17")
    private val 품종id = mapOf(홍로 to 1L)

    /** 정산정보 한 행. 바꾸고 싶은 값만 이름으로 넘긴다. */
    private fun 거래(
        grdCd: String? = "11",
        plorCd: String? = "367000",
        plorNm: String? = "충청북도 괴산군",
        unitQty: String? = "10.000",
        unitTotQty: String? = "100.000",
        totprc: String? = "300000.000",
        lwprc: String? = "20000.000",
        hgprc: String? = "40000.000",
        sclsfNm: String? = "홍로",
    ) = TradeItem(
        trdClclnYmd = "2025-09-22", whslMrktCd = "110001", whslMrktNm = "서울가락",
        corpCd = "11000101", corpNm = "서울청과㈜",
        gdsLclsfCd = "06", gdsLclsfNm = "과실류",
        gdsMclsfCd = "01", gdsMclsfNm = "사과",
        gdsSclsfCd = "17", gdsSclsfNm = sclsfNm,
        trdSe = "경매", unitCd = "12", unitNm = "kg",
        unitQty = unitQty, unitTotQty = unitTotQty,
        pkgCd = "101", pkgNm = null, szCd = "100", szNm = ".",
        grdCd = grdCd, grdNm = "특",
        plorCd = plorCd, plorNm = plorNm,
        totprc = totprc, avgprc = "0.000", lwprc = lwprc, hgprc = hgprc,
    )

    // ── 도매 집계 ─────────────────────────────────────────────────────

    @Test
    fun `자연키가 같은 행은 한 줄로 접히고 금액과 물량이 더해진다`() {
        val rows = listOf(
            거래(unitTotQty = "1925.000", totprc = "14488000.000"),
            거래(unitTotQty = "80.000", totprc = "144000.000"),
        ).toWholesaleRows(품종id)

        assertThat(rows).hasSize(1)
        val row = rows.first()
        assertThat(row.totPrc).isEqualTo(14_632_000L)
        assertThat(row.totQty).isEqualByComparingTo("2005")
        assertThat(row.tradeCount).isEqualTo(2)
        assertThat(row.trdClclnYmd).isEqualTo(LocalDate.of(2025, 9, 22))
        assertThat(row.varietyId).isEqualTo(1L)
    }

    @Test
    fun `등급이나 산지가 다르면 따로 남는다`() {
        val rows = listOf(거래(grdCd = "11"), 거래(grdCd = "12"), 거래(plorCd = "568000")).toWholesaleRows(품종id)

        assertThat(rows).hasSize(3)
    }

    @Test
    fun `물량이 0인 행은 집계에서 뺀다`() {
        // 실측 12,175행 중 82행. 금액도 0이라 대표가를 끌어내리기만 한다.
        val rows = listOf(거래(unitTotQty = "100.000"), 거래(unitTotQty = "0.000", totprc = "0.000"))
            .toWholesaleRows(품종id)

        assertThat(rows.single().tradeCount).isEqualTo(1)
    }

    @Test
    fun `물량 0인 행만 있는 묶음은 아예 나오지 않는다`() {
        assertThat(listOf(거래(unitTotQty = "0.000")).toWholesaleRows(품종id)).isEmpty()
    }

    @Test
    fun `최저가와 최고가는 상자값을 kg 당으로 바꾼 뒤 고른다`() {
        // lwprc·hgprc 는 상자 하나 값이다. 10kg 상자 11,000원(kg당 1,100)과 11.5kg 상자 22,000원(kg당 1,913)이
        // 한 묶음에 섞이면 kg 당으로 바꾼 값에서 최저·최고를 골라야 한다.
        val row = listOf(
            거래(unitQty = "10.000", lwprc = "11000.000", hgprc = "30000.000"),
            거래(unitQty = "11.500", lwprc = "22000.000", hgprc = "22000.000"),
        ).toWholesaleRows(품종id).single()

        assertThat(row.lowPrcPerKg).isEqualByComparingTo("1100.00")
        assertThat(row.highPrcPerKg).isEqualByComparingTo("3000.00")
    }

    @Test
    fun `kg 환산 최저가는 내림 최고가는 올림한다`() {
        // 22,000 / 11.5 = 1,913.0434… 반올림하면 경계가 안쪽으로 밀려 대표가가 범위를 벗어난다.
        val row = listOf(거래(unitQty = "11.500", lwprc = "22000.000", hgprc = "22000.000"))
            .toWholesaleRows(품종id).single()

        assertThat(row.lowPrcPerKg).isEqualByComparingTo("1913.04")
        assertThat(row.highPrcPerKg).isEqualByComparingTo("1913.05")
    }

    @Test
    fun `산지명 꼬리 공백을 잘라낸다`() {
        val row = listOf(거래(plorNm = "충청북도 괴산군    ")).toWholesaleRows(품종id).single()

        assertThat(row.plorNm).isEqualTo("충청북도 괴산군")
    }

    @Test
    fun `산지명이 null 이면 null 로 둔다`() {
        assertThat(listOf(거래(plorNm = null)).toWholesaleRows(품종id).single().plorNm).isNull()
    }

    @Test
    fun `필수값이 비어 있으면 예외를 던진다`() {
        // 실측에선 한 번도 없었다. 생기면 그 실행을 FAILED 로 남기고 원인을 본다.
        assertThatThrownBy { listOf(거래(totprc = null)).toWholesaleRows(품종id) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("totprc")
    }

    // ── 품종 목록 ─────────────────────────────────────────────────────

    @Test
    fun `품종은 대중소 코드 기준으로 한 번씩만 뽑는다`() {
        assertThat(listOf(거래(), 거래(), 거래(grdCd = "12")).toVarieties()).containsExactly(
            VarietyUpsert("06", "과실류", "01", "사과", "17", "홍로"),
        )
    }

    @Test
    fun `품종명이 null 인 행과 있는 행이 섞이면 있는 쪽을 쓴다`() {
        assertThat(listOf(거래(sclsfNm = null), 거래(sclsfNm = "홍로")).toVarieties().single().sclsfNm)
            .isEqualTo("홍로")
    }
}
