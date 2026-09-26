package com.pomona.common

/** 찾는 대상이 없다. [GlobalExceptionHandler] 가 404 로 바꾼다. */
abstract class NotFoundException(message: String) : RuntimeException(message)

/** 요청 값이 잘못됐다. [GlobalExceptionHandler] 가 400 으로 바꾼다. */
abstract class BadRequestException(message: String) : RuntimeException(message)
