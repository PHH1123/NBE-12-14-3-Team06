package com.back.nbe12142team06.global.response

class RsData<T> @JvmOverloads constructor(
    val statusCode: String,
    val msg: String,
    var data: T? = null
    ) {
    fun simpleStatusCode(): Int = statusCode.substringBefore('-').toInt()
}
