package com.back.nbe12142team06

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
@EnableJpaAuditing
class Nbe12142Team06Application

// 톱레벨 main 은 Nbe12142Team06ApplicationKt 에 static main 으로 컴파일된다.
// companion object 안에 두면 @JvmStatic 없이는 인스턴스 메서드가 되어
// bootJar 이 mainClass 를 찾지 못한다.
fun main(args: Array<String>) {
    runApplication<Nbe12142Team06Application>(*args)
}
