package com.aqua.aqualight.platform.text

import android.content.Context
import com.aqua.aqualight.application.text.AppTextResolver

class AndroidAppTextResolver(
    context: Context
) : AppTextResolver {

    private val appContext = context.applicationContext

    override fun get(resId: Int, vararg args: Any): String {
        return appContext.getString(resId, *args)
    }
}
