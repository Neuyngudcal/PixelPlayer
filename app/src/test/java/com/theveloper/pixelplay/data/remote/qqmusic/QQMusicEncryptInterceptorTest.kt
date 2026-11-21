package com.theveloper.pixelplay.data.remote.qqmusic

import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import timber.log.Timber

class QQMusicEncryptInterceptorTest {

    @Test
    fun `interceptor never logs plaintext request json sign or decrypted response json`() {
        val loggedMessages = mutableListOf<String>()
        val recordingTree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                loggedMessages += message
            }
        }
        Timber.plant(recordingTree)

        try {
            val plaintextPayload = """{"comm":{"uin":"12345678","authst":"SECRET_AUTH_TOKEN"}}"""
            val generatedSign = "zzb_SECRET_SIGN_VALUE"
            val decryptedPayload = """{"req_0":{"code":0,"subcode":0,"data":{"vkey":"SECRET_VKEY"}}}"""

            val signGenerator = mockk<QQSignGenerator>()
            every { signGenerator.generateSign(plaintextPayload) } returns generatedSign
            every { signGenerator.encryptRequestWithVm(plaintextPayload) } returns "ZW5jcnlwdGVk"
            every { signGenerator.decryptResponseWithVm(any()) } returns decryptedPayload

            val request = Request.Builder()
                .url("https://u.y.qq.com/cgi-bin/musics.fcg?secretQuery=DO_NOT_LOG")
                .post(plaintextPayload.toRequestBody("application/json".toMediaType()))
                .build()

            val chain = mockk<Interceptor.Chain>()
            every { chain.request() } returns request
            every { chain.proceed(any()) } answers {
                val outgoing = firstArg<Request>()
                Response.Builder()
                    .request(outgoing)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(byteArrayOf(1, 2, 3).toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            }

            val interceptor = QQMusicEncryptInterceptor(signGenerator)
            val response = interceptor.intercept(chain)

            assertTrue(response.isSuccessful)
            val joinedLogs = loggedMessages.joinToString("\n")
            assertFalse(joinedLogs.contains("SECRET_AUTH_TOKEN"))
            assertFalse(joinedLogs.contains(generatedSign))
            assertFalse(joinedLogs.contains("SECRET_VKEY"))
            assertFalse(joinedLogs.contains("DO_NOT_LOG"))
            assertFalse(joinedLogs.contains("Plain request json"))
            assertFalse(joinedLogs.contains("Decrypted JSON content"))
        } finally {
            Timber.uproot(recordingTree)
        }
    }
}
