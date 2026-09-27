package com.pomona.variety.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * 정산 품종 ↔ 소매 품종. 백오피스에서 손으로 짝짓는다.
 *
 * 정산 품종([variety])은 만든 뒤 안 바뀌므로 생성자 `val`, 짝지은 소매 품종([retailVariety])은 다시 고를 수 있어
 * 본문 `var` 이다. 행이 없으면 소매 짝이 없는 것이다.
 * 자연키가 [variety] 하나라 그것으로 비교한다.
 */
@Entity
@Table(name = "variety_retail_mapping")
class VarietyRetailMapping(

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variety_id", nullable = false, unique = true)
    val variety: VarietyMaster,

    retailVariety: RetailVariety,

    @Column(nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "retail_variety_id", nullable = false)
    var retailVariety: RetailVariety = retailVariety
        protected set

    @Column(nullable = false)
    var updatedAt: OffsetDateTime = createdAt
        protected set

    /** 다른 소매 품종으로 바꾼다. */
    fun mapTo(retailVariety: RetailVariety) {
        this.retailVariety = retailVariety
        this.updatedAt = OffsetDateTime.now()
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is VarietyRetailMapping && variety == other.variety)

    override fun hashCode(): Int = variety.hashCode()

    override fun toString(): String = "VarietyRetailMapping(id=$id, $variety -> $retailVariety)"
}
