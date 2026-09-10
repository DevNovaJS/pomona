package com.pomona.datago.perday

import com.pomona.datago.DataGoUriFactory
import com.pomona.datago.common.DataGoResponse
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
}
