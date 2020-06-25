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

import android.content.res.Resources
import android.util.TypedValue
import com.example.apkmanifestreader.components.ResourceTypes

open class XmlNameValueHelper(
    resources: Resources,
    futureAttributes: Map<Int, String>? = null
) {
    private val mAppResources = resources
    private val mFutureAttributes = futureAttributes ?: mapOf(
        // Added in version N MR1
        Pair(0x0101052c, "roundIcon"),
        // Added in Pie
        Pair(0x01010572, "compileSdkVersion"),
        Pair(0x01010573, "compileSdkVersionCodename"),
        Pair(0x0101057A, "appComponentFactory"),
        // Added in Q
        Pair(0x01010601, "allowAudioPlaybackCapture")
    )

    open fun getFutureAttribute(resId: Int): String =
        mFutureAttributes[resId] ?: getSysIdRef(resId)

    open fun getAndroidAttributeName(id: Int): String {
        return try {
            // android:attr/name
            mAppResources.getResourceName(id).split("/").last()
        } catch (e: Resources.NotFoundException) {
            // Attribute not defined in this version of Android.
            //See https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/res/res/values/public.xml;l=2856?q=0x01010572&ss=android%2Fplatform%2Fsuperproject
            getFutureAttribute(id)
        }
    }

    open fun getAttributeValue(dataType: Int, data: Int): String? {
        return when (dataType) {

            TypedValue.TYPE_REFERENCE -> {
                if (data == Resources.ID_NULL) {
                    "@null"
                } else {
                    getResIdRef(data)
                }
            }

            ResourceTypes.TYPE_DYNAMIC_REFERENCE, ResourceTypes.TYPE_DYNAMIC_ATTRIBUTE ->
                getDynamicTypeRef(dataType, data)

            else -> TypedValue.coerceToString(dataType, data)
        }
    }

    open fun getIdName(data: Int) = "id:0x%08x".format(data)

    open fun getClassName(data: Int) = "class:0x%08x".format(data)

    open fun getStyleName(data: Int) = "style:0x%08x".format(data)

    open fun getResIdRef(resId: Int): String = "resId:0x%08x".format(resId)

    open fun getSysIdRef(resId: Int): String = "sysId:0x%08x".format(resId)

    open fun getInvalidNameSpacePrefixId(index: Int): String = "invPrefixId:%d".format(index)

    open fun getInvalidNameSpaceUriId(index: Int): String = "invUriId:%d".format(index)

    open fun getInvalidTagId(tagId: Int): String = "invTagId:%08x".format(tagId)

    open fun getDynamicTypeRef(dataType: Int, data: Int): String =
        when (dataType) {
            ResourceTypes.TYPE_DYNAMIC_REFERENCE -> "dynRef:0x%08x".format(data)
            ResourceTypes.TYPE_DYNAMIC_ATTRIBUTE -> "dynAttr:0x%08x".format(data)
            else -> getUnknownType(dataType, data)
        }

    open fun getUnknownType(dataType: Int, data: Int): String =
        "unkType:0x%08x(%d)".format(data, dataType)
}