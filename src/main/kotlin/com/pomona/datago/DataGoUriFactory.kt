package com.pomona.datago

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 공공데이터포털 요청 URI 를 조립한다.
 *
 * 파라미터 이름에 `[`, `]`, `:` 가 들어가는데 (`cond[trd_clcln_ymd::EQ]`) 이 문자들은
 * RFC 3986 상 쿼리에 그대로 넣을 수 없어 직접 인코딩한다. serviceKey 는 이미
 * 인코딩된 값이므로 다시 인코딩하지 않는다.
 */
class DataGoUriFactory(
    private val baseUrl: String,
    private val serviceKey: String,
) {
    fun build(path: String, params: Map<String, String>): URI {
        val query = params.entries.joinToString("&") { (name, value) ->
            "${encode(name)}=${encode(value)}"
        }
        return URI.create("$baseUrl/$path?serviceKey=$serviceKey&$query")
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}
