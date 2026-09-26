package com.pomona.review.domain

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
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 직접 먹은 과일 리뷰.
 *
 * 품종부터 본문까지 전부 고칠 수 있으므로 저장 필드는 전부 본문 `var` + `protected set` 이고 [update] 로만 바꾼다.
 * 생성자 파라미터에 `val` 이 없는 이유다 — 파라미터는 본문 필드의 첫 값으로만 쓰인다.
 * 만든 뒤 안 바뀌는 건 [createdAt] 하나라 그것만 생성자 `val` 이다.
 *
 * 자연키가 없어 `equals`/`hashCode` 를 덮어쓰지 않는다. [com.pomona.batch.domain.BatchRun] 과 같다.
 */
@Entity
@Table(name = "review")
class Review(
    variety: VarietyMaster,
    eatenDate: LocalDate,
    title: String,
    store: String,
    origin: String?,
    price: Int,
    weightGram: Int?,
    rating: Int,
    body: String,

    @Column(nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variety_id", nullable = false)
    var variety: VarietyMaster = variety
        protected set

    /** 먹은 날. 상세에 붙는 도매 시세는 이날 또는 그 전 마지막 거래일 값이다. */
    @Column(nullable = false)
    var eatenDate: LocalDate = eatenDate
        protected set

    @Column(nullable = false, length = 100)
    var title: String = title
        protected set

    /** 산 곳 */
    @Column(nullable = false, length = 100)
    var store: String = store
        protected set

    /** 포장에 적힌 산지. 안 적혀 있으면 null */
    @Column(length = 100)
    var origin: String? = origin
        protected set

    /** 산 가격(원) */
    @Column(nullable = false)
    var price: Int = price
        protected set

    /** 산 무게(g). 바나나 한 송이처럼 모르면 null */
    @Column
    var weightGram: Int? = weightGram
        protected set

    /** 별점 0~5. 컬럼이 smallint 라 JDBC 타입을 맞춰 준다 — 안 하면 스키마 검사가 integer 를 기대해 어긋난다. */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    var rating: Int = rating
        protected set

    @Column(nullable = false, columnDefinition = "text")
    var body: String = body
        protected set

    @Column(nullable = false)
    var updatedAt: OffsetDateTime = createdAt
        protected set

    /** kg당 산 가격(원). 무게를 모르면 null — 도매가와 비교하지 않는다. */
    val pricePerKg: BigDecimal?
        get() = weightGram?.let { BigDecimal(price).multiply(GRAMS_PER_KG).divide(BigDecimal(it), 0, RoundingMode.HALF_UP) }

    fun update(
        variety: VarietyMaster,
        eatenDate: LocalDate,
        title: String,
        store: String,
        origin: String?,
        price: Int,
        weightGram: Int?,
        rating: Int,
        body: String,
    ) {
        this.variety = variety
        this.eatenDate = eatenDate
        this.title = title
        this.store = store
        this.origin = origin
        this.price = price
        this.weightGram = weightGram
        this.rating = rating
        this.body = body
        this.updatedAt = OffsetDateTime.now()
    }

    override fun toString(): String = "Review(id=$id, $eatenDate $title)"
}

private val GRAMS_PER_KG = BigDecimal(1000)
