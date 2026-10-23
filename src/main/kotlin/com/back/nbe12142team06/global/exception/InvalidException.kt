package com.back.nbe12142team06.global.exception

class InvalidException : BusinessException {
    constructor(message: String) : super("400", message)

    constructor(statusCode: Int, message: String) : super("400-$statusCode", message)

    constructor(message: String, cause: Throwable) : super("400", message, cause)

    constructor(statusCode: Int, message: String, cause: Throwable) : super("400-$statusCode", message, cause)
}
