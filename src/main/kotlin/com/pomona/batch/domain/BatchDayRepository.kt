package com.pomona.batch.domain

import com.pomona.batch.model.BatchDayResponse
import com.pomona.batch.model.BatchRunResponse
import com.pomona.batch.model.LatestRunResponse
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 백오피스 "최근 실행 이력". 수집 대상일 단위로 10일씩 묶어 준다.
 *
 * 실행 기록 단위로 자르면 소매가 품목마다 한 건이라(하루 21건) 한 장에 하루치도 다 안 들어온다.
 * 같은 날짜를 매일 D-1~D-5 로 다시 긁고 재실행도 쌓이므로, 하루 안에서는 작업·품목마다 마지막 실행만 남기고 몇 번 돌았는지를 붙인다.
 */
@Repository
class BatchDayRepository(private val jdbcTemplate: JdbcTemplate) {

    /** [before] 보다 앞선 수집 대상일 10일, 최근 날짜부터. [before] 가 없으면 가장 최근 날짜부터. */
    fun findDays(before: LocalDate?): List<BatchDayResponse> =
        jdbcTemplate.query(SQL, { rs, _ ->
            LatestRunResponse(
                run = BatchRunResponse(
                    id = rs.getLong("id"),
                    jobName = rs.getString("job_name"),
                    targetDate = rs.getObject("target_date", LocalDate::class.java),
                    status = BatchStatus.valueOf(rs.getString("status")),
                    params = rs.getString("params"),
                    rowCount = rs.getInt("row_count"),
                    message = rs.getString("message"),
                    startedAt = rs.getObject("started_at", OffsetDateTime::class.java),
                    finishedAt = rs.getObject("finished_at", OffsetDateTime::class.java),
                ),
                attempts = rs.getInt("attempts"),
            )
        }, before, before)
            .groupBy { it.run.targetDate }
            .map { (targetDate, runs) -> BatchDayResponse(targetDate, runs) }

    companion object {
        /** 한 장에 담는 수집 대상일 수 */
        const val PAGE_DAYS = 10
    }
}

/**
 * `days` 에서 수집 대상일 10일을 고르고, 그 날짜의 실행을 작업·품목(소매 params 의 item, 도매는 null)마다 묶어
 * 마지막 실행 한 건(`rn = 1`)과 묶음의 실행 횟수를 낸다.
 * 바인딩은 (before, before) — 없으면 null 이라 날짜로 거르지 않는다.
 * 하루 안에서는 도매 먼저(작업 이름 역순), 소매는 수집한 품목 순(id 순).
 */
private val SQL = """
    with days as (
        select distinct target_date
          from batch_run
         where cast(? as date) is null or target_date < cast(? as date)
         order by target_date desc
         limit ${BatchDayRepository.PAGE_DAYS}
    )
    select *
      from (
        select b.*,
               row_number() over same_run as rn,
               count(*) over (partition by b.job_name, b.target_date, b.params ->> 'item') as attempts
          from batch_run b
          join days d on d.target_date = b.target_date
        window same_run as (partition by b.job_name, b.target_date, b.params ->> 'item' order by b.id desc)
      ) latest
     where rn = 1
     order by target_date desc, job_name desc, id
"""
