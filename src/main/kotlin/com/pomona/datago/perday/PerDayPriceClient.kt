package com.pomona.datago.perday

import com.pomona.datago.DataGoUriFactory
import com.pomona.datago.common.DataGoResponse
import com.pomona.datago.common.lastPageOf
import com.pomona.datago.common.orThrow
import com.pomona.datago.perday.model.PriceItem
import com.pomona.datago.perday.model.PriceRequest
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.client.RestClient

private const val PATH = "perDay/price"

class PerDayPriceClient(
    private val restClient: RestClient,
    private val uriFactory: DataGoUriFactory,
) {
    fun fetchPage(request: PriceRequest): DataGoResponse<PriceItem> =
        restClient.get()
            .uri(uriFactory.build(PATH, request.toParams()))
            .retrieve()
            .body(object : ParameterizedTypeReference<DataGoResponse<PriceItem>>() {})
            .orThrow("가격", request)

    /**
     * 요청 범위의 모든 페이지를 받아 이어 붙인다. 수집은 범위를 통째로 지우고 다시 넣으므로
     * 넘기는 행이 그 범위의 전부여야 한다. 매일 수집(5일)은 한 페이지로 끝나고, 백필(1년)은 여러 페이지다.
     */
    fun fetchAll(request: PriceRequest): List<PriceItem> {
        val first = fetchPage(request)
        val lastPage = lastPageOf(first.totalCount, request.numOfRows)

        return first.items + ((request.pageNo + 1)..lastPage)
            .flatMap { fetchPage(request.copy(pageNo = it)).items }
    }
}
