/*
 * Copyright (c) 2024 ModCore Inc. All rights reserved.
 *
 * This code is part of ModCore Inc.'s Essential Mod repository and is protected
 * under copyright registration # TX0009138511. For the full license, see:
 * https://github.com/EssentialGG/Essential/blob/main/LICENSE
 *
 * You may not use, copy, reproduce, modify, sell, license, distribute,
 * commercialize, or otherwise exploit, or create derivative works based
 * upon, this file or any other in this repository, all of which is reserved by Essential.
 */
package gg.essential.handlers.screenshot

import gg.essential.util.httpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.MalformedURLException

object ScreenshotUploadUtil {
    @Throws(MalformedURLException::class, IOException::class)
    fun httpUpload(url: String, fileData: ByteArray): Boolean {
        val httpClient = httpClient.join()
        val request = Request.Builder().apply {
            url(url)
            post(MultipartBody.Builder().apply {
                setType(MultipartBody.FORM)
                addFormDataPart("file", "file", fileData.toRequestBody("image/png".toMediaType()))
            }.build())
        }
        httpClient.newCall(request.build()).execute().use { response ->
            return response.isSuccessful
        }
    }
}