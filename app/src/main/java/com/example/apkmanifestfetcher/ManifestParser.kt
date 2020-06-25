package com.example.apkmanifestfetcher

import android.content.Context
import android.util.Log
import org.w3c.dom.Node
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

class ManifestParser {
    var isSplitApk: Boolean? = null
    var manifestAttributes: HashMap<String, String>? = null

    companion object {
        private var filePath: String? = null

        fun parse(context: Context, filePath: String): ManifestParser? {
            this.filePath = filePath
            return parse(context, FileInputStream(File(filePath)))
        }

        private fun parse(context: Context, inputStream: InputStream): ManifestParser? {
            val result = ManifestParser()
            Log.d("Applog", "<<< Start manifest extraction.")
            val manifestXmlString =
                ApkManifestFetcher.getManifestXmlFromInputStream(
                    context, inputStream
                ) ?: return null

            Log.d("Applog", "<<< End manifest extraction. Append to log.")
            Util.appendLog(manifestXmlString)
            Log.d("Applog", "<<< End append to log. Start parsing.")
            val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
            val builder: DocumentBuilder = factory.newDocumentBuilder()
            val document = try {
                builder.parse(manifestXmlString.byteInputStream())
            } catch (e: Exception) {
                Log.d("Applog", e.toString())
                null
            }
            Log.d("Applog", "<<< End parsing.")

            if (document != null) {
                document.documentElement.normalize()
                val manifestNode: Node? = document.getElementsByTagName("manifest")?.item(0)
                if (manifestNode != null) {
                    val manifestAttributes = HashMap<String, String>()
                    for (i in 0 until manifestNode.attributes.length) {
                        val node = manifestNode.attributes.item(i)
                        manifestAttributes[node.nodeName] = node.nodeValue
                    }
                    result.manifestAttributes = manifestAttributes
                }
            }
            result.manifestAttributes?.let {
                result.isSplitApk = (it["android:isFeatureSplit"]?.toBoolean()
                    ?: false) || (it.containsKey("split"))
            }
            return result
        }
    }
}
