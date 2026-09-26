package com.pomona.review.controller

import com.pomona.price.domain.WholesaleDailyWriteRepository
import com.pomona.price.model.WholesaleDailyRow
import com.pomona.variety.domain.VarietyUpsertRepository
import com.pomona.variety.model.VarietyUpsert
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
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
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 테스트 트랜잭션으로 감싸지 않는다. 감싸면 수정 내용이 테스트가 끝날 때까지 DB 에 안 나가서
 * "수정할 때 제약에 걸리면 400" 이 실제로 나는지 볼 수 없다. 대신 끝나면 시험 품종(대분류 ZZ)과 그 리뷰·도매 행을 지운다.
 *
 * 도매 행은 2099년에만 넣는다. 쓰기 레포가 날짜 단위로 지우고 넣으므로 실데이터 날짜를 쓰면 그날 실데이터가 지워진다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ReviewControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var varietyUpsertRepository: VarietyUpsertRepository
    @Autowired private lateinit var wholesaleDailyWriteRepository: WholesaleDailyWriteRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val jsonMapper = JsonMapper.builder().build()
    private var varietyId = 0L

    @BeforeEach
    fun plantVariety() {
        varietyId = varietyUpsertRepository.upsert(VarietyUpsert("ZZ", "시험대분류", "01", "시험바나나", "01", "시험품종"))
    }

    @AfterEach
    fun cleanUp() {
        jdbcTemplate.update("delete from review where variety_id in (select id from variety_master where lclsf_cd = 'ZZ')")
        jdbcTemplate.update("delete from wholesale_daily where variety_id in (select id from variety_master where lclsf_cd = 'ZZ')")
        jdbcTemplate.update("delete from variety_master where lclsf_cd = 'ZZ'")
    }

    /** 요청 본문. 바꾸고 싶은 값만 넘긴다. [weightGram] 가 null 이면 필드를 아예 빼고 보낸다. */
    private fun request(
        rating: Int = 4,
        price: Int = 25_000,
        weightGram: Int? = 2_000,
        title: String = "달았던 바나나",
        varietyId: Long = this.varietyId,
        eatenDate: String = "2099-01-10",
    ): String = jsonMapper.writeValueAsString(
        buildMap {
            put("varietyId", varietyId)
            put("eatenDate", eatenDate)
            put("title", title)
            put("store", "동네 마트")
            put("origin", "필리핀")
            put("price", price)
            if (weightGram != null) {
                put("weightGram", weightGram)
            }
            put("rating", rating)
            put("body", "많이 달다")
        },
    )

    /** 2099-01-09 에 kg당 3,000원에 거래된 것으로 도매 행을 넣는다. */
    private fun plantWholesale() {
        val date = LocalDate.of(2099, 1, 9)
        wholesaleDailyWriteRepository.replaceDay(date, "110001", listOf(
            WholesaleDailyRow(
                trdClclnYmd = date, whslMrktCd = "110001", varietyId = varietyId,
                trdSe = "경매", grdCd = "11", grdNm = "특", plorCd = "367000", plorNm = null, unitNm = "kg",
                totPrc = 30_000, totQty = BigDecimal("10"),
                lowPrcPerKg = BigDecimal.ONE, highPrcPerKg = BigDecimal.ONE, tradeCount = 1,
            ),
        ))
    }

    private fun create(body: String = request()): Long {
        val response = mockMvc.post("/api/admin/reviews") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andReturn().response.contentAsString
        return jsonMapper.readTree(response)["id"].asLong()
    }

    @Test
    fun `작성하면 201 과 품종 이름 kg당 가격을 준다`() {
        mockMvc.post("/api/admin/reviews") {
            contentType = MediaType.APPLICATION_JSON
            content = request()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.itemName") { value("시험바나나") }
            jsonPath("$.pricePerKg") { value(12500) }
        }
    }

    @Test
    fun `무게를 빼고 작성하면 kg당 가격이 비어 있다`() {
        val id = create(request(weightGram = null))

        mockMvc.get("/api/admin/reviews/$id").andExpect {
            status { isOk() }
            jsonPath("$.weightGram") { value(null) }
            jsonPath("$.pricePerKg") { value(null) }
        }
    }

    @Test
    fun `수정하면 바뀐 값이 저장된다`() {
        val id = create()

        mockMvc.put("/api/admin/reviews/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = request(title = "다시 먹어 보니 싱거웠다", rating = 2)
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/admin/reviews/$id").andExpect {
            jsonPath("$.title") { value("다시 먹어 보니 싱거웠다") }
            jsonPath("$.rating") { value(2) }
        }
    }

    @Test
    fun `별점이 5를 넘으면 걸린 제약 이름과 함께 400`() {
        mockMvc.post("/api/admin/reviews") {
            contentType = MediaType.APPLICATION_JSON
            content = request(rating = 6)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value(containsString("ck_review_rating")) }
        }
    }

    @Test
    fun `수정할 때도 제약에 걸리면 400`() {
        val id = create()

        mockMvc.put("/api/admin/reviews/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = request(price = 0)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value(containsString("ck_review_price")) }
        }
    }

    @Test
    fun `없는 품종으로 작성하면 400`() {
        mockMvc.post("/api/admin/reviews") {
            contentType = MediaType.APPLICATION_JSON
            content = request(varietyId = -1)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("없는 품종이다: -1") }
        }
    }

    @Test
    fun `삭제하면 204 이고 그 뒤로는 404`() {
        val id = create()

        mockMvc.delete("/api/admin/reviews/$id").andExpect { status { isNoContent() } }
        mockMvc.get("/api/admin/reviews/$id").andExpect {
            status { isNotFound() }
            jsonPath("$.message") { value("리뷰가 없다: $id") }
        }
    }

    @Test
    fun `작성 응답에 그날 도매 시세가 붙는다`() {
        plantWholesale()

        mockMvc.post("/api/admin/reviews") {
            contentType = MediaType.APPLICATION_JSON
            content = request(eatenDate = "2099-01-10")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.marketPrice.date") { value("2099-01-09") }
            jsonPath("$.marketPrice.perKg") { value(3000) }
        }
    }

    @Test
    fun `7일 안에 거래가 없으면 도매 시세가 비어 있다`() {
        plantWholesale()

        mockMvc.post("/api/admin/reviews") {
            contentType = MediaType.APPLICATION_JSON
            content = request(eatenDate = "2099-01-20")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.marketPrice") { value(null) }
        }
    }

    @Test
    fun `공개 리뷰 목록에도 그날 도매 시세가 붙는다`() {
        plantWholesale()
        val id = create()

        mockMvc.get("/api/public/reviews").andExpect {
            status { isOk() }
            jsonPath("$[?(@.id == $id)].marketPrice.date") { value("2099-01-09") }
        }
    }

    @Test
    fun `작성 중 미리보기는 시세를 주고 거래가 없으면 204`() {
        plantWholesale()

        mockMvc.get("/api/admin/reviews/market-price?varietyId=$varietyId&eatenDate=2099-01-10").andExpect {
            status { isOk() }
            jsonPath("$.date") { value("2099-01-09") }
        }
        mockMvc.get("/api/admin/reviews/market-price?varietyId=$varietyId&eatenDate=2099-01-20").andExpect {
            status { isNoContent() }
        }
    }
}
