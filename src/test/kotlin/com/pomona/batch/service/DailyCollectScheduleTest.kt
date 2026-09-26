package com.pomona.batch.service

import com.pomona.batch.domain.BatchRun
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.LocalDate
import java.time.ZoneId

/** 스케줄은 "언제, 어느 기간을" 넘기는지만 본다. 수집 자체는 RangeCollector 테스트가 본다. */
class DailyCollectScheduleTest {

    private class FakeRangeCollector : RangeCollector(mock(WholesaleCollector::class.java), mock(RetailCollector::class.java)) {
        var receivedRange: Pair<LocalDate, LocalDate>? = null
        override fun collectAllIfIdle(from: LocalDate, to: LocalDate): List<BatchRun>? {
            receivedRange = from to to
            return emptyList()
        }
    }

    private val rangeCollector = FakeRangeCollector()
    private val dailyCollectSchedule = DailyCollectSchedule(rangeCollector)

    @Test
    fun `D-5 부터 D-1 까지를 수집하라고 넘긴다`() {
        // 확정 지연: 전날치만 한 번 받으면 16% 가 영영 빠진다.
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

        dailyCollectSchedule.runDaily()

        assertThat(rangeCollector.receivedRange).isEqualTo(today.minusDays(5) to today.minusDays(1))
    }

    @Test
    fun `겹치면 건너뛰는 쪽을 쓴다`() {
        dailyCollectSchedule.runDaily()

        // collectAll 이 아니라 collectAllIfIdle 을 불러야 백필 중에 예외가 터지지 않는다
        assertThat(rangeCollector.receivedRange).isNotNull()
    }
}
