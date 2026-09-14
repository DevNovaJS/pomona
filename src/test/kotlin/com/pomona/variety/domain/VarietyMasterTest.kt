package com.pomona.variety.domain

import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class VarietyMasterTest {

    @Autowired private lateinit var em: EntityManager

    private fun 시험용품종(sclsfCd: String = "01", sclsfNm: String? = "시험홍로") = VarietyMaster(
        lclsfCd = "ZZ", lclsfNm = "시험대분류",
        mclsfCd = "ZZ", mclsfNm = "시험중분류",
        sclsfCd = sclsfCd, sclsfNm = sclsfNm,
    )

    @Test
    fun `저장하면 DB 가 id 를 매겨준다`() {
        val variety = 시험용품종()
        assertThat(variety.id).isNull()

        em.persist(variety)
        em.flush()

        assertThat(variety.id).isNotNull()
    }

    @Test
    fun `저장한 값이 그대로 돌아온다`() {
        val variety = 시험용품종()
        em.persist(variety)
        em.flush()
        em.clear()

        val found = em.find(VarietyMaster::class.java, variety.id)

        assertThat(found.lclsfCd).isEqualTo("ZZ")
        assertThat(found.mclsfNm).isEqualTo("시험중분류")
        assertThat(found.sclsfNm).isEqualTo("시험홍로")
    }

    @Test
    fun `품종명이 null 이어도 저장된다`() {
        // 실측: 12,175행 중 22행이 gds_sclsf_nm 을 null 로 준다.
        val variety = 시험용품종(sclsfNm = null)

        em.persist(variety)
        em.flush()
        em.clear()

        assertThat(em.find(VarietyMaster::class.java, variety.id).sclsfNm).isNull()
    }

    @Test
    fun `영속화 전후로 hashCode 가 변하지 않는다`() {
        // data class 였다면 id 가 equals·hashCode 에 들어가 persist 시점에 값이 바뀐다.
        val variety = 시험용품종()
        val before = variety.hashCode()

        em.persist(variety)
        em.flush()

        assertThat(variety.hashCode()).isEqualTo(before)
    }

    @Test
    fun `자연키가 같으면 같은 품종으로 본다`() {
        assertThat(시험용품종()).isEqualTo(시험용품종())
        assertThat(시험용품종(sclsfCd = "02")).isNotEqualTo(시험용품종(sclsfCd = "03"))
    }
}
