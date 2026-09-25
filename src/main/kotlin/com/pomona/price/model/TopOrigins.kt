package com.pomona.price.model

import java.math.BigDecimal
import java.time.YearMonth

/**
 * 품종 하나의 주요 산지. 품종 페이지는 [total] 을 기본으로 보여주고, 물량 막대에서 달을 누르면 [byMonth] 의 그 달로 바꾼다.
 *
 * 둘 다 주는 이유: 기간 합계는 "원래 어디서 나나"(1위도 10% 안팎으로 퍼진다),
 * 한 달은 "그 달엔 어디서 오나"(블루베리 9월은 천안·서천이 90%)다.
 */
data class TopOrigins(
    val varietyId: Long,
    /** 조회 기간 전체 합계 상위 5곳, 물량 많은 순 */
    val total: List<OriginVolume>,
    /** 달마다 상위 5곳, 물량 많은 순. 거래가 없는 달은 들어가지 않는다 */
    val byMonth: Map<YearMonth, List<OriginVolume>>,
)

/** 품목 하나의 주요 산지. 품목 안의 품종 전부(기타·소량 포함)를 합쳐 [TopOrigins] 와 같은 방식으로 뽑는다. */
data class ItemTopOrigins(
    val lclsfCd: String,
    val mclsfCd: String,
    val total: List<OriginVolume>,
    val byMonth: Map<YearMonth, List<OriginVolume>>,
)

data class OriginVolume(
    val plorCd: String,
    /** 수입은 나라 이름(`800US` 미국). 실측에서 코드 `339000` 하나는 이름이 늘 비어 온다 */
    val plorNm: String?,
    /** 물량 합계(kg). 비중은 월별 물량의 합계로 나눠 구한다 */
    val qty: BigDecimal,
)
