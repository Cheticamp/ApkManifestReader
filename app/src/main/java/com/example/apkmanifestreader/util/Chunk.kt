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


package com.example.apkmanifestreader.util

import com.example.apkmanifestreader.io.ByteStreamReader
import java.io.EOFException

/*
    Format for ResChunk_header:

    Type identifier for this chunk.  The meaning of this getValue depends on the containing chunk.
        (2 bytes)

    Size of the chunk header (in bytes).  Adding this getValue to the address of the chunk allows
    you to find its associated data (if any). (2 bytes)

    Total size of this chunk (in bytes).  This is the header size plus the size of any data
    associated with the chunk.  Adding this getValue to the chunk allows you to completely skip
    its contents (including any child chunks).  If this getValue is the same as header size, there is
    no data associated with the chunk. (4 bytes)
*/

class Chunk(
    val offset: Long,       // Offset of this chunk in the file.
    val type: Int,          // Identifies what's contained in this chunk
    val headerSize: Int,    // Size in bytes of the chunk header plus any type-specific header.
    val chunkSize: Int     // Total size of the chunk from the start of chunk header to end.
) {

    companion object {
        const val NO_ENTRY = -1

        // Get the 8-byte header of the next chunk
        @JvmStatic
        fun getChunkHeader(reader: ByteStreamReader): Chunk? {
            return try {
                Chunk(
                    reader.getCount(),              // Offset of chunk in the file
                    reader.readUnsignedShort(),     // chunk type
                    reader.readUnsignedShort(),     // header size-includes this + type header if any
                    reader.readInt()                // chunk size from start of this header to end
                )
            } catch (e: EOFException) { // Assume just reading until the end
                null
            }
        }

        // Go to the end of the type's header
        @JvmStatic
        fun gotoHeaderEnd(chunkHeader: Chunk, reader: ByteStreamReader) {
            gotoChunkOffset(                chunkHeader,                reader,                chunkHeader.headerSize            )
        }

        // Go to the end of the chunk.
        @JvmStatic
        fun gotoChunkEnd(chunkHeader: Chunk, reader: ByteStreamReader) {
            gotoChunkOffset(
                chunkHeader,
                reader,
                chunkHeader.chunkSize
            )
        }

        //Todo Chunk offsets are really Longs but we treat them as Ints.
        @JvmStatic
        fun gotoChunkOffset(chunkHeader: Chunk, reader: ByteStreamReader, offset: Int) {
            val bytesToSkip = (chunkHeader.offset + offset - reader.getCount()).toInt()
            if (bytesToSkip < 0) {
                reportError("Skip bytes is negative. ($bytesToSkip)")
                return
            }
            reader.skipBytes(bytesToSkip)
        }

        // Check a chunk header to make sure that it is valid before we continue.
        @JvmStatic
        fun requireChunkStart(
            chunkHeader: Chunk?, expectedType: Int, nullHeaderOk: Boolean = false
        ): Boolean {
            if (chunkHeader != null && chunkHeader.type == expectedType) {
                return true
            }
            if (chunkHeader == null && nullHeaderOk) {
                return false
            }
            throw ApkException(
                "Invalid start to resource type ${"""0x%08x""".format(expectedType)}"
            )
        }
    }
}