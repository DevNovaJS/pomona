package com.pomona.batch.service

import com.pomona.batch.domain.BatchRun
import com.pomona.batch.domain.BatchRunRepository
import com.pomona.datago.katsale.KatSaleClient
import com.pomona.datago.perday.PerDayPriceClient
import com.pomona.price.domain.RetailDailyWriteRepository
import com.pomona.price.domain.WholesaleDailyWriteRepository
import com.pomona.variety.domain.VarietyUpsertRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class RangeCollectorTest {

    private class FakeWholesaleCollector : WholesaleCollector(
        mock(KatSaleClient::class.java), mock(VarietyUpsertRepository::class.java),
        mock(WholesaleDailyWriteRepository::class.java), mock(BatchRunRepository::class.java),
    ) {
        val collectedDates = mutableListOf<LocalDate>()

        /** 수집 도중에 멈춰 세워 "돌고 있는 중" 을 만들 때 쓴다. */
        var pause: (() -> Unit)? = null

        override fun collect(date: LocalDate): BatchRun {
            collectedDates += date
            pause?.invoke()
            return BatchRun("wholesale-daily", date, "{}")
        }
    }

    private class FakeRetailCollector : RetailCollector(
        mock(PerDayPriceClient::class.java), mock(RetailDailyWriteRepository::class.java),
        mock(BatchRunRepository::class.java),
    ) {
        val collectedRequests = mutableListOf<Triple<RetailItem, LocalDate, LocalDate>>()
        override fun collect(item: RetailItem, from: LocalDate, to: LocalDate): BatchRun {
            collectedRequests += Triple(item, from, to)
            return BatchRun("retail-daily", to, "{}")
        }
    }

    private val wholesale = FakeWholesaleCollector()
    private val retail = FakeRetailCollector()
    private val collector = RangeCollector(wholesale, retail)

    private val apple = RetailItem("400", "411", "apple")
    private val from = LocalDate.of(2026, 9, 1)
    private val to = LocalDate.of(2026, 9, 3)

    // ── 무엇을 몇 번 부르나 ───────────────────────────────────────────

    @Test
    fun `도매는 기간 안의 날짜를 하루씩 오래된 것부터 수집한다`() {
        collector.collectWholesale(from, LocalDate.of(2026, 9, 4))

        assertThat(wholesale.collectedDates).containsExactly(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2),
            LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 4),
        )
    }

    @Test
    fun `시작일과 종료일이 같으면 하루만 수집한다`() {
        collector.collectWholesale(from, from)

        assertThat(wholesale.collectedDates).containsExactly(from)
    }

    @Test
    fun `소매는 기간 조회가 되므로 품목마다 한 번씩 부른다`() {
        // 1년을 넣어도 품목당 한 번이다. 여러 페이지는 fetchAll 이 알아서 받는다.
        collector.collectRetail(LocalDate.of(2025, 9, 23), LocalDate.of(2026, 9, 22))

        assertThat(retail.collectedRequests).hasSize(21)
        assertThat(retail.collectedRequests.map { it.second to it.third })
            .containsOnly(LocalDate.of(2025, 9, 23) to LocalDate.of(2026, 9, 22))
    }

    @Test
    fun `소매는 품목을 골라 다시 받을 수 있다`() {
        // 백오피스의 "실패 건 재실행" 은 그 품목만 다시 돌린다.
        collector.collectRetail(from, to, listOf(apple))

        assertThat(retail.collectedRequests).hasSize(1)
        assertThat(retail.collectedRequests.single().first).isEqualTo(apple)
    }

    @Test
    fun `도매와 소매를 한 번에 재수집한다`() {
        val runs = collector.collectAll(from, to)

        assertThat(wholesale.collectedDates).hasSize(3)
        assertThat(retail.collectedRequests).hasSize(21)
        assertThat(runs).hasSize(24)
    }

    // ── 한 번에 하나만 ────────────────────────────────────────────────

    /** 첫 도매 수집에서 멈춘 채로 [check] 을 실행한다. 그동안 수집은 "돌고 있는 중" 이다. */
    private fun whileCollecting(check: () -> Unit) {
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        wholesale.pause = {
            entered.countDown()
            proceed.await()
        }
        val job = thread { collector.collectAll(from, to) }
        entered.await()
        try {
            check()
        } finally {
            proceed.countDown()
            job.join()
        }
    }

    @Test
    fun `돌고 있는 중에 다시 요청하면 예외를 던진다`() {
        whileCollecting {
            assertThatThrownBy { collector.collectAll(from, to) }
                .isInstanceOf(CollectAlreadyRunningException::class.java)
            assertThatThrownBy { collector.collectRetail(from, to) }
                .isInstanceOf(CollectAlreadyRunningException::class.java)
        }
    }

    @Test
    fun `스케줄이 쓰는 쪽은 돌고 있으면 건너뛰고 null 을 돌려준다`() {
        whileCollecting {
            assertThat(collector.collectAllIfIdle(from, to)).isNull()
        }
    }

    @Test
    fun `끝나면 다시 수집할 수 있다`() {
        collector.collectAll(from, to)

        assertThat(collector.collectAll(from, to)).isNotEmpty()
    }

    @Test
    fun `수집 도중 예외가 나도 잠금이 풀린다`() {
        wholesale.pause = { error("수집 실패") }
        runCatching { collector.collectAll(from, to) }
        wholesale.pause = null

        assertThat(collector.collectAll(from, to)).isNotEmpty()
    }

    @Test
    fun `동시에 스무 건이 들어와도 하나만 돈다`() {
        val start = CountDownLatch(1)
        val done = CountDownLatch(20)
        val succeeded = AtomicInteger()
        val pool = Executors.newFixedThreadPool(20)
        wholesale.pause = { Thread.sleep(50) }      // 아직 돌고 있는 동안 나머지가 들어오게 한다

        repeat(20) {
            pool.submit {
                start.await()
                runCatching { collector.collectAll(from, to) }.onSuccess { succeeded.incrementAndGet() }
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        pool.shutdown()

        assertThat(succeeded.get()).isEqualTo(1)
    }
}
