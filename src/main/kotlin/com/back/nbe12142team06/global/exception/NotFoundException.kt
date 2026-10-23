package com.back.nbe12142team06.global.exception

class NotFoundException : BusinessException {
    constructor(message: String) : super("404", message)

    constructor(statusCode: Int, message: String) : super("404-$statusCode", message)

    constructor(message: String, cause: Throwable) : super("404", message, cause)

    constructor(statusCode: Int, message: String, cause: Throwable) : super("404-$statusCode", message, cause)
}
