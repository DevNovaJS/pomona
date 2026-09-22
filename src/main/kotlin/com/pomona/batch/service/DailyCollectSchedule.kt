package com.pomona.batch.service

import com.pomona.batch.domain.BatchRun
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

/**
 * 매일 정해진 시각에 최근 며칠치를 다시 수집한다.
 *
 * 하루 호출 수는 도매 15콜(5일 x 06·08 두 부류, 부류당 페이지 수만큼) + 소매 21콜이다.
 */
@Component
class DailyCollectSchedule(
    private val wholesale: WholesaleCollector,
    private val retail: RetailCollector,
) {

    @Scheduled(cron = "0 0 6 * * *", zone = SEOUL)
    fun runDaily() {
        collect(LocalDate.now(ZoneId.of(SEOUL)))
    }

    /**
     * [today] 기준 D-1 ~ D-5 를 다시 수집한다.
     *
     * 하루치를 한 번만 받으면 안 되는 이유는 확정 지연이다. 실측에서 전날 시점의 정산정보는
     * 약 16% 가 아직 올라오지 않았고 D-4 무렵 채워졌다. 같은 날짜를 다시 받아 덮어쓴다.
     *
     * 수집기는 실패해도 예외를 던지지 않고 FAILED 로 기록하므로, 한 날짜가 실패해도 나머지는 계속 돈다.
     */
    fun collect(today: LocalDate): List<BatchRun> {
        val from = today.minusDays(RECOLLECT_DAYS.toLong())
        val to = today.minusDays(1)
        log.info("일별 수집 시작: {} ~ {}", from, to)

        val wholesaleRuns = (1..RECOLLECT_DAYS).map { wholesale.collect(today.minusDays(it.toLong())) }
        // 소매는 기간 조회가 되므로 품목마다 한 번이면 된다.
        val retailRuns = RETAIL_ITEMS.map { retail.collect(it, from, to) }

        return wholesaleRuns + retailRuns
    }
}

private const val SEOUL = "Asia/Seoul"

/** 다시 받는 날짜 수. 확정이 D-4 무렵이라 하루 여유를 더했다. */
private const val RECOLLECT_DAYS = 5

private val log = LoggerFactory.getLogger(DailyCollectSchedule::class.java)
