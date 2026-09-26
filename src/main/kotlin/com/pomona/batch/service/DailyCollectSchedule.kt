package com.pomona.batch.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

/**
 * 매일 정해진 시각에 최근 며칠치를 다시 수집한다. **언제 도는지만** 정하고 수집은 [RangeCollector] 가 한다.
 *
 * 하루 호출 수는 도매 15콜(5일 x 부류 2개, 페이지 수만큼) + 소매 21콜이다.
 */
@Component
class DailyCollectSchedule(private val rangeCollector: RangeCollector) {

    /**
     * D-1 ~ D-5 를 다시 수집한다. 하루치를 한 번만 받으면 안 되는 이유는 확정 지연이다.
     * 실측에서 전날 시점의 정산정보는 약 16% 가 아직 올라오지 않았고 D-4 무렵 채워졌다.
     *
     * 백필이 도는 중이면 **건너뛴다.** 기다렸다 돌지 않는다.
     * 그날 못 받은 날짜는 다음 날 D-2 로 다시 받으므로 빠지지 않는다.
     */
    @Scheduled(cron = "0 0 6 * * *", zone = SEOUL)
    fun runDaily() {
        val today = LocalDate.now(ZoneId.of(SEOUL))
        rangeCollector.collectAllIfIdle(today.minusDays(RECOLLECT_DAYS), today.minusDays(1))
    }
}

private const val SEOUL = "Asia/Seoul"

/** 다시 받는 날짜 수. 확정이 D-4 무렵이라 하루 여유를 더했다. */
private const val RECOLLECT_DAYS = 5L
