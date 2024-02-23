/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package nl.giejay.android.tv.immich.shared.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import java.io.IOException
import java.io.InputStream

/**
 * A collection of utility methods, all static.
 */
object Utils {

    fun View.getAllChildren(): List<View> {
        val result = ArrayList<View>()
        if (this !is ViewGroup) {
            result.add(this)
        } else {
            for (index in 0 until this.childCount) {
                val child = this.getChildAt(index)
                result.addAll(child.getAllChildren())
            }
        }
        return result
    }

    fun <T> Comparator<T>.optionalReversed(reverse: Boolean): Comparator<T>{
        if(reverse){
            return this.reversed()
        }
        return this
    }

    fun <T> Comparable<T>.compareToNullSafe(date: T?): Int {
        if(date == null){
            return -1
        }
        return this.compareTo(date)
    }

    fun convertDpToPixel(ctx: Context, dp: Int): Int {
        val density = ctx.resources.displayMetrics.density
        return Math.round(dp.toFloat() * density)
    }

    /**
     * Will read the content from a given [InputStream] and return it as a [String].
     *
     * @param inputStream The [InputStream] which should be read.
     * @return Returns `null` if the the [InputStream] could not be read. Else
     * returns the content of the [InputStream] as [String].
     */
    fun inputStreamToString(inputStream: InputStream): String {
        return try {
            val bytes = ByteArray(inputStream.available())
            inputStream.read(bytes, 0, bytes.size)
            String(bytes)
        } catch (e: IOException) {
            ""
        }
    }

    fun getResourceUri(context: Context, resID: Int): Uri {
        return Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                    context.resources.getResourcePackageName(resID) + '/' +
                    context.resources.getResourceTypeName(resID) + '/' +
                    context.resources.getResourceEntryName(resID)
        )
    }
}