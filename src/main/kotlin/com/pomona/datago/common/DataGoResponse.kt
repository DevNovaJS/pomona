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
