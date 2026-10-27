package com.back.nbe12142team06.global.testsupport

import org.hibernate.resource.jdbc.spi.StatementInspector
import java.util.concurrent.atomic.AtomicInteger

/**
 * 테스트에서 "이 호출이 쿼리를 몇 건 쓰는지" 를 세기 위한 Hibernate 훅.
 *
 * Hibernate 가 실행하는 모든 SQL 을 가로채되, 카운터를 ThreadLocal 로 두어
 * **현재 스레드가 실행한 SQL 만** 센다.
 *
 * SessionFactory 의 statistics(prepareStatementCount)를 쓰지 않는 이유:
 * 그 카운터는 SessionFactory 전체에 하나뿐이어서 @Async 알림 스레드
 * (NoticeRequestedEventListener)가 커밋 후 백그라운드로 날리는 INSERT 까지 함께 센다.
 * 앞선 테스트가 남긴 비동기 쿼리가 측정 구간에 끼어들면 개수가 어긋나 간헐적으로 실패한다.
 *
 * Hibernate 가 no-arg 생성자로 직접 인스턴스화하므로, 카운터는 companion object 에 둔다.
 * 등록: spring.jpa.properties.hibernate.session_factory.statement_inspector=<이 클래스의 FQCN>
 */
class ThreadLocalQueryCounter : StatementInspector {

    override fun inspect(sql: String): String {
        counter.get().incrementAndGet()
        return sql
    }

    companion object {
        private val counter: ThreadLocal<AtomicInteger> = ThreadLocal.withInitial { AtomicInteger() }

        /** block 을 실행하는 동안 현재 스레드에서 나간 SQL 개수 */
        fun countQueries(block: () -> Unit): Int {
            counter.get().set(0)
            block()
            return counter.get().get()
        }
    }
}
