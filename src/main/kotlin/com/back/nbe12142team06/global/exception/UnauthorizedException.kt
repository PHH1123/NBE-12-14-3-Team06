package com.back.nbe12142team06.global.exception

class UnauthorizedException : BusinessException {
    constructor(message: String) : super("401", message)

    constructor(statusCode: Int, message: String) : super("401-$statusCode", message)

    constructor(message: String, cause: Throwable) : super("401", message, cause)

    constructor(statusCode: Int, message: String, cause: Throwable) : super("401-$statusCode", message, cause)
}
