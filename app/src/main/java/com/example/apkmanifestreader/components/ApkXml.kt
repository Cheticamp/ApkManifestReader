/*
 *  Copyright 2020 Cheticamp
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.example.apkmanifestreader.components

import android.content.res.Resources
import android.util.Log
import com.example.apkmanifestreader.components.ResourceTypes.Companion.RES_STRING_POOL_TYPE
import com.example.apkmanifestreader.components.ResourceTypes.Companion.RES_XML_CDATA_TYPE
import com.example.apkmanifestreader.components.ResourceTypes.Companion.RES_XML_END_ELEMENT_TYPE
import com.example.apkmanifestreader.components.ResourceTypes.Companion.RES_XML_END_NAMESPACE_TYPE
import com.example.apkmanifestreader.components.ResourceTypes.Companion.RES_XML_RESOURCE_MAP_TYPE
import com.example.apkmanifestreader.components.ResourceTypes.Companion.RES_XML_START_ELEMENT_TYPE
import com.example.apkmanifestreader.components.ResourceTypes.Companion.RES_XML_START_NAMESPACE_TYPE
import com.example.apkmanifestreader.components.ResourceTypes.Companion.RES_XML_TYPE
import com.example.apkmanifestreader.io.ByteStreamReader
import com.example.apkmanifestreader.util.Chunk
import com.example.apkmanifestreader.util.Chunk.Companion.NO_ENTRY
import com.example.apkmanifestreader.util.Chunk.Companion.getChunkHeader
import com.example.apkmanifestreader.util.Chunk.Companion.gotoChunkEnd
import com.example.apkmanifestreader.util.Chunk.Companion.gotoChunkOffset
import com.example.apkmanifestreader.util.Chunk.Companion.gotoHeaderEnd
import com.example.apkmanifestreader.util.Chunk.Companion.requireChunkStart
import com.example.apkmanifestreader.util.XmlNameValueHelper
import com.example.apkmanifestreader.util.reportError
import org.apache.commons.text.StringEscapeUtils
import kotlin.math.max

/*
    Class for decoding a binary XML file from an input stream . Manifest files are the primary
    target, but this class may also work for other binary XML files (layout files, etc.)

    Builds a string containing a decoded manifest file that can be displayed and parsed with an
    XML parser. Upon entry to ApkXml.decodeXml(), the reader should be positioned at the first
    byte of the XML file.

    In the output, scalar resources are represented by their values, e.g.,
    android:windowSoftInputMode="0x10" while complex resources and references are represented by
    their resource ids ("resId:0x7f1401f9") by default. XmlNameValueHelper.kt may be extended
    to produce different outputs.

    Refer to ResourceTypes.h in the AOSP source regarding data layout of the XML file.
 */

