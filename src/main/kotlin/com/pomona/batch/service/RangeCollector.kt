package com.pomona.batch.service

import com.pomona.batch.CollectAlreadyRunningException
import com.pomona.batch.domain.BatchRun
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 기간을 받아 수집한다. 매일 도는 스케줄도, 백필도, 백오피스 재실행·기간 재수집도 전부 이리로 들어온다.
 *
 * **한 번에 하나만 돈다.** 겹치면 같은 날짜를 동시에 지우고 넣게 되어 순서에 따라 결과가 달라지고,
 * 20분짜리 작업이 둘씩 돌면서 API 호출도 두 배가 된다. 막는 단위는 날짜 하나가 아니라 **작업 전체**다.
 *
 * 잠금은 **이 프로세스 안에서만** 유효하다. 백엔드가 컨테이너 하나로 뜨는 동안은 이걸로 충분하고,
 * 서버를 여러 대로 늘리면 DB 잠금(PostgreSQL 어드바이저리 락)으로 바꿔야 한다.
 * 프로세스가 죽으면 잠금도 같이 사라지므로 잠긴 채 남는 일이 없다.
 */
@Service
class RangeCollector(
    private val wholesaleCollector: WholesaleCollector,
    private val retailCollector: RetailCollector,
) {

    private val running = AtomicBoolean(false)

    /** 지금 수집이 돌고 있는지. 백오피스가 뒤에서 도는 기간 재수집이 끝났는지 볼 때 쓴다. */
    val isRunning: Boolean
        get() = running.get()

    /** 도매·소매를 한 번에. 백필이 이걸 쓴다. 이미 돌고 있으면 예외. */
    fun collectAll(from: LocalDate, to: LocalDate): List<BatchRun> =
        exclusively { allRange(from, to) } ?: throw CollectAlreadyRunningException()

    /** [collectAll] 과 같지만 이미 돌고 있으면 예외 대신 건너뛰고 null. 스케줄이 이걸 쓴다. */
    fun collectAllIfIdle(from: LocalDate, to: LocalDate): List<BatchRun>? =
        exclusively { allRange(from, to) }

    /**
     * [collectAll] 을 뒤에서 돌리고 바로 돌아온다. 백오피스의 기간 재수집이 이걸 쓴다 — 1년치는 10분쯤 걸려
     * HTTP 응답을 그동안 붙잡고 있을 수 없다. 잠금은 돌려주기 **전에** 잡으므로 이미 돌고 있으면 바로 예외다.
     * 끝났는지는 [isRunning] 과 실행 이력으로 본다.
     */
    fun startAll(from: LocalDate, to: LocalDate) {
        if (!tryLock()) {
            throw CollectAlreadyRunningException()
        }
        Thread.ofVirtual().name("collect-range").start {
            try {
                allRange(from, to)
            } finally {
                running.set(false)
            }
        }
    }

    /** 실행 기록 하나를 같은 조건으로 다시 돌린다. 백오피스의 재실행 버튼. 이미 돌고 있으면 예외. */
    fun retry(run: BatchRun): BatchRun =
        exclusively {
            when (run.jobName) {
                WHOLESALE_JOB -> wholesaleCollector.collect(run.targetDate)
                RETAIL_JOB -> retailCollector.retry(run)
                else -> error("모르는 작업이다: ${run.jobName}")
            }
        } ?: throw CollectAlreadyRunningException()

    /** 도매만 다시. 도매는 하루 단위 조회만 되므로 날짜를 하나씩 수집한다. */
    fun collectWholesale(from: LocalDate, to: LocalDate): List<BatchRun> =
        exclusively { wholesaleRange(from, to) } ?: throw CollectAlreadyRunningException()

    /**
     * 소매만 다시. 기간 조회가 되므로 품목마다 한 번이면 된다.
     * [items] 를 주면 그 품목만 받는다. 백오피스의 실패 건 재실행이 이 형태다.
     */
    fun collectRetail(from: LocalDate, to: LocalDate, items: List<RetailItem> = RETAIL_ITEMS): List<BatchRun> =
        exclusively { retailRange(from, to, items) } ?: throw CollectAlreadyRunningException()

    /** 놀고 있으면 [block] 을 실행하고, 이미 돌고 있으면 실행하지 않고 null. */
    private fun <T> exclusively(block: () -> T): T? {
        if (!tryLock()) {
            return null
        }
        try {
            return block()
        } finally {
            running.set(false)
        }
    }

    /** 잠금을 잡으면 true. 푸는 건 잡은 쪽이 끝날 때 한다. */
    private fun tryLock(): Boolean {
        // compareAndSet 은 "지금 false 면 true 로" 를 쪼갤 수 없는 한 동작으로 한다.
        // if (!running) { running = true } 로 쓰면 두 스레드가 같이 통과할 수 있다.
        if (!running.compareAndSet(false, true)) {
            log.info("이미 수집이 돌고 있어 건너뛴다")
            return false
        }
        return true
    }

    private fun allRange(from: LocalDate, to: LocalDate): List<BatchRun> =
        wholesaleRange(from, to) + retailRange(from, to, RETAIL_ITEMS)

    private fun wholesaleRange(from: LocalDate, to: LocalDate): List<BatchRun> {
        log.info("도매 수집: {} ~ {}", from, to)
        return datesOf(from, to).map { wholesaleCollector.collect(it) }
    }

    private fun retailRange(from: LocalDate, to: LocalDate, items: List<RetailItem>): List<BatchRun> {
        log.info("소매 수집: {} ~ {}, 품목 {}개", from, to, items.size)
        return items.map { retailCollector.collect(it, from, to) }
    }

    private fun datesOf(from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { it <= to }
            .toList()
}

private val log = LoggerFactory.getLogger(RangeCollector::class.java)
