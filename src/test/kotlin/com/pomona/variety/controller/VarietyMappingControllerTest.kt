package com.pomona.variety.controller

import com.pomona.price.domain.WholesaleDailyWriteRepository
import com.pomona.price.model.WholesaleDailyRow
import com.pomona.variety.domain.RetailVarietyUpsertRepository
import com.pomona.variety.domain.VarietyUpsertRepository
import com.pomona.variety.model.RetailVarietyUpsert
import com.pomona.variety.model.VarietyUpsert
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
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 2099년에 열흘 거래를 넣어 시험 품종을 품종 페이지 대상으로 만든다. 기준일이 DB 의 마지막 거래일이라
 * 그러면 미연결 목록에는 시험 품종만 나온다. 테스트가 끝나면 롤백된다.
 *
 * 품목 이름은 실데이터 소매 품목(사과·배…)과 겹치지 않게 짓는다. 겹치면 실제 소매 품종도 후보로 끼어든다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class VarietyMappingControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var varietyUpsertRepository: VarietyUpsertRepository
    @Autowired private lateinit var retailVarietyUpsertRepository: RetailVarietyUpsertRepository
    @Autowired private lateinit var wholesaleDailyWriteRepository: WholesaleDailyWriteRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private var varietyId = 0L
    private var retailVarietyId = 0L

    @BeforeEach
    fun plant() {
        varietyId = varietyUpsertRepository.upsert(VarietyUpsert("ZZ", "시험대분류", "01", "시험과일", "01", "시험품종"))
        (1..10).forEach { day ->
            val date = LocalDate.of(2099, 1, day)
            wholesaleDailyWriteRepository.replaceDay(date, "110001", listOf(
                WholesaleDailyRow(
                    trdClclnYmd = date, whslMrktCd = "110001", varietyId = varietyId,
                    trdSe = "경매", grdCd = "11", grdNm = "특", plorCd = "367000", plorNm = null, unitNm = "kg",
                    totPrc = 100_000, totQty = BigDecimal("100"),
                    lowPrcPerKg = BigDecimal.ONE, highPrcPerKg = BigDecimal.ONE, tradeCount = 1,
                ),
            ))
        }
        retailVarietyUpsertRepository.upsertAll(listOf(RetailVarietyUpsert("999", "시험부류", "999", "시험과일", "01", "시험품종")))
        retailVarietyId = jdbcTemplate.queryForObject("select id from retail_variety where ctgry_cd = '999'", Long::class.java)!!
    }

    private fun map(retailVarietyId: Long?) = mockMvc.put("/api/admin/mappings/$varietyId") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"retailVarietyId": $retailVarietyId}"""
    }

    @Test
    fun `아직 안 본 품종은 미연결 목록에 후보와 함께 나온다`() {
        mockMvc.get("/api/admin/mappings/unmapped").andExpect {
            status { isOk() }
            jsonPath("$[0].variety.id") { value(varietyId) }
            jsonPath("$[0].candidates[0].id") { value(retailVarietyId) }
            jsonPath("$[0].candidates[0].vrtyNm") { value("시험품종") }
        }
    }

    @Test
    fun `짝을 지으면 미연결 목록에서 빠지고 매핑 목록에 나온다`() {
        map(retailVarietyId).andExpect {
            status { isOk() }
            jsonPath("$.retailVariety.id") { value(retailVarietyId) }
        }

        mockMvc.get("/api/admin/mappings/unmapped").andExpect { jsonPath("$.length()") { value(0) } }
        mockMvc.get("/api/admin/mappings").andExpect {
            jsonPath("$[0].varietyId") { value(varietyId) }
            jsonPath("$[0].retailVariety.vrtyNm") { value("시험품종") }
        }
    }

    @Test
    fun `소매에 없음으로 확인해도 미연결 목록에서 빠진다`() {
        map(null).andExpect {
            status { isOk() }
            jsonPath("$.retailVariety") { value(null) }
        }

        mockMvc.get("/api/admin/mappings/unmapped").andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `이미 짝지은 품종은 다시 고르면 바뀐다`() {
        map(null)

        map(retailVarietyId).andExpect { jsonPath("$.retailVariety.id") { value(retailVarietyId) } }
    }

    @Test
    fun `매핑을 지우면 다시 미연결로 돌아간다`() {
        map(retailVarietyId)

        mockMvc.delete("/api/admin/mappings/$varietyId").andExpect { status { isNoContent() } }
        mockMvc.get("/api/admin/mappings/unmapped").andExpect { jsonPath("$[0].variety.id") { value(varietyId) } }
    }

    @Test
    fun `없는 소매 품종이면 400 없는 품종이면 404 없는 매핑을 지우면 404`() {
        map(-1).andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("없는 소매 품종이다: -1") }
        }
        mockMvc.put("/api/admin/mappings/-1") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"retailVarietyId": null}"""
        }.andExpect { status { isNotFound() } }
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
