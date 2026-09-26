package com.pomona.price.service

import com.pomona.price.domain.WholesaleDailyRepository
import com.pomona.price.model.BuildPeriod
import org.springframework.stereotype.Service

/** 공개 빌드의 기준 기간을 정한다. 기준일 규칙은 여기 한 곳에만 둔다. */
@Service
class BuildPeriodService(private val wholesaleDailyRepository: WholesaleDailyRepository) {

    /** 기준일은 DB 의 마지막 거래일. 12개월 범위는 [BuildPeriod] 가 계산한다. */
    fun current(): BuildPeriod = BuildPeriod(wholesaleDailyRepository.findLastTradeDate())
}
