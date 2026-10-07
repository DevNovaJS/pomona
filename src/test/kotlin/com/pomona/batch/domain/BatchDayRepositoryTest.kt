package com.pomona.batch.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

/** 로컬 DB 에 실제 수집 이력(2025~2026년)이 있으므로 그보다 뒤인 2099년 날짜만 쓴다. 최근 날짜부터 주므로 시험 날짜가 맨 앞에 온다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(BatchDayRepository::class)
class BatchDayRepositoryTest {

    @Autowired private lateinit var batchRunRepository: BatchRunRepository
    @Autowired private lateinit var batchDayRepository: BatchDayRepository

    private fun wholesale(day: Int, rowCount: Int): BatchRun {
        val run = batchRunRepository.save(BatchRun("wholesale-daily", LocalDate.of(2099, 1, day), """{"date":"2099-01-$day"}"""))
        run.succeed(rowCount)
        return batchRunRepository.save(run)
    }

    private fun retail(day: Int, item: String, rowCount: Int): BatchRun {
        val run = batchRunRepository.save(
            BatchRun("retail-daily", LocalDate.of(2099, 1, day), """{"from":"2099-01-01","to":"2099-01-$day","item":"$item"}"""),
        )
        run.succeed(rowCount)
        return batchRunRepository.save(run)
    }

    @Test
    fun `하루 안에서 작업 품목마다 마지막 실행만 남기고 실행 횟수를 붙인다`() {
        wholesale(5, rowCount = 100)
        val lastWholesale = wholesale(5, rowCount = 120)
        retail(5, "411", rowCount = 10)
        val lastApple = retail(5, "411", rowCount = 11)
        val pear = retail(5, "412", rowCount = 20)

        val day = batchDayRepository.findDays(null).first()

        assertThat(day.targetDate).isEqualTo(LocalDate.of(2099, 1, 5))
        // 도매 먼저, 소매는 수집한 순
        assertThat(day.runs.map { it.run.id }).containsExactly(lastWholesale.id, lastApple.id, pear.id)
        assertThat(day.runs.map { it.attempts }).containsExactly(2, 2, 1)
    }

    @Test
    fun `수집 대상일 10일씩 최근 날짜부터 주고 before 로 다음 장을 준다`() {
        (1..12).forEach { wholesale(it, rowCount = 1) }

        val first = batchDayRepository.findDays(null)
        val next = batchDayRepository.findDays(first.last().targetDate)

        assertThat(first.map { it.targetDate.dayOfMonth }).containsExactly(12, 11, 10, 9, 8, 7, 6, 5, 4, 3)
        assertThat(next.take(2).map { it.targetDate }).containsExactly(LocalDate.of(2099, 1, 2), LocalDate.of(2099, 1, 1))
    }
}
