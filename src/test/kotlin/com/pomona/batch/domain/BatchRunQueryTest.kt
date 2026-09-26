package com.pomona.batch.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

/** 로컬 DB 에 실제 수집 이력이 있으므로 작업 이름을 따로 써서 그것만 본다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class BatchRunQueryTest {

    @Autowired private lateinit var batchRunRepository: BatchRunRepository

    private val job = "test-job"

    /** [day] 날짜를 수집한 실행 한 건을 [status] 로 끝내 저장한다. params 가 같으면 같은 작업의 재시도다. */
    private fun finished(day: Int, status: BatchStatus): BatchRun {
        val run = batchRunRepository.save(BatchRun(job, LocalDate.of(2099, 1, day), """{"date":"2099-01-$day"}"""))
        when (status) {
            BatchStatus.SUCCESS -> run.succeed(rowCount = 10)
            BatchStatus.EMPTY -> run.succeed(rowCount = 0)
            BatchStatus.FAILED -> run.fail("API 오류")
            BatchStatus.RUNNING -> {}
        }
        return batchRunRepository.save(run)
    }

    private fun unresolvedIds() = batchRunRepository.findUnresolvedFailures().filter { it.jobName == job }.map { it.id }

    @Test
    fun `실패한 뒤 같은 조건으로 성공하면 실패 목록에서 빠진다`() {
        finished(5, BatchStatus.FAILED)
        finished(5, BatchStatus.SUCCESS)

        assertThat(unresolvedIds()).isEmpty()
    }

    @Test
    fun `결측으로 끝나도 풀린 것으로 본다`() {
        finished(5, BatchStatus.FAILED)
        finished(5, BatchStatus.EMPTY)

        assertThat(unresolvedIds()).isEmpty()
    }

    @Test
    fun `재시도도 실패하면 마지막 실패 한 건만 남는다`() {
        finished(5, BatchStatus.FAILED)
        val last = finished(5, BatchStatus.FAILED)

        assertThat(unresolvedIds()).containsExactly(last.id)
    }

    @Test
    fun `다른 날짜의 성공은 실패를 풀지 않는다`() {
        val failed = finished(5, BatchStatus.FAILED)
        finished(6, BatchStatus.SUCCESS)

        assertThat(unresolvedIds()).containsExactly(failed.id)
    }

    @Test
    fun `작업별 마지막 성공 날짜는 실패와 결측을 빼고 센다`() {
        finished(2, BatchStatus.SUCCESS)
        finished(5, BatchStatus.SUCCESS)
        finished(7, BatchStatus.EMPTY)
        finished(9, BatchStatus.FAILED)

        val last = batchRunRepository.findLastDates().single { it.jobName == job }

        assertThat(last.targetDate).isEqualTo(LocalDate.of(2099, 1, 5))
    }

    @Test
    fun `최근 이력은 나중에 시작한 것부터 준다`() {
        val first = finished(2, BatchStatus.SUCCESS)
        val second = finished(1, BatchStatus.SUCCESS)

        assertThat(batchRunRepository.findTop50ByOrderByIdDesc().take(2).map { it.id }).containsExactly(second.id, first.id)
    }
}
