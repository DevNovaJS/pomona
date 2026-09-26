package com.pomona.variety.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 소매 품종 마스터. [VarietyMaster] 의 소매 쪽. 쓰기는 [RetailVarietyUpsertRepository] 가 해서 JPA 로는 읽기만 하므로
 * id 외 전부 생성자 `val` 이다. `equals`/`hashCode` 는 자연키(부류·품목·품종 코드)로만 비교한다.
 */
@Entity
@Table(name = "retail_variety")
class RetailVariety(

    @Column(nullable = false, length = 3)
    val ctgryCd: String,

    @Column(nullable = false, length = 20)
    val ctgryNm: String,

    @Column(nullable = false, length = 3)
    val itemCd: String,

    @Column(nullable = false, length = 20)
    val itemNm: String,

    @Column(nullable = false, length = 2)
    val vrtyCd: String,

    @Column(nullable = false, length = 30)
    val vrtyNm: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    private val naturalKey get() = Triple(ctgryCd, itemCd, vrtyCd)

    override fun equals(other: Any?): Boolean =
        this === other || (other is RetailVariety && naturalKey == other.naturalKey)

    override fun hashCode(): Int = naturalKey.hashCode()

    override fun toString(): String = "RetailVariety(id=$id, $itemCd/$vrtyCd, $itemNm $vrtyNm)"
}
