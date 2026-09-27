package com.pomona.variety.controller

import com.pomona.variety.domain.VarietyUpsertRepository
import com.pomona.variety.model.VarietyUpsert
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

/** 테스트가 끝나면 롤백된다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AdminVarietyControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var varietyUpsertRepository: VarietyUpsertRepository

    @Test
    fun `거래가 없어 페이지가 없는 품종도 코드 순으로 전부 나온다`() {
        val second = varietyUpsertRepository.upsert(VarietyUpsert("ZZ", "시험대분류", "01", "시험포도", "02", "시험레드샤인"))
        val first = varietyUpsertRepository.upsert(VarietyUpsert("ZZ", "시험대분류", "01", "시험포도", "01", "시험샤인"))

        mockMvc.get("/api/admin/varieties").andExpect {
            status { isOk() }
            jsonPath("$[-2].id") { value(first) }
            jsonPath("$[-1].id") { value(second) }
            jsonPath("$[-1].sclsfNm") { value("시험레드샤인") }
        }
    }
}
