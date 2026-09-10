package com.pomona.datago

import com.pomona.datago.katsale.KatSaleClient
import com.pomona.datago.perday.PerDayPriceClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(DataGoProperties::class)
class DataGoConfig {

    @Bean
    fun dataGoUriFactory(properties: DataGoProperties): DataGoUriFactory =
        DataGoUriFactory(baseUrl = properties.baseUrl, serviceKey = properties.serviceKey)

    @Bean
    fun katSaleClient(builder: RestClient.Builder, uriFactory: DataGoUriFactory): KatSaleClient =
        KatSaleClient(builder.build(), uriFactory)

    @Bean
    fun perDayPriceClient(builder: RestClient.Builder, uriFactory: DataGoUriFactory): PerDayPriceClient =
        PerDayPriceClient(builder.build(), uriFactory)
}
