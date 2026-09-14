package com.pomona.price.domain

import com.pomona.variety.domain.VarietyMaster
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 도매 일별 집계. [정산] 원본 여러 행을 자연키로 묶은 한 줄.
 *
 * 자연키는 7컬럼이고, 그중 [variety] 자리가 원래 품종 3컬럼이라 실질 9컬럼이다.
 * 대표가는 컬럼이 아니라 [representativePricePerKg] 로 계산한다 — 저장하면 [totPrc]·[totQty] 와
 * 어긋날 수 있는 파생 데이터가 된다.
 */
@Entity
@Table(name = "wholesale_daily")
class WholesaleDaily(

    @Column(nullable = false)
    val trdClclnYmd: LocalDate,

    @Column(nullable = false, length = 6)
    val whslMrktCd: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variety_id", nullable = false)
    val variety: VarietyMaster,

    /** 매매구분. 실측 5종 — 경매 / 정가수의 / 정가수의(예약형) / 전자거래 / `-`. 코드가 없다. */
    @Column(nullable = false, length = 20)
    val trdSe: String,

    @Column(nullable = false, length = 2)
    val grdCd: String,

    @Column(nullable = false, length = 20)
    var grdNm: String,

    @Column(nullable = false, length = 6)
    val plorCd: String,

    /** 원산지명. 꼬리 공백이 붙어 오므로 trim 해서 넣는다. 실측 17행은 null. */
    @Column(length = 30)
    var plorNm: String?,

    @Column(nullable = false, length = 10)
    val unitNm: String,

    /** SUM(totprc). 이 묶음으로 오간 거래대금 총액(원). */
    @Column(nullable = false)
    var totPrc: Long,

    /** SUM(unit_tot_qty). 실제로 오간 무게 합(kg). 소수가 실제로 온다. */
    @Column(nullable = false, precision = 14, scale = 3)
    var totQty: BigDecimal,

    /** MIN(lwprc / unit_qty). 최저 낙찰가의 kg 환산. 내림으로 저장한다. */
    @Column(nullable = false, precision = 12, scale = 2)
    var lowPrcPerKg: BigDecimal,

    /** MAX(hgprc / unit_qty). 최고 낙찰가의 kg 환산. 올림으로 저장한다. */
    @Column(nullable = false, precision = 12, scale = 2)
    var highPrcPerKg: BigDecimal,

    /** 이 한 줄로 합쳐진 [정산] 원본 행 수. 39%가 1이다 — 그런 행의 대표가는 단일 거래값이다. */
    @Column(nullable = false)
    var tradeCount: Int,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    /** 대표가(원/kg). 저장하지 않고 그때그때 나눈다. */
    val representativePricePerKg: BigDecimal
        get() = totPrc.toBigDecimal().divide(totQty, 2, java.math.RoundingMode.HALF_UP)

    private val naturalKey
        get() = listOf(trdClclnYmd, whslMrktCd, variety, trdSe, grdCd, plorCd, unitNm)

    override fun equals(other: Any?): Boolean =
        this === other || (other is WholesaleDaily && naturalKey == other.naturalKey)

    override fun hashCode(): Int = naturalKey.hashCode()

    override fun toString(): String =
        "WholesaleDaily(id=$id, $trdClclnYmd ${variety.sclsfNm} $grdNm $plorNm, ${totQty}kg)"
}
