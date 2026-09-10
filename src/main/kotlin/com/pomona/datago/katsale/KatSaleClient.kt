package com.pomona.datago.katsale

import com.pomona.datago.DataGoUriFactory
import com.pomona.datago.common.DataGoResponse
import com.pomona.datago.common.orThrow
import com.pomona.datago.katsale.model.TradeItem
import com.pomona.datago.katsale.model.TradeRequest
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.client.RestClient

private const val PATH = "katSale/trades"

class KatSaleClient(
    private val restClient: RestClient,
    private val uriFactory: DataGoUriFactory,
) {
    fun fetchPage(request: TradeRequest): DataGoResponse<TradeItem> =
        restClient.get()
            .uri(uriFactory.build(PATH, request.toParams()))
            .retrieve()
            .body(object : ParameterizedTypeReference<DataGoResponse<TradeItem>>() {})
            .orThrow("정산정보", request)

    /**
     * 요청한 페이지부터 마지막 페이지까지 이어서 받아 합친다.
     *
     * 정산정보는 하루치를 통째로 모아야 집계가 가능하다. 같은 집계 키의 원본 행이
     * 페이지 경계에 걸칠 수 있어 페이지별로 접으면 부분합이 저장된다.
     */
    fun fetchAll(request: TradeRequest): List<TradeItem> {
        val first = fetchPage(request)
        val lastPage = lastPageOf(first.totalCount, request.numOfRows)

        return first.items + ((request.pageNo + 1)..lastPage)
            .flatMap { fetchPage(request.copy(pageNo = it)).items }
    }

    /** 올림 나눗셈. totalCount 가 0 이면 0 페이지가 되어 추가 호출이 없다. */
    private fun lastPageOf(totalCount: Int, numOfRows: Int): Int =
        (totalCount + numOfRows - 1) / numOfRows
}
