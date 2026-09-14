package com.pomona.price.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 소매 조사 원본. [가격] API 의 한 행이 그대로 한 행이다. 집계하지 않는다.
 *
 * 품종 FK 가 없다 — [정산]과 코드 체계가 달라(`06/01/17` vs `400/411/07`) 지금은 연결할 수 없다.
 * 실측에서 null 도 공백도 0건이라 전 컬럼이 non-null 이다.
 */
@Entity
@Table(name = "retail_daily")
class RetailDaily(

    @Column(nullable = false) val exmnYmd: LocalDate,

    /** 유통 단계. 01 소매 / 02 중도매 / 03 친환경. 자연키에 포함된다. */
    @Column(nullable = false, length = 2) val seCd: String,
    @Column(nullable = false, length = 20) val seNm: String,

    @Column(nullable = false, length = 3) val ctgryCd: String,
    @Column(nullable = false, length = 20) val ctgryNm: String,
    @Column(nullable = false, length = 3) val itemCd: String,
    @Column(nullable = false, length = 20) val itemNm: String,
    @Column(nullable = false, length = 2) val vrtyCd: String,
    @Column(nullable = false, length = 30) val vrtyNm: String,

    /** 등급. 축이 품목마다 다르다 — 04 상품·05 중품(품질) / 15 M과·16 S과(크기). */
    @Column(nullable = false, length = 2) val grdCd: String,
    @Column(nullable = false, length = 20) val grdNm: String,

    @Column(nullable = false, length = 4) val sggCd: String,
    @Column(nullable = false, length = 20) val sggNm: String,
    /** 조사한 점포. 실측 52곳, 지역당 2곳 안팎이라 지역별 대표값을 내기엔 표본이 얇다. */
    @Column(nullable = false, length = 7) val mrktCd: String,
    @Column(nullable = false, length = 30) val mrktNm: String,

    /** 판매 단위. 개 / kg / g. [unitSz] 와 붙여야 뜻이 생긴다 — 개 + 10 = 10개 묶음. */
    @Column(nullable = false, length = 10) val unit: String,
    @Column(nullable = false, length = 10) val unitSz: String,

    /** 조사일가격(원). 위 묶음 하나의 값. */
    @Column(nullable = false) var exmnDdPrc: Long,

    /** kg 환산가. [unit] 이 kg 일 때만 실제로 환산된다. '개' 단위는 조사가를 복사해 온다. */
    @Column(nullable = false) var exmnDdCnvsPrc: Long,

    @Column var orgnlRegDt: OffsetDateTime?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    private val naturalKey
        get() = listOf(exmnYmd, seCd, ctgryCd, itemCd, vrtyCd, grdCd, sggCd, mrktCd)

    override fun equals(other: Any?): Boolean =
        this === other || (other is RetailDaily && naturalKey == other.naturalKey)

    override fun hashCode(): Int = naturalKey.hashCode()

    override fun toString(): String =
        "RetailDaily(id=$id, $exmnYmd $itemNm $vrtyNm $grdNm $mrktNm, $exmnDdPrc)"
}