class ApkXml(
    appResources: Resources,           // This should be this app's resources.
    nameValueHelper: XmlNameValueHelper? = null
) {
    private var mAppResources: Resources = appResources
    private var mXmlNameValueHelper: XmlNameValueHelper =
        nameValueHelper ?: XmlNameValueHelper(mAppResources)

    private lateinit var mReader: ByteStreamReader

    private var mResourceMap = intArrayOf()
    private val mStringPool = ApkStringPool()
    private val mOutput = StringBuilder()

    private val mNameSpaceManager = NameSpaceManager()
    private val mFormatter = Formatter()

    fun decodeXml(reader: ByteStreamReader): String {
        mReader = reader

        getChunkHeader(mReader)?.apply {
            requireChunkStart(this, RES_XML_TYPE)
            gotoHeaderEnd(this, mReader)
            decodeXml()
        }

        return mOutput.toString()
    }

    /*
        Structure of input:

        XML tree header (ResXMLTree_header)
        Chunk header (ResChunk_header) Type = RES_XML_TYPE
        Set of chunks of type ResXMLTree_node follow.
    */

    private fun decodeXml() {
        mOutput.clear().append("$XML_HEADER\n")
        var chunkHeader = getChunkHeader(mReader)
        while (chunkHeader != null) {
            when (chunkHeader.type) {
                RES_XML_START_ELEMENT_TYPE -> decodeStartElement(chunkHeader)
                RES_XML_END_ELEMENT_TYPE -> decodeEndElement(chunkHeader)
                RES_XML_START_NAMESPACE_TYPE -> decodeStartNameSpace(chunkHeader)
                RES_XML_END_NAMESPACE_TYPE -> decodeEndNameSpace(chunkHeader)
                RES_XML_CDATA_TYPE -> decodeCdataElement(chunkHeader)
                RES_STRING_POOL_TYPE -> extractStringPool(chunkHeader)
                RES_XML_RESOURCE_MAP_TYPE -> decodeResourceMap(chunkHeader)
                else -> processUnknownChunk(chunkHeader)
            }
            chunkHeader = getChunkHeader(mReader)
        }
    }

    private fun extractStringPool(chunkHeader: Chunk) {
        if (mStringPool.getSize() > 0) {
            reportError("More than one string pool in encoded XML file. Using last defined.")
        }
        mStringPool.extractStringPool(chunkHeader, mReader)
    }

    /*
        Start Name Space Chunk
        ResXMLTree_node
            Chunk header - ResChunk_header (8 bytes)
            Line number - original source line number (4 bytes)
            Comment - String pool reference for comment (4 bytes)
        ResXMLTree_namespaceExt
            Prefix of namespace - String pool reference (4 bytes)
            URI of namespace - String pool reference (4 bytes)
    */

    private fun decodeStartNameSpace(chunkHeader: Chunk) {
        /*val lineNumber =*/ mReader.skipBytes(4)
        /*val comment =*/ mReader.skipBytes(4)
        val prefixIndex = mReader.readInt()
        val prefix =
            mStringPool[prefixIndex] ?: mXmlNameValueHelper.getInvalidNameSpacePrefixId(prefixIndex)
        val uriIndex = mReader.readInt()
        val uri = mStringPool[uriIndex] ?: mXmlNameValueHelper.getInvalidNameSpaceUriId(uriIndex)
        mNameSpaceManager.pushNameSpace(prefix, uri)
        gotoChunkEnd(chunkHeader, mReader)
    }

    /*
        End Name Space Chunk
        ResXMLTree_node
            Chunk header - ResChunk_header (8 bytes)
            Line number - original source line number (4 bytes)
            Comment - String pool reference for comment (4 bytes)
        ResXMLTree_namespaceExt
            Prefix of namespace - String pool reference (4 bytes)
            URI of namespace - String pool reference (4 bytes)
    */

    private fun decodeEndNameSpace(chunkHeader: Chunk) {
        /*val lineNumber =*/ mReader.skipBytes(4)
        /*val comment =*/ mReader.skipBytes(4)
        /*val prefixIndex =*/ mReader.skipBytes(4)
        val uriIndex = mReader.readInt()
        mNameSpaceManager.popNameSpace(mStringPool[uriIndex]!!)
        gotoChunkEnd(chunkHeader, mReader)
    }

    /*
        Start Element Chunk

        ResXMLTree_node
            Chunk header(ResChunk_header)
            Line number from original XML (4 bytes)
            Comment - String pool index (4 bytes)
        ResXMLTree_endElementExt
            Namespace - String pool index (4 bytes)
            Element name - String pool index (4 bytes)
        ResXMLTree_attrExt (Start element only)
            Byte offset from start of this structure (ResXMLTree_endElementExt) to start of attributes.
                (2 bytes)
            Size of attribute section (2 bytes)
            Attribute count (2 bytes)

            For the following three indices, 0 if none.
            Index (1-based) of the "id" attribute within the attributes for an elemant. (2 bytes)
            Index (1-based) of the "class" attribute within the attributes for an elemant. (2 bytes)
            Index (1-based) of the "style" attribute within the attributes for an elemant. (2 bytes)

        ResXMLTree_attribute (Start element only)
            (attribute section repeats "Attribute count" times)
            Attribute namespace - String pool index (4 bytes)
            Attribute name - String pool index (4 bytes)
            Original raw string getValue for the attribute - String pool index (4 bytes)
            Processed type getValue for the attribute - String pool index (8 bytes)
               Size of type getValue (2 bytes)
               Reserved - always zero (1 byte)
               Data Type of data getValue (ResValue) (1 bytes)
               Data for this item as interpreted by Data Type (4 bytes)
    */

    private fun decodeStartElement(chunkHeader: Chunk) {
        /*val lineNumber =*/ mReader.skipBytes(4)
        /*val comment =*/ mReader.skipBytes(4)
        val tagNameSpace = mReader.readInt()
        val tagId = mReader.readInt()
        val attrStart = mReader.readUnsignedShort()
        /*val attrSize =*/ mReader.skipBytes(2)
        val attrCount = mReader.readUnsignedShort()

        // idIndex, classIndex and styleIndex are relative to "1".
        val idIndex = mReader.readUnsignedShort()
        val classIndex = mReader.readUnsignedShort()
        val styleIndex = mReader.readUnsignedShort()
        gotoChunkOffset(chunkHeader, mReader, chunkHeader.headerSize + attrStart)

        mOutput.append(startNewElement()).append("<").append(getQualifiedTag(tagNameSpace, tagId))

        var nameSpacesEmitted = 0

        // Look at the attributes. Each is an ResXMLTree_attribute
        for (attrPos in 0 until attrCount) {
            val nameSpaceId = mReader.readInt()
            val nameId = mReader.readInt()
            val attrRawValue = mReader.readInt()

            // Look at typed getValue for the attribute. This is a ResValue.
            val typeStart = mReader.getCount()
            val valueSize = mReader.readUnsignedShort()
            mReader.skipBytes(1) // always zero
            val dataType = mReader.readByteToInt()
            val data = mReader.readInt()

            // getQualifiedAttr() will create a dummy prefix if the name space has not been
            // defined explicitly. (Name space without a name space chunk.) Make sure that we
            // explicitly define the name space by emitting it.
            val attrName = getQualifiedAttr(nameSpaceId, nameId)
            nameSpacesEmitted += emitNameSpaces()

            mOutput.append(
                mFormatter.onAttribute(attrPos + nameSpacesEmitted, attrCount + nameSpacesEmitted)
            )

            val attrValue =
                when (attrPos + 1) {
                    idIndex -> mXmlNameValueHelper.getIdName(data)
                    classIndex -> mXmlNameValueHelper.getClassName(data)
                    styleIndex -> mXmlNameValueHelper.getStyleName(data)
                    else -> StringEscapeUtils.escapeXml11(
                        if (attrRawValue == NO_ENTRY) {
                            mXmlNameValueHelper.getAttributeValue(dataType, data)
                                ?: mXmlNameValueHelper.getUnknownType(dataType, data)
                        } else {
                            mStringPool[attrRawValue]
                        }
                    )
                }

            mOutput.append(attrName).append("=").append("\"$attrValue\"")

            gotoChunkOffset(
                chunkHeader, mReader, (typeStart - chunkHeader.offset + valueSize).toInt()
            )
        }
        gotoChunkEnd(chunkHeader, mReader)
    }

    /*
        Like start element but without the attributes.
     */

    private fun decodeEndElement(chunkHeader: Chunk) {
        /*val lineNumber =*/ mReader.skipBytes(4)
        /*val comment =*/ mReader.skipBytes(4)
        val tagNameSpace = mReader.readInt()
        val tagId = mReader.readInt()
        mFormatter.onElementEnd(mOutput, getQualifiedTag(tagNameSpace, tagId))
        gotoChunkEnd(chunkHeader, mReader)
    }

    private fun decodeCdataElement(chunkHeader: Chunk) {
        /* val lineNumber =*/ mReader.skipBytes(4)
        /*val comment =*/ mReader.skipBytes(4)
        val stringNumber = mReader.readInt()
        val string = mStringPool[stringNumber] ?: "***Invalid CDATA String***"
        mOutput.append(startNewElement()).append("<![CDATA[").append(string).append("]]")
        mFormatter.onElementEnd(mOutput, null, true)
        gotoChunkEnd(chunkHeader, mReader)
    }

    /*
        XMLResourceMap chunk

        This contains a uint32_t array mapping strings in the string pool back to system
        resource identifiers. It is optional.

    */

    private fun decodeResourceMap(chunkHeader: Chunk) {
        gotoHeaderEnd(chunkHeader, mReader)
        mResourceMap = IntArray((chunkHeader.chunkSize - chunkHeader.headerSize) / 4)
        for (i in mResourceMap.indices) {
            mResourceMap[i] = mReader.readInt()
        }
        gotoChunkEnd(chunkHeader, mReader)
    }

    private fun processUnknownChunk(chunkHeader: Chunk) {
        reportError(
            "Unknown chunk type in encoded XML file (0x%08x). Attempting to skip to next chunk.".format(
                chunkHeader.type
            )
        )
        gotoChunkEnd(chunkHeader, mReader)
    }

    private fun getAttributeName(nameId: Int): String? {
        val attrName: String? = mStringPool[nameId]
        if (attrName != null && attrName.isNotEmpty()) {
            return attrName
        }

        // See if the reference is in the resource map.
        if (nameId >= mResourceMap.size) {
            // Not in the map
            return null
        }

        // Look for an Android system id in the resource map. Here, we must rely upon the
        // underlying Android system for the system ids. If the target API for the app is greater
        // than the API of the framework that we are running on, those attributes defined in APIs
        // later than the API of the framework will not be available, so we try to backfill them here.

        val systemAttrId = mResourceMap[nameId]
        return mXmlNameValueHelper.getAndroidAttributeName(systemAttrId)
    }

    private fun emitNameSpaces(): Int {
        var namesSpacesEmitted = 0
        var nameSpacePair = mNameSpaceManager.getNextNameSpaceToEmit()
        while (nameSpacePair != null) {

            mOutput.append(mFormatter.onNameSpace(namesSpacesEmitted == 0))
                .append(makeXmlNs(nameSpacePair.prefix, nameSpacePair.uri))
            namesSpacesEmitted++
            nameSpacePair = mNameSpaceManager.getNextNameSpaceToEmit()
        }
        return namesSpacesEmitted
    }

    private fun getQualifiedTag(nameSpaceId: Int, tagId: Int): String {
        var tagString = mStringPool[tagId]
        if (tagString == null) {
            reportError("Unknown tag: $tagId")
            tagString = mXmlNameValueHelper.getInvalidTagId(tagId)
        }
        return getQualifiedName(nameSpaceId, tagString)
    }

    private fun getQualifiedAttr(nameSpaceId: Int, attrId: Int): String {
        var attrName = getAttributeName(attrId)
        if (attrName == null) {
            reportError("Unknown XML attribute id: $attrId")
            attrName = mXmlNameValueHelper.getResIdRef(attrId)
        }
        return getQualifiedName(nameSpaceId, attrName)
    }

    private fun getQualifiedName(nameSpaceId: Int, name: String): String {
        return if (nameSpaceId == NO_ENTRY) {
            name
        } else {
            val uri = mStringPool[nameSpaceId]
            val prefix =
                if (!uri.isNullOrBlank()) { // As it should be...
                    mNameSpaceManager.getNameSpacePrefix(uri)
                } else {
                    // Fix for package com.keramidas.TitaniumBackup and maybe others that report
                    // an empty string as a name space. Here we assume that the last created
                    // name space is the one to use.
                    Log.d("Applog", "Name space uri is blank.")
                    // Get last pushed name space. If stack is empty, create a dummy one.
                    mNameSpaceManager.getTopNameSpacePrefix()
                        ?: mNameSpaceManager.getNameSpacePrefix(DUMMY_NAME_SPACE)
                }
            "$prefix:$name"
        }
    }

    private fun startNewElement() = mFormatter.onElementStart()

    companion object {
        private const val XML_HEADER =
            "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>"

        private const val DUMMY_NAME_SPACE = "http://schemas.android.com/apk/res/dummy"

        private fun makeXmlNs(prefix: String, uri: String): String = "xmlns:$prefix=\"$uri\""
    }

    /*
        Track and manage name spaces. Create dummy prefixes if needed.
     */
    private class NameSpaceManager {
        private val mNameSpaceStack = mutableListOf<NameSpaceEntry>()
        private var mNameSpacesToEmit = 0
        private var mDummyPrefixCount = 0

        fun pushNameSpace(prefix: String, uri: String) {
            mNameSpaceStack.add(NameSpaceEntry(prefix, uri))
            mNameSpacesToEmit++
        }

        fun getNameSpacePrefix(uri: String): String =
            mNameSpaceStack.findLast { entry ->
                entry.uri == uri
            }?.prefix ?: createDummyPrefix().also { prefix ->
                pushNameSpace(prefix, uri)
            }

        fun popNameSpace(uri: String) {
            val pos = mNameSpaceStack.indexOfLast { it.uri == uri }
            if (pos >= 0) mNameSpaceStack.removeAt(pos)
        }

        fun getNextNameSpaceToEmit(): NameSpaceEntry? =
            if (mNameSpacesToEmit > 0) {
                mNameSpaceStack[mNameSpaceStack.size - mNameSpacesToEmit--]
            } else {
                null
            }

        fun getTopNameSpacePrefix(): String? {
            val pos = mNameSpaceStack.size - 1
            return if (pos >= 0) {
                mNameSpaceStack[pos].prefix
            } else {
                null
            }
        }

        // If a name space is referenced but has not been tied to a prefix, invent a prefix for it.
        private fun createDummyPrefix(): String = "ns${++mDummyPrefixCount}"

        data class NameSpaceEntry(val prefix: String, val uri: String)
    }

    /*
       Track the depth of tags for tag closure and indentation.
    */
    private class Formatter {
        private var mTagDepth = 0
        private var mLastDepthPushed = 1

        fun onElementStart(): String {
            // Capture the current indent prior to the change to the tag depth.
            val currentIndent = getIndent()
            val tagEnd = if (++mTagDepth > mLastDepthPushed) ">\n" else ""
            mLastDepthPushed = mTagDepth
            return "$tagEnd$currentIndent"
        }

        fun onAttribute(attrNumber: Int, attrCount: Int): String =
            if (attrNumber == 0 && attrCount == 1) " " else "\n${getIndent()}"

        fun onNameSpace(first: Boolean): String = if (first) " " else "\n${getIndent()}"

        fun onElementEnd(sb: StringBuilder, qualifiedTagName: String?, isCdata: Boolean = false) {
            if (mTagDepth-- == mLastDepthPushed) {
                sb.append(if (isCdata) ">\n" else " />\n")
            } else if (qualifiedTagName != null) {
                sb.append(getIndent()).append("</$qualifiedTagName>\n")
            }
        }

        private fun getIndent() = INDENTATION_STOP.repeat(max(0, mTagDepth))

        private companion object {
            const val INDENTATION_STOP = "    "
        }
    }
}
