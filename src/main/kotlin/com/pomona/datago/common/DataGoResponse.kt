package com.pomona.datago.common

data class DataGoResponse<T>(
    val response: Payload<T>,
) {
    val totalCount: Int get() = response.body.totalCount

    val items: List<T> get() = response.body.items.item

    /** 게이트웨이가 정상 처리했는지. 건수가 0이어도 결과 코드가 0이면 성공(결측)이다. */
    val isSuccess: Boolean get() = response.header.resultCode == "0"

    data class Payload<T>(
        val header: Header,
        val body: Body<T>,
    )

    data class Header(
        val resultCode: String,
        val resultMsg: String,
    )

    data class Body<T>(
        val pageNo: Int,
        val numOfRows: Int,
        val totalCount: Int,
        val items: Items<T>,
    )

    data class Items<T>(
        val item: List<T>,
    )
}

/**
 * 응답이 쓸 수 있는 상태인지 확인하고 그대로 돌려준다.
 *
 * 게이트웨이는 한도 초과나 키 오류에도 HTTP 200 을 주므로 본문의 결과 코드로 판정해야 한다.
 * 수신자가 nullable 인 이유는 RestClient 의 body() 가 null 을 줄 수 있기 때문이다.
 */
fun <T> DataGoResponse<T>?.orThrow(api: String, request: Any): DataGoResponse<T> {
    val response = this ?: error("$api 응답 본문이 비었다: $request")
    check(response.isSuccess) {
        "$api 오류 [${response.response.header.resultCode}] " +
            "${response.response.header.resultMsg}: $request"
    }
    return response
}
