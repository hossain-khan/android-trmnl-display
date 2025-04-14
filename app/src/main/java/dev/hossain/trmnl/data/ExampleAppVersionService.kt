package dev.hossain.trmnl.data

import android.content.Context
import dev.hossain.trmnl.di.ApplicationContext
import javax.inject.Inject

// Example service class that does not need DI module or binding
class ExampleAppVersionService
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val versionName: String = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"

        fun getApplicationVersion(): String = versionName
    }
