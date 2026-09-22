package com.pomona.batch.service

import com.pomona.batch.domain.BatchRun
import com.pomona.batch.domain.BatchRunRepository
import com.pomona.datago.katsale.KatSaleClient
import com.pomona.datago.katsale.model.TradeRequest
import com.pomona.datago.katsale.model.toVarieties
import com.pomona.datago.katsale.model.toWholesaleRows
import com.pomona.price.domain.WholesaleDailyWriteRepository
import com.pomona.variety.domain.VarietyUpsertRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * 정산정보 하루치를 받아 도매 집계로 저장하고 실행 기록을 남긴다.
 *
 * 트랜잭션을 걸지 않는다. 걸면 저장 중 예외로 롤백될 때 "실패했다" 는 실행 기록까지 같이 사라진다.
 * 실행 기록은 시작·종료 때 각각 저장하고, 데이터 쓰기는 [WholesaleDailyWriteRepository.replaceDay]
 * 안의 트랜잭션 하나로 묶인다. API 호출은 DB 쓰기 전에 끝낸다.
 */
@Service
class WholesaleCollector(
    private val katSaleClient: KatSaleClient,
    private val varieties: VarietyUpsertRepository,
    private val wholesale: WholesaleDailyWriteRepository,
    private val batchRunRepository: BatchRunRepository,
) {

    /** [date] 하루치를 수집한다. 실패해도 예외를 던지지 않고 FAILED 로 기록해 돌려준다. */
    fun collect(date: LocalDate): BatchRun {
        val run = batchRunRepository.save(BatchRun(jobName = JOB_NAME, targetDate = date, params = paramsOf(date)))
        try {
            val items = CATEGORIES.flatMap { katSaleClient.fetchAll(TradeRequest(date, GARAK, it)) }
            val varietyIds = varieties.upsertAll(items.toVarieties())
            val rowCount = wholesale.replaceDay(date, GARAK, items.toWholesaleRows(varietyIds))
            run.succeed(rowCount)
        } catch (e: Exception) {
            log.error("도매 수집 실패: {}", date, e)
            run.fail(e.message ?: e::class.java.name)
        }
        return batchRunRepository.save(run)
    }

    private fun paramsOf(date: LocalDate): String =
        """{"date":"$date","market":"$GARAK","categories":[${CATEGORIES.joinToString(",") { "\"$it\"" }}]}"""
}

private const val JOB_NAME = "wholesale-daily"

/** 가락시장. 수집 범위는 가락 한 곳이다. */
private const val GARAK = "110001"

/** 과실류, 과일과채류 */
private val CATEGORIES = listOf("06", "08")

private val log = LoggerFactory.getLogger(WholesaleCollector::class.java)
