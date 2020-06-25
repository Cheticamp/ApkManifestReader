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


package com.example.apkmanifestreader.io

import android.content.Context
import com.example.apkmanifestreader.components.ApkXml
import java.io.InputStream

class ApkFile(inputStream: InputStream) {
    private var mReader = ByteStreamReader.from(inputStream)

    fun decodeXml(context: Context): String {
        return ApkXml(context.resources).decodeXml(mReader)
    }

    fun setNewInputStream(inputStream: InputStream) {
        mReader = ByteStreamReader.from(inputStream)
    }

    fun close() {
        mReader.close()
    }
}