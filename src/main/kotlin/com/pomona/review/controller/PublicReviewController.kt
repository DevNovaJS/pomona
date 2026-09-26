package com.pomona.review.controller

import com.pomona.review.model.ReviewResponse
import com.pomona.review.service.ReviewService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 공개 빌드용. 리뷰 목록·상세 페이지와 품종 페이지의 "이 품종 리뷰" 가 이 목록 하나를 나눠 쓴다. */
@RestController
@RequestMapping("/api/public")
class PublicReviewController(private val reviewService: ReviewService) {

    /** 리뷰 전부. 최근에 먹은 순이고 리뷰마다 그날 도매 시세가 붙는다. */
    @GetMapping("/reviews")
    fun reviews(): List<ReviewResponse> = reviewService.findAll()
}
