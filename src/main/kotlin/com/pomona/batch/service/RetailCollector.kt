package com.pomona.batch.service

import com.pomona.batch.domain.BatchRun
import com.pomona.batch.domain.BatchRunRepository
import com.pomona.datago.perday.PerDayPriceClient
import com.pomona.datago.perday.model.PriceRequest
import com.pomona.price.domain.RetailDailyWriteRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/** 수집 대상 소매 품목. 부류 없이 품목코드만 주면 0행이 오므로 둘을 같이 들고 다닌다. */
data class RetailItem(val ctgryCd: String, val itemCd: String, val name: String)

/**
 * 소매 조사 가격을 품목 하나 × 기간 단위로 받아 저장하고 실행 기록을 남긴다.
 *
 * 도매와 달리 접지 않으므로 받은 행이 그대로 저장된다. 트랜잭션을 걸지 않는 이유는 [WholesaleCollector] 와 같다.
 */
@Service
class RetailCollector(
    private val perDayPriceClient: PerDayPriceClient,
    private val retail: RetailDailyWriteRepository,
    private val runs: BatchRunRepository,
) {

    /** [item] 의 [from]~[to] 조사값을 수집한다. 실패해도 예외를 던지지 않고 FAILED 로 기록해 돌려준다. */
    fun collect(item: RetailItem, from: LocalDate, to: LocalDate): BatchRun {
        val request = PriceRequest(from = from, to = to, categoryCode = item.ctgryCd, itemCode = item.itemCd)
        val run = runs.save(BatchRun(jobName = JOB_NAME, targetDate = to, params = paramsOf(request)))
        try {
            val items = perDayPriceClient.fetchAll(request)
            // 지우는 범위를 요청 객체에서 그대로 꺼낸다. 받아온 범위와 지우는 범위가 어긋날 수 없다.
            val rowCount = retail.replaceRange(
                request.seCd, request.categoryCode, request.itemCode, request.from, request.to,
                items.map { it.toRow() },
            )
            run.succeed(rowCount)
        } catch (e: Exception) {
            log.error("소매 수집 실패: {} {}~{}", item.name, from, to, e)
            run.fail(e.message ?: e::class.java.name)
        }
        return runs.save(run)
    }

    private fun paramsOf(request: PriceRequest): String =
        """{"from":"${request.from}","to":"${request.to}","se":"${request.seCd}",""" +
            """"category":"${request.categoryCode}","item":"${request.itemCode}"}"""
}

private const val JOB_NAME = "retail-daily"

private val log = LoggerFactory.getLogger(RetailCollector::class.java)

/**
 * 수집 대상 21개 품목. 실호출로 확정한 목록이다.
 * 딸기·수박·참외·토마토·멜론은 과일류(400)가 아니라 채소류(200)에 있다.
 */
val RETAIL_ITEMS: List<RetailItem> = listOf(
    RetailItem("400", "411", "사과"),
    RetailItem("400", "412", "배"),
    RetailItem("400", "413", "복숭아"),
    RetailItem("400", "414", "포도"),
    RetailItem("400", "415", "감귤"),
    RetailItem("400", "416", "단감"),
    RetailItem("400", "418", "바나나"),
    RetailItem("400", "419", "참다래"),
    RetailItem("400", "420", "파인애플"),
    RetailItem("400", "421", "오렌지"),
    RetailItem("400", "424", "레몬"),
    RetailItem("400", "425", "체리"),
    RetailItem("400", "428", "망고"),
    RetailItem("400", "429", "블루베리"),
    RetailItem("400", "430", "아보카도"),
    RetailItem("400", "437", "키위"),
    RetailItem("200", "221", "수박"),
    RetailItem("200", "222", "참외"),
    RetailItem("200", "225", "토마토"),
    RetailItem("200", "226", "딸기"),
    RetailItem("200", "257", "멜론"),
)
