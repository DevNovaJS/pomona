package com.pomona.batch.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class BatchRunTest {

    @Autowired private lateinit var runs: BatchRunRepository

    private val 날짜 = LocalDate.of(2099, 1, 4)

    private fun 실행() = BatchRun(
        jobName = "wholesale-daily", targetDate = 날짜,
        params = """{"whsl_mrkt_cd":"110001","gds_lclsf_cd":"06"}""",
    )

    @Test
    fun `시작하면 RUNNING 이고 종료시각이 없다`() {
        val run = runs.save(실행())

        assertThat(run.status).isEqualTo(BatchStatus.RUNNING)
        assertThat(run.finishedAt).isNull()
    }

    @Test
    fun `행이 있으면 SUCCESS 로 끝난다`() {
        val run = runs.save(실행())

        run.succeed(rowCount = 598)

        assertThat(run.status).isEqualTo(BatchStatus.SUCCESS)
        assertThat(run.rowCount).isEqualTo(598)
        assertThat(run.finishedAt).isNotNull()
    }

    @Test
    fun `0행이면 실패가 아니라 EMPTY 다`() {
        // 일요일 휴장과 소매 주말 미조사는 정상 결측이다. FAILED 로 남기면 매주 가짜 경보가 울린다.
        val run = runs.save(실행())

        run.succeed(rowCount = 0)

        assertThat(run.status).isEqualTo(BatchStatus.EMPTY)
    }

    @Test
    fun `실패하면 사유가 남는다`() {
        val run = runs.save(실행())

        run.fail("정산정보 오류 [22] 일일 트래픽 초과")

        assertThat(run.status).isEqualTo(BatchStatus.FAILED)
        assertThat(run.message).contains("트래픽 초과")
    }

    @Test
    fun `끝난 실행을 또 끝낼 수 없다`() {
        val run = runs.save(실행())
        run.succeed(1)

        assertThatThrownBy { run.fail("두 번째") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("이미 끝난 실행")
    }

    @Test
    fun `같은 날짜를 여러 번 재수집해도 이력이 전부 남는다`() {
        // 자연키가 없는 유일한 테이블. 중복이 곧 이력이다.
        repeat(3) { runs.save(실행()).succeed(it) }
        runs.flush()

        assertThat(runs.findByJobNameAndTargetDateOrderByIdDesc("wholesale-daily", 날짜)).hasSize(3)
    }

    @Test
    fun `jsonb 파라미터가 그대로 돌아온다`() {
        val saved = runs.save(실행())
        runs.flush()

        assertThat(runs.findById(saved.id!!).get().params).contains("110001")
    }
}
