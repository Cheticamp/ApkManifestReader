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

import com.example.apkmanifestreader.components.ResourceTypes.Companion.RES_STRING_POOL_TYPE
import com.example.apkmanifestreader.io.ByteStreamReader
import com.example.apkmanifestreader.util.Chunk
import com.example.apkmanifestreader.util.Chunk.Companion.gotoChunkEnd
import com.example.apkmanifestreader.util.Chunk.Companion.gotoChunkOffset
import com.example.apkmanifestreader.util.Chunk.Companion.gotoHeaderEnd
import com.example.apkmanifestreader.util.Chunk.Companion.requireChunkStart
import com.example.apkmanifestreader.util.getByteToInt
import com.example.apkmanifestreader.util.getShort
import com.example.apkmanifestreader.util.reportError

/*
    Class related to the chunk type RES_STRING_POOL_TYPE that can retrieve strings from a
    string pool. This class does not interpret styled strings.

    See https://justanapplication.wordpress.com/2011/09/15/android-internals-resources-part-four-the-stringpool-chunk/

    The  format is:

    Chunk header (ResChunk_header)

    String Pool header
        stringCount - Number of strings in this pool. (4 bytes)
        styleCount - Number of style span arrays in the pool. (4 bytes)
        flags - As follows: (4 bytes)
            SORTED_FLAG = 0x01 - The string index is sorted by the string values
                (based on strcmp16())
            UTF8_FLAG = 1<<8 - String pool is encoded in UTF-8
        stringsStart - String pool starts at this offset from chunk start. (4 bytes)
        stylesStart - Style pool starts at this offset from chunk start. (4 bytes)

    String Pool offsets - stringCount offset to strings (4 * stringCount bytes)
    Style Pool offsets - styleCount offsets to styles (4 * styleCount bytes)
    String pool - stringCount null terminated strings (Do not assume that this starts
        immediately after the indices.)
    Style pool - styleCount entries. The style pool may be shorter than the string pool
            or even non-existent if there are no styled strings. (styleCount * 8 bytes)
            Each entry applies to its corresponding entry (same offset) in the string pool.

        (ResStringPool_span - Format for each entry in the style pool.)
        Style span offset - offset into the string pool that gives the name of the style span.
            The string that is being style has the same index into the string pool that
            this style pool entry has.
            0xFFFFFFFF denotes the end of the style spans (4 bytes)
        First character - first character of the string that the span applies to. (4 bytes)
        Last character - last character of the string that the span applies to (4 bytes)
        (Style pool entries repeat styleCount times.)
 */

class ApkStringPool {
    private var mStringCount = 0
    private var mStyleCount = 0
    private var mStringTableFlags = 0
    private var mStringsOffset = 0
    private var mStylesOffset = 0
    private var mStringsOffsets = IntArray(0) { 0 }
    private var mStringsTable = ByteArray(0)
    private var mStylesOffsets = IntArray(0) { 0 }
    private var mIsCharSetUtf8 = false
    private lateinit var mStringDecoder: (ByteArray, Int, Int) -> String

    fun extractStringPool(chunkHeader: Chunk, reader: ByteStreamReader): ApkStringPool {
        requireChunkStart(chunkHeader, RES_STRING_POOL_TYPE)
        mStringCount = reader.readInt()
        mStyleCount = reader.readInt()
        mStringTableFlags = reader.readInt()
        mStringsOffset = reader.readInt()
        mStylesOffset = reader.readInt()
        mIsCharSetUtf8 = (mStringTableFlags and UTF8_FLAG) == UTF8_FLAG
        mStringDecoder = { b: ByteArray, start: Int, length: Int ->
            String(b, start, length, if (mIsCharSetUtf8) Charsets.UTF_8 else Charsets.UTF_16LE)
        }
        gotoHeaderEnd(chunkHeader, reader)

        mStringsOffsets = IntArray(mStringCount)
        for (i in 0 until mStringCount) {
            mStringsOffsets[i] = reader.readInt()
        }

        mStylesOffsets = IntArray(mStyleCount)
        for (i in 0 until mStyleCount) {
            mStylesOffsets[i] = reader.readInt()
        }

        gotoChunkOffset(chunkHeader, reader, mStringsOffset)

        // Determine the size of the string table. Styles always appear before strings.
        val sizeOfStringTable =
            if (mStylesOffset > 0) {
                mStylesOffset
            } else {
                chunkHeader.chunkSize
            } - mStringsOffset
        mStringsTable = ByteArray(sizeOfStringTable)
        reader.readFully(mStringsTable)

        gotoChunkEnd(chunkHeader, reader)

        return this
    }

//    @Suppress("unused")
//    fun dumpStrings(tag: String = "StringPool") {
//        for (i in mStringsOffsets.indices) {
//            Log.d(tag, "<<<< $i: ${getInternal(i)}")
//        }
//    }

