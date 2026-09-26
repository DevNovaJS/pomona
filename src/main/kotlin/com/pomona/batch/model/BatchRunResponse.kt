package com.pomona.batch.model

import com.fasterxml.jackson.annotation.JsonRawValue
import com.pomona.batch.domain.BatchRun
import com.pomona.batch.domain.BatchStatus
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 실행 기록 한 건. 엔티티를 그대로 내보내지 않고 이걸로 옮긴다 — 엔티티의 params 는 JSON 을 담은 문자열이라
 * 그대로 내보내면 따옴표로 감싼 문자열이 된다. [params] 는 JSON 그대로 끼워 넣는다.
 *
 * 결측(EMPTY)은 실패가 아니다. 주말·공휴일인지는 화면이 [targetDate] 의 요일로 가른다.
 */
data class BatchRunResponse(
    val id: Long,
    val jobName: String,
    val targetDate: LocalDate,
    val status: BatchStatus,
    @get:JsonRawValue val params: String,
    val rowCount: Int,
    val message: String?,
    val startedAt: OffsetDateTime,
    val finishedAt: OffsetDateTime?,
)

fun BatchRun.toResponse() = BatchRunResponse(
    id = id!!, jobName = jobName, targetDate = targetDate, status = status, params = params,
    rowCount = rowCount, message = message, startedAt = startedAt, finishedAt = finishedAt,
)
