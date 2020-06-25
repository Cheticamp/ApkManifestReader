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

class ResourceTypes {
    companion object {
        const val RES_STRING_POOL_TYPE = 0x0001
        const val RES_XML_TYPE = 0x0003

        // Chunk types in RES_XML_TYPE
        const val RES_XML_START_NAMESPACE_TYPE = 0x0100
        const val RES_XML_END_NAMESPACE_TYPE = 0x0101
        const val RES_XML_START_ELEMENT_TYPE = 0x0102
        const val RES_XML_END_ELEMENT_TYPE = 0x0103
        const val RES_XML_CDATA_TYPE = 0x0104

        // This contains a uint32_t array mapping strings in the string
        // pool back to resource identifiers.  It is optional.
        const val RES_XML_RESOURCE_MAP_TYPE = 0x0180

        const val TYPE_DYNAMIC_REFERENCE = 0x07
        const val TYPE_DYNAMIC_ATTRIBUTE = 0x08
    }
}