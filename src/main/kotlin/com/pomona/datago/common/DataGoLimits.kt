package com.pomona.datago.common

/**
 * 공공데이터포털 API 공통 제약.
 *
 * 가격 API 는 명세에 최대 1000 이 명시돼 있고, 정산정보는 명시가 없으나
 * 실호출로 1000 이 동작하는 것을 확인했다.
 */
const val MAX_ROWS_PER_PAGE = 1000
