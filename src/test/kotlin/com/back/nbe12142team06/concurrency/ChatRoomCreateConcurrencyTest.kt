package com.back.nbe12142team06.concurrency

import com.back.nbe12142team06.DatabaseCleaner
import com.back.nbe12142team06.domain.chat.repository.ChatMessageRepository
import com.back.nbe12142team06.domain.chat.repository.ChatPartRepository
import com.back.nbe12142team06.domain.chat.repository.ChatRoomRepository
import com.back.nbe12142team06.domain.chat.service.ChatService
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * [동시성] 같은 두 사용자 사이에 채팅방이 동시에 여러 번 개설 요청될 때 방이 1개만 생기는지 검증한다.
 *
 * ChatService.createChatRoom() 은 findByUser() 로 기존 방을 찾고 없으면 save() 하는
 * check-then-act 구조다. 두 스레드가 같은 시점에 "없다"를 보면 둘 다 방을 만들 수 있고,
 * ChatPart 의 PK 는 (user_id, chat_room_id) 이므로 방 ID 가 다르면 복합키 충돌도 나지 않는다.
 * 즉 DB 제약이 최종 방어선 역할을 하지 못한다.
 *
 * 이 클래스의 테스트 메서드에는 @Transactional 을 붙이지 않는다.
 * 각 스레드가 실제로 자기 트랜잭션을 커밋해야 경쟁 상태가 재현된다.
 * (테스트에 @Transactional 을 붙이면 모든 스레드가 같은 트랜잭션/커넥션을 공유하게 되어
 * 경쟁 자체가 발생하지 않는다.)
 */
@SpringBootTest
@ActiveProfiles("test")
class ChatRoomCreateConcurrencyTest {

    @Autowired
    private lateinit var chatService: ChatService

    @Autowired
    private lateinit var chatRoomRepository: ChatRoomRepository

    @Autowired
    private lateinit var chatPartRepository: ChatPartRepository

    @Autowired
    private lateinit var chatMessageRepository: ChatMessageRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    // 실시간 전송은 외부(STOMP)로 나가므로 막아 둔다
    @field:MockitoBean
    private lateinit var messagingTemplate: SimpMessagingTemplate

    private var escortId: Long = 0
    private var clientId: Long = 0

    @BeforeEach
    fun setUp() {
        databaseCleaner.clean()
        val tag = System.nanoTime().toString()
        val phoneTag = tag.substring(tag.length - 7)

        val escort = userRepository.save(
            User(
                username = "chat-escort-$tag",
                password = "pw",
                email = "chat-escort-$tag@test.com",
                name = "동행매니저",
                role = Role.ESCORT,
                gender = Gender.FEMALE,
                birthDate = LocalDate.of(1995, 1, 1),
                phoneNum = "010${phoneTag}1",
                region = "수원",
            ),
        )

        val client = userRepository.save(
            User(
                username = "chat-client-$tag",
                password = "pw",
                email = "chat-client-$tag@test.com",
                name = "의뢰인",
                role = Role.CLIENT,
                gender = Gender.MALE,
                birthDate = LocalDate.of(1970, 1, 1),
                phoneNum = "010${phoneTag}2",
                region = "수원",
            ),
        )

        escortId = escort.id
        clientId = client.id
    }

    @AfterEach
    fun tearDown() {
        databaseCleaner.clean()
    }

    @Test
    @DisplayName("[동시성] 같은 상대에게 10개 스레드로 동시에 채팅방을 개설하면 채팅방은 1개만 생겨야 한다")
    fun 같은_방향_동시_채팅방_개설() {
        val result = createConcurrently(threadCount = 10) { chatService.createChatRoom(escortId, clientId) }

        assertRoomCreatedOnce(result)
    }

    @Test
    @DisplayName("[동시성] 두 사용자가 서로에게 동시에 채팅방을 개설하면 채팅방은 1개만 생겨야 한다")
    fun 양방향_동시_채팅방_개설() {
        // findByUser() 는 (user1, user2) 를 집합으로 비교하므로 개설 방향은 결과에 영향을 주지 않는다.
        val result = createConcurrently(threadCount = 10) { index ->
            if (index % 2 == 0) {
                chatService.createChatRoom(escortId, clientId)
            } else {
                chatService.createChatRoom(clientId, escortId)
            }
        }

        assertRoomCreatedOnce(result)
    }

    private fun assertRoomCreatedOnce(result: ConcurrentResult) {
        val rooms = chatRoomRepository.findAll()

        assertThat(rooms)
            .describedAs(
                "채팅방 %d개 생성 (성공 %d, 실패 %d) / 실패 원인: %s",
                rooms.size, result.successCount, result.failureCount, result.failures,
            )
            .hasSize(1)

        // 방 1개에 참여자는 발신자·수신자 2명, 개설 안내 메시지는 1건이어야 한다.
        assertThat(chatPartRepository.findAll()).hasSize(2)
        assertThat(chatMessageRepository.findAll()).hasSize(1)
    }

    private data class ConcurrentResult(
        val successCount: Int,
        val failureCount: Int,
        val failures: List<String>,
    )

    private fun createConcurrently(threadCount: Int, action: (Int) -> Unit): ConcurrentResult {
        val executorService = Executors.newFixedThreadPool(threadCount)
        val readyLatch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)

        val successCount = AtomicInteger()
        val failureCount = AtomicInteger()
        val failures = ConcurrentLinkedQueue<String>()

        repeat(threadCount) { index ->
            executorService.submit(
                Runnable {
                    try {
                        readyLatch.countDown()
                        startLatch.await()
                        action(index)
                        successCount.incrementAndGet()
                    } catch (e: Exception) {
                        failureCount.incrementAndGet()
                        failures.add("${e.javaClass.simpleName}: ${e.message}")
                    } finally {
                        doneLatch.countDown()
                    }
                },
            )
        }

        readyLatch.await()
        startLatch.countDown()
        val finished = doneLatch.await(10, TimeUnit.SECONDS)
        executorService.shutdown()

        assertThat(finished).describedAs("10초 안에 모든 스레드가 끝나야 한다 (교착 상태 여부 확인)").isTrue()

        return ConcurrentResult(successCount.get(), failureCount.get(), failures.distinct())
    }
}
