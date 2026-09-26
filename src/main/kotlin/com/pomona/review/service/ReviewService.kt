package com.pomona.review.service

import com.pomona.review.domain.Review
import com.pomona.review.domain.ReviewRepository
import com.pomona.review.model.ReviewRequest
import com.pomona.review.model.ReviewResponse
import com.pomona.review.model.toResponse
import com.pomona.variety.domain.VarietyMaster
import com.pomona.variety.domain.VarietyRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 찾는 리뷰가 없을 때. */
class ReviewNotFoundException(id: Long) : NoSuchElementException("리뷰가 없다: $id")

/** 요청에 적힌 품종이 없을 때. 요청 본문이 잘못된 것이라 404 가 아니라 400 이다. */
class UnknownVarietyException(varietyId: Long) : IllegalArgumentException("없는 품종이다: $varietyId")

/** 백오피스 리뷰 작성·수정·삭제와 조회. */
@Service
@Transactional(readOnly = true)
class ReviewService(
    private val reviewRepository: ReviewRepository,
    private val varietyRepository: VarietyRepository,
) {

    fun findAll(): List<ReviewResponse> = reviewRepository.findAllByOrderByEatenDateDescIdDesc().map { it.toResponse() }

    fun find(id: Long): ReviewResponse = findReview(id).toResponse()

    @Transactional
    fun create(request: ReviewRequest): ReviewResponse {
        val review = Review(
            variety = findVariety(request.varietyId),
            eatenDate = request.eatenDate, title = request.title, store = request.store, origin = request.origin,
            price = request.price, weightGram = request.weightGram, rating = request.rating, body = request.body,
        )
        return reviewRepository.save(review).toResponse()
    }

    /** 바뀐 값은 트랜잭션이 끝날 때 JPA 가 알아서 UPDATE 한다(변경 감지). save 를 다시 부르지 않는다. */
    @Transactional
    fun update(id: Long, request: ReviewRequest): ReviewResponse {
        val review = findReview(id)
        review.update(
            variety = findVariety(request.varietyId),
            eatenDate = request.eatenDate, title = request.title, store = request.store, origin = request.origin,
            price = request.price, weightGram = request.weightGram, rating = request.rating, body = request.body,
        )
        return review.toResponse()
    }

    @Transactional
    fun delete(id: Long) {
        reviewRepository.delete(findReview(id))
    }

    private fun findReview(id: Long): Review = reviewRepository.findByIdOrNull(id) ?: throw ReviewNotFoundException(id)

    private fun findVariety(varietyId: Long): VarietyMaster =
        varietyRepository.findByIdOrNull(varietyId) ?: throw UnknownVarietyException(varietyId)
}
