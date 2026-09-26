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
import java.util.concurrent.TimeUnit
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

    private val wholesaleCollector = FakeWholesaleCollector()
    private val retailCollector = FakeRetailCollector()
    private val rangeCollector = RangeCollector(wholesaleCollector, retailCollector)

    private val apple = RetailItem("400", "411", "apple")
    private val from = LocalDate.of(2026, 9, 1)
    private val to = LocalDate.of(2026, 9, 3)

    // ── 무엇을 몇 번 부르나 ───────────────────────────────────────────

    @Test
    fun `도매는 기간 안의 날짜를 하루씩 오래된 것부터 수집한다`() {
        rangeCollector.collectWholesale(from, LocalDate.of(2026, 9, 4))

        assertThat(wholesaleCollector.collectedDates).containsExactly(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2),
            LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 4),
        )
    }

    @Test
    fun `시작일과 종료일이 같으면 하루만 수집한다`() {
        rangeCollector.collectWholesale(from, from)

        assertThat(wholesaleCollector.collectedDates).containsExactly(from)
    }

    @Test
    fun `소매는 기간 조회가 되므로 품목마다 한 번씩 부른다`() {
        // 1년을 넣어도 품목당 한 번이다. 여러 페이지는 fetchAll 이 알아서 받는다.
        rangeCollector.collectRetail(LocalDate.of(2025, 9, 23), LocalDate.of(2026, 9, 22))

        assertThat(retailCollector.collectedRequests).hasSize(21)
        assertThat(retailCollector.collectedRequests.map { it.second to it.third })
            .containsOnly(LocalDate.of(2025, 9, 23) to LocalDate.of(2026, 9, 22))
    }

    @Test
    fun `소매는 품목을 골라 다시 받을 수 있다`() {
        // 백오피스의 "실패 건 재실행" 은 그 품목만 다시 돌린다.
        rangeCollector.collectRetail(from, to, listOf(apple))

        assertThat(retailCollector.collectedRequests).hasSize(1)
        assertThat(retailCollector.collectedRequests.single().first).isEqualTo(apple)
    }

    @Test
    fun `도매와 소매를 한 번에 재수집한다`() {
        val runs = rangeCollector.collectAll(from, to)

        assertThat(wholesaleCollector.collectedDates).hasSize(3)
        assertThat(retailCollector.collectedRequests).hasSize(21)
        assertThat(runs).hasSize(24)
    }

    // ── 한 번에 하나만 ────────────────────────────────────────────────

    /** 첫 도매 수집에서 멈춘 채로 [check] 을 실행한다. 그동안 수집은 "돌고 있는 중" 이다. */
    private fun whileCollecting(check: () -> Unit) {
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        wholesaleCollector.pause = {
            entered.countDown()
            proceed.await()
        }
        val job = thread { rangeCollector.collectAll(from, to) }
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
            assertThatThrownBy { rangeCollector.collectAll(from, to) }
                .isInstanceOf(CollectAlreadyRunningException::class.java)
            assertThatThrownBy { rangeCollector.collectRetail(from, to) }
                .isInstanceOf(CollectAlreadyRunningException::class.java)
        }
    }

    @Test
    fun `스케줄이 쓰는 쪽은 돌고 있으면 건너뛰고 null 을 돌려준다`() {
        whileCollecting {
            assertThat(rangeCollector.collectAllIfIdle(from, to)).isNull()
        }
    }

    @Test
    fun `끝나면 다시 수집할 수 있다`() {
        rangeCollector.collectAll(from, to)

        assertThat(rangeCollector.collectAll(from, to)).isNotEmpty()
    }

    @Test
    fun `수집 도중 예외가 나도 잠금이 풀린다`() {
        wholesaleCollector.pause = { error("수집 실패") }
        runCatching { rangeCollector.collectAll(from, to) }
        wholesaleCollector.pause = null

        assertThat(rangeCollector.collectAll(from, to)).isNotEmpty()
    }

    // ── 뒤에서 돌리기 ─────────────────────────────────────────────────

    /** 뒤에서 도는 수집이 끝날 때까지 기다린다. 5초 넘게 안 끝나면 실패. */
    private fun awaitIdle() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (rangeCollector.isRunning) {
            check(System.nanoTime() < deadline) { "수집이 끝나지 않는다" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `기간 재수집은 뒤에서 돌고 바로 돌아온다`() {
        val proceed = CountDownLatch(1)
        wholesaleCollector.pause = { proceed.await() }

        rangeCollector.startAll(from, to)

        // 첫 도매 수집에서 멈춰 있는데도 여기까지 왔다
        assertThat(rangeCollector.isRunning).isTrue()
        proceed.countDown()
        awaitIdle()
        assertThat(wholesaleCollector.collectedDates).hasSize(3)
        assertThat(retailCollector.collectedRequests).hasSize(21)
    }

    @Test
    fun `뒤에서 도는 중에는 다른 수집을 시작할 수 없다`() {
        val proceed = CountDownLatch(1)
        wholesaleCollector.pause = { proceed.await() }
        rangeCollector.startAll(from, to)

        assertThatThrownBy { rangeCollector.startAll(from, to) }.isInstanceOf(CollectAlreadyRunningException::class.java)
        assertThatThrownBy { rangeCollector.collectAll(from, to) }.isInstanceOf(CollectAlreadyRunningException::class.java)

        proceed.countDown()
        awaitIdle()
    }

    // ── 재실행 ────────────────────────────────────────────────────────

    @Test
    fun `도매 실행을 재실행하면 그 날짜 하루를 다시 수집한다`() {
        rangeCollector.retry(BatchRun(WHOLESALE_JOB, from, """{"date":"$from"}"""))

        assertThat(wholesaleCollector.collectedDates).containsExactly(from)
    }

    @Test
    fun `소매 실행을 재실행하면 params 의 품목과 기간으로 다시 수집한다`() {
        val params = """{"from":"$from","to":"$to","se":"01","category":"400","item":"411"}"""

        rangeCollector.retry(BatchRun(RETAIL_JOB, to, params))

        val (item, requestFrom, requestTo) = retailCollector.collectedRequests.single()
        assertThat(item.itemCd).isEqualTo("411")
        assertThat(requestFrom to requestTo).isEqualTo(from to to)
    }

    @Test
    fun `재실행도 수집이 돌고 있으면 예외를 던진다`() {
        whileCollecting {
            assertThatThrownBy { rangeCollector.retry(BatchRun(WHOLESALE_JOB, from, "{}")) }
                .isInstanceOf(CollectAlreadyRunningException::class.java)
        }
    }

    @Test
    fun `동시에 스무 건이 들어와도 하나만 돈다`() {
        val start = CountDownLatch(1)
        val done = CountDownLatch(20)
        val succeeded = AtomicInteger()
        val pool = Executors.newFixedThreadPool(20)
        wholesaleCollector.pause = { Thread.sleep(50) }      // 아직 돌고 있는 동안 나머지가 들어오게 한다

        repeat(20) {
            pool.submit {
                start.await()
                runCatching { rangeCollector.collectAll(from, to) }.onSuccess { succeeded.incrementAndGet() }
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        pool.shutdown()

        assertThat(succeeded.get()).isEqualTo(1)
    }
}
