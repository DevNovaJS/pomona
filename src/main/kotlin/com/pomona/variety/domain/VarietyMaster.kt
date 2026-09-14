package com.pomona.variety.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 품종 마스터. [정산] API 가 거래마다 반복해서 주는 품종 6필드에서 중복을 걷어낸 것.
 *
 * **`data class` 가 아니다.** data class 는 생성자 프로퍼티 전부로 `equals`/`hashCode` 를 만드는데,
 * 거기에 [id] 가 끼면 `persist` 전에는 null, 후에는 값이 생겨 같은 객체의 해시가 도중에 바뀐다.
 * `Set` 이나 `Map` 에 넣어둔 엔티티를 저장 직후 못 찾게 된다. 그래서 손으로 쓰되
 * **자연키(대·중·소분류 코드)로만** 비교한다. 자연키는 한 번 정해지면 안 바뀌므로 해시가 안정적이다.
 */
@Entity
@Table(name = "variety_master")
class VarietyMaster(

    @Column(nullable = false, length = 2)
    val lclsfCd: String,

    @Column(nullable = false, length = 20)
    var lclsfNm: String,

    @Column(nullable = false, length = 2)
    val mclsfCd: String,

    @Column(nullable = false, length = 20)
    var mclsfNm: String,

    @Column(nullable = false, length = 2)
    val sclsfCd: String,

    /** 실측 12,175행 중 22행이 null 로 온다. 코드는 오는데 이름이 비어 있다. */
    @Column(length = 20)
    var sclsfNm: String?,
) {
    /**
     * `bigserial`. DB 가 매기므로 `persist` 전에는 null 이다.
     * 밖에서 못 바꾸도록 `protected set` 을 건다 — id 를 손으로 넣는 경로는 없어야 한다.
     * (JPA 플러그인이  클래스를 open 으로 만들기 때문에 private set 은 쓸 수 없다.)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    /** 자연키. 이 셋이 같으면 같은 품종이다. */
    private val naturalKey get() = Triple(lclsfCd, mclsfCd, sclsfCd)

    override fun equals(other: Any?): Boolean =
        this === other || (other is VarietyMaster && naturalKey == other.naturalKey)

    override fun hashCode(): Int = naturalKey.hashCode()

    override fun toString(): String =
        "VarietyMaster(id=$id, $lclsfCd/$mclsfCd/$sclsfCd, $sclsfNm)"
}
