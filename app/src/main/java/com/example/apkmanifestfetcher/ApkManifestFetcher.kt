package com.example.apkmanifestfetcher

import android.content.Context
import android.util.Log
import com.example.apkmanifestreader.io.ApkFile
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

object ApkManifestFetcher {

    fun getManifestXmlFromInputStream(
        context: Context,
        ApkInputStream: InputStream
    ): String? {

        Log.d("Applog", "Unzipping files.")
        ZipInputStream(ApkInputStream).use { zis: ZipInputStream ->
            var manifest: ByteArray? = null
            while (manifest == null) {
                var entry: ZipEntry?
                try {
                    entry = zis.nextEntry ?: break
                    when (entry.name) {
                        "AndroidManifest.xml" -> manifest = zis.readBytes()
                    }
                } catch (e: ZipException) {
                    manifest = null
                    Log.d("Applog", "<<<< Zip error!!")
                    Log.d("Applog", e.message ?: "No message")
                    break
                }
            }
            Log.d("Applog", "Done unzipping files.")
            if (manifest == null) {
                return null
            }
            return ApkFile(zis).run {
                Log.d("Applog", "Decoding manifest.")
                setNewInputStream(manifest.inputStream())
                decodeXml(context).also {
                    Log.d("Applog", "End decoding manifest.")
                    close()
                }
            }
        }
    }
}