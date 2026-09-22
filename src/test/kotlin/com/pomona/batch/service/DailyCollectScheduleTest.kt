package com.pomona.batch.service

import com.pomona.batch.domain.BatchRun
import com.pomona.batch.domain.BatchRunRepository
import com.pomona.datago.katsale.KatSaleClient
import com.pomona.datago.perday.PerDayPriceClient
import com.pomona.price.domain.RetailDailyWriteRepository
import com.pomona.price.domain.WholesaleDailyWriteRepository
import com.pomona.variety.domain.VarietyUpsertRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.LocalDate

/** 스케줄이 어느 날짜·품목을 수집하라고 시키는지만 본다. 수집기 자체는 각자 테스트가 있다. */
class DailyCollectScheduleTest {

    private val 오늘 = LocalDate.of(2026, 9, 21)

    private class 도매대역 : WholesaleCollector(
        mock(KatSaleClient::class.java), mock(VarietyUpsertRepository::class.java),
        mock(WholesaleDailyWriteRepository::class.java), mock(BatchRunRepository::class.java),
    ) {
        val 받은날짜 = mutableListOf<LocalDate>()
        override fun collect(date: LocalDate): BatchRun {
            받은날짜 += date
            return BatchRun("wholesale-daily", date, "{}")
        }
    }

    private class 소매대역 : RetailCollector(
        mock(PerDayPriceClient::class.java), mock(RetailDailyWriteRepository::class.java),
        mock(BatchRunRepository::class.java),
    ) {
        val 받은요청 = mutableListOf<Triple<RetailItem, LocalDate, LocalDate>>()
        override fun collect(item: RetailItem, from: LocalDate, to: LocalDate): BatchRun {
            받은요청 += Triple(item, from, to)
            return BatchRun("retail-daily", to, "{}")
        }
    }

    private val 도매 = 도매대역()
    private val 소매 = 소매대역()
    private val schedule = DailyCollectSchedule(도매, 소매)

    @Test
    fun `도매는 D-1 부터 D-5 까지 다섯 날짜를 수집한다`() {
        // 확정 지연: 전날치만 한 번 받으면 16% 가 영영 빠진다.
        schedule.collect(오늘)

        assertThat(도매.받은날짜).containsExactly(
            LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 18),
            LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 16),
        )
    }

    @Test
    fun `소매는 21품목을 D-5부터 D-1 기간으로 한 번씩 수집한다`() {
        schedule.collect(오늘)

        assertThat(소매.받은요청).hasSize(21)
        assertThat(소매.받은요청.map { it.first }).isEqualTo(RETAIL_ITEMS)
        assertThat(소매.받은요청.map { it.second to it.third })
            .containsOnly(LocalDate.of(2026, 9, 16) to LocalDate.of(2026, 9, 20))
    }

    @Test
    fun `하루 실행 기록은 도매 5건 소매 21건이다`() {
        val runs = schedule.collect(오늘)

        assertThat(runs).hasSize(26)
    }
}