    /*
        Get a string from the string pool.

        Each string is presented in the string table as follows for UTF-8 encoding:
            - length of the string in characters (either 1 or 2 bytes)
            - the length of the UTF-8 encoding of the string in bytes (1 or 2 bytes)
            - the UTF-8 encoded string
            - a trailing 8-bit zero

        Each string is presented in the string table as follows for UTF-16 encoding:
            - length of the string in characters (either 2 or 4 bytes)
            - the length of the UTF-16 encoding of the string in bytes (2 or 4 bytes)
            - the UTF-16 encoded string
            - a trailing 16-bit zero

            See decodeLength() functions in the AOSP frameworks/base/include/ResourceTypes.cpp
     */
    operator fun get(stringNumber: Int): String? = get(stringNumber, null)

    fun get(stringNumber: Int, default: String?): String? {
        if (stringNumber !in 0 until mStringCount) {
            return default
        }
        val stringLocation = mStringsOffsets[stringNumber]
        return getInternal(stringLocation)
    }

    private fun getInternal(stringOffsetInPool: Int): String {
        var start = stringOffsetInPool
        var (skipLength, encodedByteLen) =
            if (mIsCharSetUtf8) {
                // Look at size in characters and skip the length field.
                start += getUtf8Len(mStringsTable, start)[0]
                // Look at size in encoded bytes.
                getUtf8Len(mStringsTable, start)
            } else {
                getUtf16Len(mStringsTable, start)
            }
        start += skipLength

        if ((start + encodedByteLen) > mStringsTable.size) {
            reportError("Invalid string length in string pool at location $start.")
            encodedByteLen = mStringsTable.size - start
        }
        return mStringDecoder(mStringsTable, start, encodedByteLen)
    }

    /*
        UTF-8 encoded strings are preceded by two length fields: the length of the string in
        characters followed by the length of the encoded string in bytes. Each length field
        may be either one or two bytes in length. If the first byte of a length field has the
        high-order bit set (0x80) then the field has two bytes which is the first byte with the
        high-order but set to zero followed the the second byte.

        getUtf8Len() will inspect the length at the offset and determine its size (one or two
        bytes) and the length that it encodes.
     */

    private fun getUtf8Len(b: ByteArray, offset: Int): IntArray {
        val len = b.getByteToInt(offset)
        return if ((len and 0x80) == 0) {
            // Length field is one byte
            intArrayOf(1, len)
        } else {
            // Length field is two bytes
            intArrayOf(2, ((len and 0x7f) shl 8) + b.getByteToInt(offset + 1))
        }
    }

    /*
        UTF-16 encoded strings are preceded by one length field which specifies the length
        of the string in characters. The length field may be either two or four bytes in length.
        If the high-order bit of a length field has the high-order bit set (0x8000) then the field
        comprises four bytes. In this case, the high-order bit should be set to zero and the length
        is then the four-bytes.

        getUtf16Len() will inspect the length at the offset and determine its size (two or four
        bytes) and the length that it encodes.
 */

    private fun getUtf16Len(b: ByteArray, offset: Int): IntArray {
        val len = b.getShort(offset)
        return if ((len and 0x8000) == 0) {
            // Length field is 2 bytes
            intArrayOf(2, 2 * len)
        } else {
            // Length field is 4 bytes
            intArrayOf(4, 2 * ((len and 0x7fff) shl 16) + b.getShort(offset + 2))
        }
    }

    @Suppress("unused")
    fun getSize(): Int = mStringCount

    @Suppress("unused")
    fun getBackingArraySize(): Int = mStringsTable.size

    companion object {
        const val UTF8_FLAG = 1 shl 8
    }
}