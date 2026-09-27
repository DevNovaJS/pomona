package com.pomona.variety.controller

import com.pomona.variety.domain.RetailVarietyUpsertRepository
import com.pomona.variety.domain.VarietyUpsertRepository
import com.pomona.variety.model.RetailVarietyUpsert
import com.pomona.variety.model.VarietyUpsert
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

/** 시험 정산 품종(대분류 ZZ) 하나와 시험 소매 품종(부류 999) 둘을 넣고 짝짓는다. 테스트가 끝나면 롤백된다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class VarietyMappingControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var varietyUpsertRepository: VarietyUpsertRepository
    @Autowired private lateinit var retailVarietyUpsertRepository: RetailVarietyUpsertRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private var varietyId = 0L
    private var retailVarietyId = 0L
    private var otherRetailVarietyId = 0L

    @BeforeEach
    fun plant() {
        varietyId = varietyUpsertRepository.upsert(VarietyUpsert("ZZ", "시험대분류", "01", "시험과일", "01", "시험품종"))
        retailVarietyUpsertRepository.upsertAll(listOf(
            RetailVarietyUpsert("999", "시험부류", "999", "시험과일", "01", "시험품종"),
            RetailVarietyUpsert("999", "시험부류", "999", "시험과일", "02", "다른품종"),
        ))
        retailVarietyId = retailVarietyIdOf("01")
        otherRetailVarietyId = retailVarietyIdOf("02")
    }

    private fun retailVarietyIdOf(vrtyCd: String): Long =
        jdbcTemplate.queryForObject("select id from retail_variety where ctgry_cd = '999' and vrty_cd = ?", Long::class.java, vrtyCd)!!

    private fun map(retailVarietyId: Long, varietyId: Long = this.varietyId) = mockMvc.put("/api/admin/mappings/$varietyId") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"retailVarietyId": $retailVarietyId}"""
    }

    @Test
    fun `짝을 지으면 매핑 목록에 나온다`() {
        map(retailVarietyId).andExpect {
            status { isOk() }
            jsonPath("$.retailVariety.id") { value(retailVarietyId) }
        }

        mockMvc.get("/api/admin/mappings").andExpect {
            jsonPath("$[0].varietyId") { value(varietyId) }
            jsonPath("$[0].retailVariety.vrtyNm") { value("시험품종") }
        }
    }

    @Test
    fun `이미 짝지은 품종을 다시 연결하면 새 짝으로 바뀌고 매핑은 하나로 남는다`() {
        map(retailVarietyId)

        map(otherRetailVarietyId).andExpect { jsonPath("$.retailVariety.id") { value(otherRetailVarietyId) } }
        val count = jdbcTemplate.queryForObject("select count(*) from variety_retail_mapping where variety_id = ?", Int::class.java, varietyId)
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `매핑을 지우면 목록에서 빠진다`() {
        map(retailVarietyId)

        mockMvc.delete("/api/admin/mappings/$varietyId").andExpect { status { isNoContent() } }
        mockMvc.get("/api/admin/mappings").andExpect {
            jsonPath("$[?(@.varietyId == $varietyId)]") { isEmpty() }
        }
    }

    @Test
    fun `소매 품종 없이 보내면 400`() {
        mockMvc.put("/api/admin/mappings/$varietyId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"retailVarietyId": null}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `없는 소매 품종이면 400 없는 품종이면 404 없는 매핑을 지우면 404`() {
        map(-1).andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("없는 소매 품종이다: -1") }
        }
        map(retailVarietyId, varietyId = -1).andExpect { status { isNotFound() } }
        mockMvc.delete("/api/admin/mappings/$varietyId").andExpect { status { isNotFound() } }
    }

    @Test
    fun `소매 품종 목록을 준다`() {
        mockMvc.get("/api/admin/mappings/retail-varieties").andExpect {
            status { isOk() }
            jsonPath("$[?(@.id == $retailVarietyId)].vrtyNm") { value("시험품종") }
        }
    }
}
