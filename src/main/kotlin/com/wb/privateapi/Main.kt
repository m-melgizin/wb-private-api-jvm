package com.wb.privateapi

suspend fun main() {
    val api = WbPrivateApi()
    val catalog = api.search("ноутбук")
}