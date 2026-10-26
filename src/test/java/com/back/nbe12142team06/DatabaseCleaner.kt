package com.back.nbe12142team06

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class DatabaseCleaner(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun clean() {
        val tables = jdbcTemplate.queryForList(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
            String::class.java,
        )

        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE")
        tables.forEach { table -> jdbcTemplate.execute("TRUNCATE TABLE $table") }
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE")
    }
}
