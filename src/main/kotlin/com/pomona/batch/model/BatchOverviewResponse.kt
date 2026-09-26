package com.pomona.batch.model

import java.time.LocalDate

/** 백오피스 배치 관리의 현황. */
data class BatchOverviewResponse(
    /** 지금 수집이 돌고 있는지. 뒤에서 도는 기간 재수집이 끝났는지 여기서 본다 */
    val running: Boolean,
    val jobs: List<JobStatusResponse>,
)

data class JobStatusResponse(
    val jobName: String,
    /** 마지막으로 성공한 수집 대상 날짜. 성공이 한 번도 없으면 null */
    val lastSuccessDate: LocalDate?,
    /** 아직 안 풀린 실패 건수 */
    val unresolvedFailures: Int,
)
