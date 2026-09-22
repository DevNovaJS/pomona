package com.pomona.batch.service

import com.pomona.batch.domain.BatchRun
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/** 이미 수집이 돌고 있는데 또 시작하려 할 때. */
class CollectAlreadyRunningException : IllegalStateException("이미 수집이 돌고 있다")

/**
 * 기간을 받아 수집한다. 매일 도는 스케줄도, 백필도, 백오피스 재실행도 전부 이리로 들어온다.
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
    private val wholesale: WholesaleCollector,
    private val retail: RetailCollector,
) {

    private val running = AtomicBoolean(false)

    /** 도매·소매를 한 번에. 백필과 백오피스 재수집이 이걸 쓴다. 이미 돌고 있으면 예외. */
    fun collectAll(from: LocalDate, to: LocalDate): List<BatchRun> =
        exclusively { wholesaleRange(from, to) + retailRange(from, to, RETAIL_ITEMS) }
            ?: throw CollectAlreadyRunningException()

    /** [collectAll] 과 같지만 이미 돌고 있으면 예외 대신 건너뛰고 null. 스케줄이 이걸 쓴다. */
    fun collectAllIfIdle(from: LocalDate, to: LocalDate): List<BatchRun>? =
        exclusively { wholesaleRange(from, to) + retailRange(from, to, RETAIL_ITEMS) }

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
        // compareAndSet 은 "지금 false 면 true 로" 를 쪼갤 수 없는 한 동작으로 한다.
        // if (!running) { running = true } 로 쓰면 두 스레드가 같이 통과할 수 있다.
        if (!running.compareAndSet(false, true)) {
            log.info("이미 수집이 돌고 있어 건너뛴다")
            return null
        }
        try {
            return block()
        } finally {
            running.set(false)
        }
    }

    private fun wholesaleRange(from: LocalDate, to: LocalDate): List<BatchRun> {
        log.info("도매 수집: {} ~ {}", from, to)
        return datesOf(from, to).map { wholesale.collect(it) }
    }

    private fun retailRange(from: LocalDate, to: LocalDate, items: List<RetailItem>): List<BatchRun> {
        log.info("소매 수집: {} ~ {}, 품목 {}개", from, to, items.size)
        return items.map { retail.collect(it, from, to) }
    }

    private fun datesOf(from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { it <= to }
            .toList()
}

private val log = LoggerFactory.getLogger(RangeCollector::class.java)
