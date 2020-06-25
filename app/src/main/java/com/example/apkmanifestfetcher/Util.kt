package com.example.apkmanifestfetcher

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException

class Util {
    companion object {
        private lateinit var mLogFilePath: String

        fun setLogFile(context: Context, fileName: String) {
            mLogFilePath = context.filesDir.toString() + File.separator + fileName
            val logFile = File(mLogFilePath)
            if (logFile.exists()) {
                logFile.delete()
            }
        }

        @Synchronized
        fun appendLog(s: String) {
            run {
                val logFile = File(mLogFilePath)
                if (!logFile.exists()) {
                    try {
                        logFile.createNewFile()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                }
                try {
                    val buf = BufferedWriter(FileWriter(logFile, true))
                    buf.append(s)
                    buf.newLine()
                    buf.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }
}