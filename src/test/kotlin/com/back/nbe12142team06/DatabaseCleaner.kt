package com.back.nbe12142team06

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class DatabaseCleaner(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun clean() {
        val tables = jdbcTemplate.queryForList(
            """
        SELECT table_name FROM information_schema.tables
         WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
        """,
            String::class.java,
        )
        if (tables.isEmpty()) return

        // Postgres 는 참조 무결성 스위치가 없으므로, 한 문장에 전부 넣고 CASCADE 로 FK 순서 문제를 피한다
        jdbcTemplate.execute(
            tables.joinToString(", ", "TRUNCATE TABLE ", " RESTART IDENTITY CASCADE") { "\"$it\"" },
        )
//
//        val tables = jdbcTemplate.queryForList(
//            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
//            String::class.java,
//        )
//
//        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE")
//        tables.forEach { table -> jdbcTemplate.execute("TRUNCATE TABLE $table") }
//        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE")
    }
}
