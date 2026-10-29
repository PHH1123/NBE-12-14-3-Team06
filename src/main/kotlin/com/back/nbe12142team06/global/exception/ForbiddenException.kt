package com.back.nbe12142team06.global.exception

class ForbiddenException : BusinessException {
    constructor(message: String) : super("403", message)

    constructor(statusCode: Int, message: String) : super("403-$statusCode", message)

    constructor(message: String, cause: Throwable) : super("403", message, cause)

    constructor(statusCode: Int, message: String, cause: Throwable) : super("403-$statusCode", message, cause)
}
