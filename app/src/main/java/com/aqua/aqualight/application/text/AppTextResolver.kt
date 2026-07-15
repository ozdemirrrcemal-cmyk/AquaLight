package com.aqua.aqualight.application.text

interface AppTextResolver {
    fun get(resId: Int, vararg args: Any): String
}
