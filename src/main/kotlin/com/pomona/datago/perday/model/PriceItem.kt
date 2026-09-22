package com.pomona.datago.perday.model

import com.pomona.price.model.RetailDailyRow
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * 일별 도·소매 가격정보(perDay/price) 응답의 item 한 건.
 *
 * `exmnDdCnvsPrc`(kg환산가)는 `unit` 이 kg 일 때만 실제로 환산된 값이다.
 * '개' 단위 품목은 조사가를 그대로 복사해 온다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class PriceItem(
    val exmnYmd: String?,
    val seCd: String?,
    val seNm: String?,
    val ctgryCd: String?,
    val ctgryNm: String?,
    val itemCd: String?,
    val itemNm: String?,
    val vrtyCd: String?,
    val vrtyNm: String?,
    val grdCd: String?,
    val grdNm: String?,
    val sggCd: String?,
    val sggNm: String?,
    val unit: String?,
    val unitSz: String?,
    val mrktCd: String?,
    val mrktNm: String?,
    val exmnDdPrc: String?,
    val exmnDdCnvsPrc: String?,
    val orgnlRegDt: String?,
) {
    /** 저장용 행으로 바꾼다. 소매는 접지 않으므로 한 행이 그대로 한 줄이다. */
    fun toRow(): RetailDailyRow = RetailDailyRow(
        exmnYmd = LocalDate.parse(need(exmnYmd, "exmn_ymd"), DateTimeFormatter.BASIC_ISO_DATE),   // [가격]은 YYYYMMDD
        seCd = need(seCd, "se_cd"), seNm = need(seNm, "se_nm"),
        ctgryCd = need(ctgryCd, "ctgry_cd"), ctgryNm = need(ctgryNm, "ctgry_nm"),
        itemCd = need(itemCd, "item_cd"), itemNm = need(itemNm, "item_nm"),
        vrtyCd = need(vrtyCd, "vrty_cd"), vrtyNm = need(vrtyNm, "vrty_nm"),
        grdCd = need(grdCd, "grd_cd"), grdNm = need(grdNm, "grd_nm"),
        sggCd = need(sggCd, "sgg_cd"), sggNm = need(sggNm, "sgg_nm"),
        mrktCd = need(mrktCd, "mrkt_cd"), mrktNm = need(mrktNm, "mrkt_nm"),
        unit = need(unit, "unit"), unitSz = need(unitSz, "unit_sz"),
        exmnDdPrc = need(exmnDdPrc, "exmn_dd_prc").toLong(),
        exmnDdCnvsPrc = need(exmnDdCnvsPrc, "exmn_dd_cnvs_prc").toLong(),
        orgnlRegDt = orgnlRegDt?.let { OffsetDateTime.parse(it) },
    )

    /** 필수값이 비어 있으면 예외. 실측에선 없었고, 생기면 그 실행을 FAILED 로 남긴다. */
    private fun need(value: String?, field: String): String =
        value ?: error("가격정보 $field 값이 비었다: $this")
}
