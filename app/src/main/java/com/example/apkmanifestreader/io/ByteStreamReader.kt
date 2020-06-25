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


@file:Suppress("UnstableApiUsage")

package com.example.apkmanifestreader.io

import com.example.apkmanifestreader.util.reportError
import com.google.common.io.CountingInputStream
import com.google.common.io.LittleEndianDataInputStream
import java.io.DataInput
import java.io.EOFException
import java.io.InputStream
import kotlin.math.min

class ByteStreamReader private constructor(
    delegate: LittleEndianDataInputStream,
    private val countingInputStream: CountingInputStream
) : DataInput by delegate {

    override fun skipBytes(n: Int): Int {
        var bytesSkipped = 0
        val skipArray = ByteArray(min(1024, n))
        while (bytesSkipped < n) {
            val bytesToRead = min(n - bytesSkipped, skipArray.size)
            try {
                readFully(skipArray, 0, bytesToRead)
            } catch (e: EOFException) {
                reportError("Premature end-of-file was reached.")
                throw e
            }
            bytesSkipped += bytesToRead
        }
        return bytesSkipped
    }

    fun readByteToInt(): Int = readByte().toInt() and 0xFF

    fun getCount(): Long = countingInputStream.count

    fun close() {
        countingInputStream.close()
    }

    companion object {
        fun from(inputStream: InputStream): ByteStreamReader {
            val countingStream = CountingInputStream(inputStream)
            return ByteStreamReader(
                LittleEndianDataInputStream(countingStream),
                countingStream
            )
        }
    }
}