package com.pomona.datago.katsale.model

import com.pomona.price.model.WholesaleDailyRow
import com.pomona.variety.model.VarietyUpsert
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * 정산정보(katSale/trades) 응답의 item 한 건.
 *
 * 공공 API 는 모든 값을 문자열로 준다. 등급에 `.` `7등` 같은 비정형 값이 실제로 섞여 오므로
 * 여기서는 변환하지 않고 원본 그대로 담는다. 숫자·날짜 변환은 다음 계층에서 한다.
 *
 * **모든 필드가 nullable 이다.** 문서에 없는 null 이 실제로 온다 — `pkg_nm` 은 조회한 모든
 * 날짜(2025-09 ~ 2026-09)에서 100% null 이었다. 여기서 non-null 을 강요하면 역직렬화가
 * 통째로 터져 어느 행이 문제였는지도 남지 않는다. 수신은 관대하게 받고, 필요한 필드의
 * 검증은 엔티티로 옮기는 매핑 계층에서 행 단위로 한다.
 *
 * `plor_nm` 에는 꼬리 공백이 붙어 온다(`"충청북도 괴산군    "`). 원본 보존이 목적이라
 * 여기서 trim 하지 않는다. 역시 매핑 계층의 몫이다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class TradeItem(
    val trdClclnYmd: String?,
    val whslMrktCd: String?,
    val whslMrktNm: String?,
    val corpCd: String?,
    val corpNm: String?,
    val gdsLclsfCd: String?,
    val gdsLclsfNm: String?,
    val gdsMclsfCd: String?,
    val gdsMclsfNm: String?,
    val gdsSclsfCd: String?,
    val gdsSclsfNm: String?,
    val trdSe: String?,
    val unitCd: String?,
    val unitNm: String?,
    val unitQty: String?,
    val unitTotQty: String?,
    val pkgCd: String?,
    val pkgNm: String?,
    val szCd: String?,
    val szNm: String?,
    val grdCd: String?,
    val grdNm: String?,
    val plorCd: String?,
    val plorNm: String?,
    val totprc: String?,
    val avgprc: String?,
    val lwprc: String?,
    val hgprc: String?,
) {
    /** 품종을 가리키는 대·중·소분류 코드. 소분류 코드만으로는 유일하지 않아 셋이 다 있어야 한다. */
    fun varietyKey(): Triple<String, String, String> = Triple(
        need(gdsLclsfCd, "gds_lclsf_cd"),
        need(gdsMclsfCd, "gds_mclsf_cd"),
        need(gdsSclsfCd, "gds_sclsf_cd"),
    )

    /** 이 행이 가리키는 품종. */
    fun toVariety(): VarietyUpsert = VarietyUpsert(
        lclsfCd = need(gdsLclsfCd, "gds_lclsf_cd"), lclsfNm = need(gdsLclsfNm, "gds_lclsf_nm"),
        mclsfCd = need(gdsMclsfCd, "gds_mclsf_cd"), mclsfNm = need(gdsMclsfNm, "gds_mclsf_nm"),
        sclsfCd = need(gdsSclsfCd, "gds_sclsf_cd"), sclsfNm = gdsSclsfNm,
    )

    /** 도매 집계에서 이 행이 들어갈 묶음. 이 값이 같은 행들이 한 줄로 접힌다. */
    fun wholesaleKey(): WholesaleKey = WholesaleKey(
        trdClclnYmd = LocalDate.parse(need(trdClclnYmd, "trd_clcln_ymd")),   // [정산]은 YYYY-MM-DD
        whslMrktCd = need(whslMrktCd, "whsl_mrkt_cd"),
        variety = varietyKey(),
        trdSe = need(trdSe, "trd_se"),
        grdCd = need(grdCd, "grd_cd"),
        plorCd = need(plorCd, "plor_cd"),
        unitNm = need(unitNm, "unit_nm"),
    )

    fun gradeName(): String = need(grdNm, "grd_nm")

    /** 원산지명. 실측 12% 에 꼬리 공백이 붙어 오고 17행은 null 이다. */
    fun originName(): String? = plorNm?.trim()

    fun totalPrice(): BigDecimal = BigDecimal(need(totprc, "totprc"))

    fun totalQuantity(): BigDecimal = BigDecimal(need(unitTotQty, "unit_tot_qty"))

    /** 최저가를 kg 당으로. lwprc 는 상자 하나 값이라 상자 규격(unit_qty)으로 나눈다. 내림. */
    fun lowPricePerKg(): BigDecimal =
        BigDecimal(need(lwprc, "lwprc")).divide(boxKg(), 2, RoundingMode.FLOOR)

    /** 최고가를 kg 당으로. 올림 — 대표가가 최저~최고 범위 안에 들게 한다. */
    fun highPricePerKg(): BigDecimal =
        BigDecimal(need(hgprc, "hgprc")).divide(boxKg(), 2, RoundingMode.CEILING)

    private fun boxKg(): BigDecimal = BigDecimal(need(unitQty, "unit_qty"))

    /** 필수값이 비어 있으면 예외. 실측에선 없었고, 생기면 그 실행을 FAILED 로 남긴다. */
    private fun need(value: String?, field: String): String =
        value ?: error("정산정보 $field 값이 비었다: $this")
}

/** 도매 집계의 자연키. 정산일자·시장·품종·매매구분·등급·산지·단위. */
data class WholesaleKey(
    val trdClclnYmd: LocalDate,
    val whslMrktCd: String,
    val variety: Triple<String, String, String>,
    val trdSe: String,
    val grdCd: String,
    val plorCd: String,
    val unitNm: String,
)

/** 응답에 나온 품종을 대·중·소분류 코드 기준으로 한 번씩만 뽑는다. */
fun List<TradeItem>.toVarieties(): List<VarietyUpsert> =
    groupBy { it.varietyKey() }
        .map { (_, group) ->
            // 실측 22행이 품종명을 null 로 준다. 같은 품종의 다른 행에 이름이 있으면 그걸 쓴다.
            group.first().toVariety().copy(sclsfNm = group.firstNotNullOfOrNull { it.gdsSclsfNm })
        }

/**
 * 하루치 원본 행을 자연키로 묶어 한 줄씩 만든다.
 * [varietyIds] 는 [toVarieties] 결과를 upsert 해서 받은 `(대, 중, 소분류 코드) → id` 다.
 */
fun List<TradeItem>.toWholesaleRows(varietyIds: Map<Triple<String, String, String>, Long>): List<WholesaleDailyRow> =
    // 물량 0 인 행은 금액도 0 이라 대표가를 끌어내리기만 한다. 실측 12,175행 중 82행.
    filter { it.totalQuantity().signum() > 0 }
        .groupBy { it.wholesaleKey() }
        .map { (key, items) ->
            WholesaleDailyRow(
                trdClclnYmd = key.trdClclnYmd,
                whslMrktCd = key.whslMrktCd,
                varietyId = varietyIds.getValue(key.variety),
                trdSe = key.trdSe,
                grdCd = key.grdCd,
                grdNm = items.first().gradeName(),
                plorCd = key.plorCd,
                plorNm = items.first().originName(),
                unitNm = key.unitNm,
                totPrc = items.sumOf { it.totalPrice() }.toLong(),
                totQty = items.sumOf { it.totalQuantity() },
                // 한 묶음에 규격이 다른 상자가 섞인다(실측 20%). 행마다 kg 당으로 바꾼 값에서 고른다.
                lowPrcPerKg = items.minOf { it.lowPricePerKg() },
                highPrcPerKg = items.maxOf { it.highPricePerKg() },
                tradeCount = items.size,
            )
        }
