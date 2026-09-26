package com.pomona.variety

import com.pomona.common.BadRequestException
import com.pomona.common.NotFoundException

class VarietyNotFoundException(varietyId: Long) : NotFoundException("품종이 없다: $varietyId")

class UnknownRetailVarietyException(retailVarietyId: Long) : BadRequestException("없는 소매 품종이다: $retailVarietyId")

class VarietyMappingNotFoundException(varietyId: Long) : NotFoundException("매핑이 없다: $varietyId")
