package com.back.nbe12142team06.global.response

class RsData<T> constructor(
    val statusCode: String,
    val msg: String,
    val data: T? = null
    ) {
    fun simpleStatusCode(): Int = statusCode.substringBefore('-').toInt()
}
