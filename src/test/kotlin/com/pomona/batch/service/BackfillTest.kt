package com.pomona.batch.service

import com.pomona.batch.domain.BatchStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.system.measureTimeMillis

/**
 * 과거 N개월을 한 번 채운다. 테스트가 아니라 **일회성 작업**이라 평소 빌드에서는 건너뛴다.
 *
 * ```
 * POMONA_BACKFILL=true POMONA_BACKFILL_MONTHS=6 ./gradlew test --tests "*BackfillTest"
 * ```
 *
 * 개월 수를 안 주면 12개월이다. 실제 공공데이터 API 를 호출하고 로컬 DB 에 쓴다.
 * 12개월이면 약 1,245번 호출에 20분 안팎, 6개월이면 그 절반 정도다.
 * 7번에서 백오피스 재수집 API 가 생기면 이 파일은 지운다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "POMONA_BACKFILL", matches = "true")
class BackfillTest {

    @Autowired private lateinit var range: RangeCollector
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `과거 N개월을 수집한다`() {
        val months = System.getenv("POMONA_BACKFILL_MONTHS")?.toLong() ?: 12
        val to = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1)
        val from = to.minusMonths(months)
        println(">>> 백필 ${months}개월: $from ~ $to")

        val runs: List<com.pomona.batch.domain.BatchRun>
        val elapsed = measureTimeMillis { runs = range.collectAll(from, to) }

        val byStatus = runs.groupingBy { it.status }.eachCount()
        println(">>> 걸린 시간 ${Duration.ofMillis(elapsed).toMinutes()}분 ${Duration.ofMillis(elapsed).toSecondsPart()}초")
        println(">>> 실행 ${runs.size}건 " + BatchStatus.entries.joinToString(" / ") { "$it ${byStatus[it] ?: 0}" })
        runs.filter { it.status == BatchStatus.FAILED }.take(5)
            .forEach { println(">>> 실패: ${it.jobName} ${it.targetDate} ${it.message?.take(120)}") }

        listOf(
            "variety_master" to "select count(*) from variety_master",
            "wholesale_daily" to "select count(*) from wholesale_daily",
            "retail_daily" to "select count(*) from retail_daily",
        ).forEach { (name, sql) ->
            println(">>> $name ${jdbc.queryForObject(sql, Long::class.java)}행")
        }
        println(">>> 도매 날짜 " + jdbc.queryForObject(
            "select count(distinct trd_clcln_ymd) from wholesale_daily", Int::class.java) + "일")
        println(">>> 소매 날짜 " + jdbc.queryForObject(
            "select count(distinct exmn_ymd) from retail_daily", Int::class.java) + "일")
    }
}
