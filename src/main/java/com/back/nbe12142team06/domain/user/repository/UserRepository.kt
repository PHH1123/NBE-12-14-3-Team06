package com.back.nbe12142team06.domain.user.repository

import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.UserStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

// username, 해시 파라미터는 요청 DTO(String?)와 BlindIndexer(String?) 값을 그대로 받으므로 nullable 로 둔다
interface UserRepository : JpaRepository<User, Long> {

    // username으로 튜플 검색
    fun findByUsername(username: String?): Optional<User>

    // DB에 해당 username이 존재하는지 확인
    fun existsByUsername(username: String?): Boolean

    // 이메일 중복 확인 (블라인드 인덱스 비교)
    fun existsByEmailHash(emailHash: String?): Boolean

    // 전화번호 중복 확인 (블라인드 인덱스 비교)
    fun existsByPhoneNumHash(phoneNumHash: String?): Boolean

    // 회원정보 수정 시 이메일 중복 체크용 (본인 제외)
    fun existsByEmailHashAndIdNot(emailHash: String?, id: Long): Boolean

    // 회원정보 수정 시 전화번호 중복 체크용 (본인 제외)
    fun existsByPhoneNumHashAndIdNot(phoneNumHash: String?, id: Long): Boolean

    // [ADMIN] 탈퇴 회원 포함 단건 조회 (@SQLRestriction 을 피하려고 네이티브 쿼리 사용)
    @Query(value = "SELECT * FROM users WHERE id = :id", nativeQuery = true)
    fun findByIdIncludingDeleted(@Param("id") id: Long): Optional<User>

    // [ADMIN] 탈퇴 회원 포함 단건 조회 + 비관적 잠금 (관리자 탈퇴용)
    @Query(value = "SELECT * FROM users WHERE id = :id FOR UPDATE", nativeQuery = true)
    fun findByIdIncludingDeletedWithLock(@Param("id") id: Long): Optional<User>

    // [ADMIN] 탈퇴 회원 포함 목록 조회
    @Query(
        value = "SELECT * FROM users order by id DESC",
        countQuery = "SELECT COUNT(*) FROM users",
        nativeQuery = true,
    )
    fun findAllIncludingDeleted(pageable: Pageable): Page<User>

    // 상태별 조회
    fun findAllByUserStatus(userStatus: UserStatus): List<User>

    // 비관적 잠금 조회 (탈퇴, 정지, 지원 수락/취소 동시성 제어)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    fun findByIdWithLock(@Param("id") id: Long): User?
}