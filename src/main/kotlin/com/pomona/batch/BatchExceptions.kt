package com.pomona.batch

import com.pomona.common.NotFoundException

/** 이미 수집이 돌고 있는데 또 시작하려 할 때. */
class CollectAlreadyRunningException : IllegalStateException("이미 수집이 돌고 있다")

/** 재실행하려는 실행 기록이 없을 때. */
class BatchRunNotFoundException(id: Long) : NotFoundException("실행 기록이 없다: $id")
