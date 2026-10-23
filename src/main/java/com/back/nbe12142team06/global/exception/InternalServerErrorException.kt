package com.back.nbe12142team06.global.exception

class InternalServerErrorException : BusinessException {
    constructor(message: String) : super("500", message)

    constructor(statusCode: Int, message: String) : super("500-$statusCode", message)

    constructor(message: String, cause: Throwable) : super("500", message, cause)

    constructor(statusCode: Int, message: String, cause: Throwable) : super("500-$statusCode", message, cause)
}
