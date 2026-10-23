package com.back.nbe12142team06.global.exception

open class BusinessException : RuntimeException {

    val statusCode: String

    constructor(statusCode: String, message: String) : super(message) {
        this.statusCode = statusCode
    }

    constructor(statusCode: String, message: String, cause: Throwable) : super(message, cause) {
        this.statusCode = statusCode
    }
}
