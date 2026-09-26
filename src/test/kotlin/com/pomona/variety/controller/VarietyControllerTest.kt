package com.pomona.variety.controller

import com.pomona.price.domain.WholesaleDailyWriteRepository
import com.pomona.price.model.WholesaleDailyRow
import com.pomona.variety.domain.VarietyUpsertRepository
import com.pomona.variety.model.VarietyUpsert
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

/** 기준일이 DB 의 마지막 거래일이라 2099년 행을 넣으면 그날이 기준일이 된다. 테스트가 끝나면 롤백된다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VarietyControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var varietyUpsertRepository: VarietyUpsertRepository
    @Autowired private lateinit var wholesaleDailyWriteRepository: WholesaleDailyWriteRepository

    private val garak = "110001"

    private fun row(varietyId: Long, date: LocalDate, qty: String) = WholesaleDailyRow(
        trdClclnYmd = date, whslMrktCd = garak, varietyId = varietyId,
        trdSe = "경매", grdCd = "11", grdNm = "특", plorCd = "367000", plorNm = null, unitNm = "kg",
        totPrc = 1_000, totQty = BigDecimal(qty),
        lowPrcPerKg = BigDecimal.ONE, highPrcPerKg = BigDecimal.ONE, tradeCount = 1,
    )

    @Test
    fun `품목은 거래가 조금만 있어도 나오고 품종은 페이지 조건을 채운 것만 나온다`() {
        val paged = varietyUpsertRepository.upsert(VarietyUpsert("ZZ", "시험대분류", "01", "시험사과", "01", "시험홍로"))
        val small = varietyUpsertRepository.upsert(VarietyUpsert("ZZ", "시험대분류", "02", "시험배", "01", "시험신고"))
        (11..20).forEach { day ->
            val date = LocalDate.of(2099, 12, day)
            val rows = listOf(row(paged, date, "100")) + if (day == 20) listOf(row(small, date, "1")) else emptyList()
            wholesaleDailyWriteRepository.replaceDay(date, garak, rows)
        }

        mockMvc.get("/api/public/items").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].mclsfNm") { value("시험사과") }
            jsonPath("$[1].mclsfNm") { value("시험배") }
        }
        mockMvc.get("/api/public/varieties").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(paged) }
            jsonPath("$[0].sclsfNm") { value("시험홍로") }
        }
    }
}
