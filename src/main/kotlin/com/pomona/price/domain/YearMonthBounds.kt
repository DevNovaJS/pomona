package com.pomona.price.domain

import java.time.LocalDate
import java.time.YearMonth

/** 달 단위 조회의 시작일. `trd_clcln_ymd >= ?` 에 넣는다. */
internal fun YearMonth.firstDay(): LocalDate = atDay(1)

/** 달 단위 조회의 끝. `trd_clcln_ymd < ?` 에 넣는다. "다음 달 1일 미만" 이라 월말 날짜 계산(28~31일)을 신경 쓸 필요가 없다. */
internal fun YearMonth.nextFirstDay(): LocalDate = plusMonths(1).atDay(1)
