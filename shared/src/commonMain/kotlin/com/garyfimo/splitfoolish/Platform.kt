package com.garyfimo.splitfoolish

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform