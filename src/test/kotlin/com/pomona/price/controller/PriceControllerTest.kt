package com.pomona.price.controller

import com.pomona.price.domain.WholesaleDailyWriteRepository
import com.pomona.price.model.WholesaleDailyRow
import com.pomona.variety.domain.VarietyUpsertRepository
import com.pomona.variety.model.VarietyUpsert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 공개 빌드용 API 의 JSON 모양이 프론트가 쓸 수 있게 나오는지 본다. 계산은 각 레포 테스트가 맡는다.
 * 기준일이 DB 의 마지막 거래일이라 2099년 행을 넣으면 그날이 기준일이 된다. 테스트가 끝나면 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PriceControllerTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var varieties: VarietyUpsertRepository
    @Autowired private lateinit var wholesale: WholesaleDailyWriteRepository

    private val garak = "110001"
    private var varietyId = 0L

    /** 2099-12-11 ~ 12-20 열흘 동안 매일 100kg, kg당 1,000원. 기준일은 12-20 */
    @BeforeEach
    fun plant() {
        varietyId = varieties.upsert(VarietyUpsert("ZZ", "시험대분류", "01", "시험품목", "01", "시험품종"))
        (11..20).forEach { day ->
            val date = LocalDate.of(2099, 12, day)
            wholesale.replaceDay(date, garak, listOf(
                WholesaleDailyRow(
                    trdClclnYmd = date, whslMrktCd = garak, varietyId = varietyId,
                    trdSe = "경매", grdCd = "11", grdNm = "특", plorCd = "367000", plorNm = "충청북도 괴산군", unitNm = "kg",
                    totPrc = 100_000, totQty = BigDecimal("100"),
                    lowPrcPerKg = BigDecimal.ONE, highPrcPerKg = BigDecimal.ONE, tradeCount = 1,
                ),
            ))
        }
    }

    @Test
    fun `기준일은 DB 의 마지막 거래일이고 12개월은 그 달을 포함해 거꾸로 센다`() {
        mvc.get("/api/public/period").andExpect {
            status { isOk() }
            jsonPath("$.baseDate") { value("2099-12-20") }
            jsonPath("$.from") { value("2099-01") }
            jsonPath("$.to") { value("2099-12") }
        }
    }

    @Test
    fun `마지막 거래일 가격을 등급별 목록과 함께 준다`() {
        mvc.get("/api/public/prices/latest").andExpect {
            status { isOk() }
            jsonPath("$[0].varietyId") { value(varietyId) }
            jsonPath("$[0].date") { value("2099-12-20") }
            jsonPath("$[0].perKg") { value(1000.00) }
            jsonPath("$[0].grades[0].grdNm") { value("특") }
        }
    }

    @Test
    fun `월별 물량은 달을 연-월 문자열로 원산지를 이름으로 준다`() {
        mvc.get("/api/public/volumes").andExpect {
            status { isOk() }
            jsonPath("$[0].month") { value("2099-12") }
            jsonPath("$[0].origin") { value("DOMESTIC") }
            jsonPath("$[0].qty") { value(1000.000) }
        }
    }

    @Test
    fun `거래일 수는 연-월을 키로 준다`() {
        mvc.get("/api/public/trading-days").andExpect {
            status { isOk() }
            jsonPath("$['2099-12']") { value(10) }
        }
    }

    @Test
    fun `주요 산지는 기간 합계와 연-월을 키로 한 달별 목록을 준다`() {
        mvc.get("/api/public/origins").andExpect {
            status { isOk() }
            jsonPath("$[0].total[0].plorNm") { value("충청북도 괴산군") }
            jsonPath("$[0].byMonth['2099-12'][0].plorCd") { value("367000") }
        }
    }

    @Test
    fun `나머지 공개 경로도 응답한다`() {
        listOf("/api/public/prices/weekly", "/api/public/volumes/items", "/api/public/origins/items").forEach { path ->
            mvc.get(path).andExpect { status { isOk() } }
        }
    }
}
