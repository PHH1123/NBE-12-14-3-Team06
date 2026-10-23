package com.back.nbe12142team06.global.exception

class DuplicatedException : BusinessException {
    constructor(message: String) : super("409", message)

    constructor(statusCode: Int, message: String) : super("409-$statusCode", message)

    constructor(message: String, cause: Throwable) : super("409", message, cause)

    constructor(statusCode: Int, message: String, cause: Throwable) : super("409-$statusCode", message, cause)
}
