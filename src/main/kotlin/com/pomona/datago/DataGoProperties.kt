package com.pomona.datago

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 공공데이터포털 연동 설정.
 *
 * serviceKey 는 **인코딩된(URL-encoded)** 값을 넣는다. DataGoUriFactory 가 다시
 * 인코딩하지 않고 그대로 쿼리에 싣는다.
 */
@ConfigurationProperties(prefix = "datago")
data class DataGoProperties(
    val serviceKey: String,
    val baseUrl: String = "https://apis.data.go.kr/B552845",
)
