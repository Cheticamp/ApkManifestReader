package com.example.apkmanifestfetcher

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/*
    Identifies packages (installed or otherwise) to select for processing.
 */

object PackageSelector {

    fun getPackagesToParse(context: Context): List<ApkInfo> {
        val apksToParse = mutableListOf<ApkInfo>()
        val installedApplications = context.packageManager.getInstalledPackages(0)

        // By default, we select all installed packages. We can limit the selected packages to
        // a subset of these by specifying the package name in packagedToSelect.
        @Suppress("RemoveExplicitTypeArguments")
        val packagesToSelect = hashSetOf<String>(
//            "com.example.libraryresourcereferences"
//            "com.google.android.webview",
//            "com.google.android.ext.shared"

//            "com.google.android.ext.services"
//            "com.example.apkmanifestreader",
//            "com.example.zipfileinmemoryjni",

//            "com.android.chrome",
//            "com.android.providers.media",
//            "com.delta.mobile.android",
            // Invalid resource id for receiver label
//            "com.facebook.katana"
//            "com.google.android.apps.restore"
//            "com.google.android.apps.restore",
//            "com.google.android.apps.tachyon",
//            "com.google.android.gms"
//            "com.google.android.googlequicksearchbox",
//            "com.google.android.inputmethod.pinyin"
//            "com.google.android.talk"
//            "com.google.android.youtube"
//            "com.google.android.youtube",
//            "com.netflix.mediaclient",
//            "com.samsung.android.app.withtv",
//            "com.samsung.android.calendar"
//            "com.samsung.android.scloud",
//            "com.sec.android.app.safetyassurance",
//            "com.teslacoilsw.launcher",
//            "com.teslacoilsw.launcherclientproxy",
        )

        // If we are looking at all installed packages, we can disregard select packages by
        // setting an entry in packagesToNotSelect.
        val packagesToNotSelect = HashSet<String>()
        // Zip error - only DEFLATED entries can have EXT descriptor
        packagesToNotSelect.add("org.mozilla.firefox")
        // We will test this independently of the general scan.
        packagesToNotSelect.add("com.example.configtester")

        when {
            packagesToSelect.size > 0 -> {
                // We have specific installed APKs to parse.
                for (packageName in packagesToSelect) {
                    context.packageManager.getPackageInfo(packageName, 0)?.apply {
                        apksToParse.add(ApkInfo(packageName, applicationInfo))
                    } ?: Log.e("Applog", "Could not find package $packageName")
                }
            }
            else -> {
                // We are looking at all installed APKs.
                for ((index, packageInfo) in installedApplications.withIndex()) {
                    if (!packagesToNotSelect.contains(installedApplications[index].packageName)) {
                        val packageName = packageInfo.packageName
                        val applicationInfo = packageInfo.applicationInfo
                        apksToParse.add(ApkInfo(packageName, applicationInfo))
                    }
                }
            }
        }
        return apksToParse
    }

    data class ApkInfo(val packageName: String, val applicationInfo: ApplicationInfo)
}