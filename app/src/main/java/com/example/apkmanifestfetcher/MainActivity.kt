package com.example.apkmanifestfetcher

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Manifests will be written to this file.
        Util.setLogFile(this, "manifests")

        thread {
            val problematicApkFiles = HashMap<ApplicationInfo, HashSet<String>>()
            val apksToParse = PackageSelector.getPackagesToParse(this)
            val startTime = System.currentTimeMillis()
            for ((packageCounter, apkToParse) in apksToParse.withIndex()) {
                val packageName = apkToParse.packageName
                Log.d("Applog", "Parsing $packageName")
                Util.appendLog("**** $packageName")
                runOnUiThread {
                    packageCount.text = "$packageCounter/${apksToParse.size}"
                    packageNameDisplay.text = packageName
                }
                Log.d(
                    "AppLog",
                    "${packageCounter + 1}/${apksToParse.size} parsing app $packageName..."
                )
                val mainApkFilePath = apkToParse.applicationInfo.publicSourceDir
//                copyApk(mainApkFilePath, packageName) // Uncomment to make a local copy of APK.

                val parsedManifestOfMainApkFile =
                    try {
                        val parsedManifest =
                            ManifestParser.parse(this, mainApkFilePath)
                        if (parsedManifest?.isSplitApk != false)
                            Log.e(
                                "AppLog",
                                "$packageName - parsed normal APK, but failed to identify it as such"
                            )
                        parsedManifest?.manifestAttributes
                    } catch (e: Exception) {
                        Log.e("AppLog", e.toString())
                        e.printStackTrace()
                        null
                    }
                if (parsedManifestOfMainApkFile == null) {
                    problematicApkFiles.getOrPut(apkToParse.applicationInfo, { HashSet() })
                        .add(mainApkFilePath)
                    Log.e(
                        "AppLog",
                        "$packageName - failed to parse main APK file $mainApkFilePath"
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    applicationInfo.splitPublicSourceDirs?.forEach {
                        val parsedManifestOfSplitApkFile =
                            try {
                                val parsedManifest = ManifestParser.parse(this, it)
                                if (parsedManifest?.isSplitApk != true)
                                    Log.e(
                                        "AppLog",
                                        "$packageName - parsed split APK, but failed to identify it as such"
                                    )
                                parsedManifest?.manifestAttributes
                            } catch (e: Exception) {
                                Log.e("AppLog", e.toString())
                                null
                            }
                        if (parsedManifestOfSplitApkFile == null) {
                            Log.e(
                                "AppLog",
                                "$packageName - failed to parse main APK file $it"
                            )
                            problematicApkFiles.getOrPut(applicationInfo, { HashSet() })
                                .add(it)
                        }
                    }
                }
            }

            val endTime = System.currentTimeMillis()
            Log.d(
                "AppLog",
                "done parsing. number of files we failed to parse:${problematicApkFiles.size} time taken:${endTime - startTime} ms"
            )
            if (problematicApkFiles.isNotEmpty()) {
                Log.d("AppLog", "list of files that we failed to getInternal their manifest:")
                for (entry in problematicApkFiles) {
                    Log.d(
                        "AppLog",
                        "packageName:${entry.key.packageName} , files:${entry.value}"
                    )
                }
            }
            runOnUiThread {
                packageCount.text = "Done!"
                packageNameDisplay.text =
                    resources.getQuantityString(
                        R.plurals.packages_processed,
                        apksToParse.size,
                        apksToParse.size
                    )
            }
        }
    }

    @Suppress("unused")
    private fun copyApk(mainApkFilePath: String, packageName: String) {
        val inputFile = File(mainApkFilePath)
        val outputFile = File(filesDir, packageName)
        inputFile.copyTo(outputFile, true)
    }
}
