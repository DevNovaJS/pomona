package com.pomona.review.controller

import com.pomona.review.model.ReviewRequest
import com.pomona.review.model.ReviewResponse
import com.pomona.review.service.ReviewService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 백오피스 리뷰 작성·수정·삭제. */
@RestController
@RequestMapping("/api/admin/reviews")
class ReviewController(private val reviewService: ReviewService) {

    @GetMapping
    fun findAll(): List<ReviewResponse> = reviewService.findAll()

    @GetMapping("/{id}")
    fun find(@PathVariable id: Long): ReviewResponse = reviewService.find(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: ReviewRequest): ReviewResponse = reviewService.create(request)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: ReviewRequest): ReviewResponse = reviewService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) {
        reviewService.delete(id)
    }
}
