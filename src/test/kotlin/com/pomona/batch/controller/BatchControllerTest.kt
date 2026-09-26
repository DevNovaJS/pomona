package com.pomona.batch.controller

import com.pomona.batch.domain.BatchRun
import com.pomona.batch.domain.BatchRunRepository
import com.pomona.batch.service.WHOLESALE_JOB
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * JSON 모양과 상태 코드만 본다. 조회 조건은 [com.pomona.batch.domain.BatchRunQueryTest],
 * 재실행·기간 재수집의 동작은 [com.pomona.batch.service.RangeCollectorTest] 가 맡는다.
 * 재실행과 기간 재수집을 여기서 부르면 실제 공공 API 를 호출하므로 부르지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class BatchControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var batchRunRepository: BatchRunRepository

    private fun failedRun(): BatchRun {
        val run = batchRunRepository.save(BatchRun(WHOLESALE_JOB, LocalDate.of(2099, 1, 5), """{"date":"2099-01-05","market":"110001"}"""))
        run.fail("API 오류")
        return batchRunRepository.save(run)
    }

    @Test
    fun `현황은 수집 중인지와 작업별 최신 성공일 실패 건수를 준다`() {
        mockMvc.get("/api/admin/batch/status").andExpect {
            status { isOk() }
            jsonPath("$.running") { value(false) }
            jsonPath("$.jobs[0].jobName") { value("wholesale-daily") }
            jsonPath("$.jobs[1].jobName") { value("retail-daily") }
            jsonPath("$.jobs[0].unresolvedFailures") { isNumber() }
        }
    }

    @Test
    fun `실패 목록은 params 를 문자열이 아니라 JSON 그대로 준다`() {
        val run = failedRun()

        mockMvc.get("/api/admin/batch/failures").andExpect {
            status { isOk() }
            jsonPath("$[?(@.id == ${run.id})].params.date") { value("2099-01-05") }
            jsonPath("$[?(@.id == ${run.id})].message") { value("API 오류") }
        }
    }

    @Test
    fun `최근 이력에 방금 실행이 맨 앞에 온다`() {
        val run = failedRun()

        mockMvc.get("/api/admin/batch/runs").andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(run.id!!) }
            jsonPath("$[0].status") { value("FAILED") }
        }
    }

    @Test
    fun `없는 실행을 재실행하면 404 와 사유를 준다`() {
        mockMvc.post("/api/admin/batch/runs/-1/retry").andExpect {
            status { isNotFound() }
            jsonPath("$.message") { value("실행 기록이 없다: -1") }
        }
    }

    @Test
    fun `기간 재수집에 날짜가 빠지면 400`() {
        mockMvc.post("/api/admin/batch/collect?from=2099-01-01").andExpect { status { isBadRequest() } }
    }
}
