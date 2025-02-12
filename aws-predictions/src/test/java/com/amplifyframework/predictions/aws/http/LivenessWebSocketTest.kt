/*
 * Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amplifyframework.predictions.aws.http

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.util.Attributes
import com.amplifyframework.core.Action
import com.amplifyframework.core.Consumer
import com.amplifyframework.predictions.PredictionsException
import com.amplifyframework.predictions.aws.models.liveness.ChallengeConfig
import com.amplifyframework.predictions.aws.models.liveness.ColorSequence
import com.amplifyframework.predictions.aws.models.liveness.DisconnectionEvent
import com.amplifyframework.predictions.aws.models.liveness.FaceMovementAndLightServerChallenge
import com.amplifyframework.predictions.aws.models.liveness.FreshnessColor
import com.amplifyframework.predictions.aws.models.liveness.LightChallengeType
import com.amplifyframework.predictions.aws.models.liveness.OvalParameters
import com.amplifyframework.predictions.aws.models.liveness.ServerChallenge
import com.amplifyframework.predictions.aws.models.liveness.ServerSessionInformationEvent
import com.amplifyframework.predictions.aws.models.liveness.SessionInformation
import com.amplifyframework.predictions.aws.models.liveness.ValidationException
import com.amplifyframework.predictions.models.FaceLivenessSessionInformation
import io.mockk.mockk
import io.mockk.verify
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
internal class LivenessWebSocketTest {
    private val json = Json { encodeDefaults = true }

    private lateinit var livenessWebSocket: LivenessWebSocket
    private lateinit var server: MockWebServer

    private val onComplete = mockk<Action>(relaxed = true)
    private val onSessionInformationReceived = mockk<Consumer<SessionInformation>>(relaxed = true)
    private val onErrorReceived = mockk<Consumer<PredictionsException>>(relaxed = true)
    private val credentialsProvider = object : CredentialsProvider {
        override suspend fun resolve(attributes: Attributes): Credentials {
            return Credentials(
                "", "", "", null, ""
            )
        }
    }
    private val sessionInformation = FaceLivenessSessionInformation(1f, 1f, "1", "3")

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)

        server = MockWebServer()

        livenessWebSocket = LivenessWebSocket(
            credentialsProvider,
            server.url("/").toString(),
            "",
            sessionInformation,
            onSessionInformationReceived,
            onErrorReceived,
            onComplete
        )
    }

    @After
    fun shutDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `onClosing informs webSocket`() {
        val webSocket = mockk<WebSocket>(relaxed = true)
        livenessWebSocket.webSocket = webSocket

        livenessWebSocket.webSocketListener.onClosing(webSocket, 4, "closing")

        verify { webSocket.close(4, "closing") }
    }

    @Test
    fun `normal status onClosed calls onComplete`() {
        livenessWebSocket.webSocketListener.onClosed(mockk(), 1000, "closing")

        verify { onComplete.call() }
    }

    @Test
    fun `bad status onClosed calls onError`() {
        livenessWebSocket.webSocketListener.onClosed(mockk(), 5000, "closing")

        verify { onErrorReceived.accept(any()) }
    }

    @Test
    fun `onClosed does not call onError if client stopped`() {
        livenessWebSocket.clientStoppedSession = true

        livenessWebSocket.webSocketListener.onClosed(mockk(), 5000, "closing")

        verify(exactly = 0) { onErrorReceived.accept(any()) }
    }

    @Test
    fun `onFailure calls onError`() {
        // Response does noted like to be mockk
        val response = Response.Builder()
            .code(200)
            .request(Request.Builder().url(URL("https://amazon.com")).build())
            .protocol(Protocol.HTTP_2)
            .message("Response")
            .build()

        livenessWebSocket.webSocketListener.onFailure(mockk(), mockk(), response)

        verify { onErrorReceived.accept(any()) }
    }

    @Test
    fun `onFailure does not call onError if client stopped`() {
        livenessWebSocket.clientStoppedSession = true
        // Response does noted like to be mockk
        val response = Response.Builder()
            .code(200)
            .request(Request.Builder().url(URL("https://amazon.com")).build())
            .protocol(Protocol.HTTP_2)
            .message("Response")
            .build()

        livenessWebSocket.webSocketListener.onFailure(mockk(), mockk(), response)

        verify(exactly = 0) { onErrorReceived.accept(any()) }
    }

    @Test
    fun `web socket assigned on open`() {
        val openLatch = CountDownLatch(1)
        val latchingListener = LatchingWebSocketResponseListener(
            livenessWebSocket.webSocketListener,
            openLatch = openLatch
        )
        livenessWebSocket.webSocketListener = latchingListener

        server.enqueue(MockResponse().withWebSocketUpgrade(ServerWebSocketListener()))
        server.start()
        livenessWebSocket.start()

        openLatch.await(3, TimeUnit.SECONDS)

        assertTrue(livenessWebSocket.webSocket != null)
        val originalRequest = livenessWebSocket.webSocket!!.request()

        assertEquals("AWS4-HMAC-SHA256", originalRequest.url.queryParameter("X-Amz-Algorithm"))
        assertTrue(
            originalRequest.url.queryParameter("X-Amz-Credential")!!.endsWith("//rekognition/aws4_request")
        )
        assertEquals("299", originalRequest.url.queryParameter("X-Amz-Expires"))
        assertEquals("host", originalRequest.url.queryParameter("X-Amz-SignedHeaders"))
        assertEquals("AWS4-HMAC-SHA256", originalRequest.url.queryParameter("X-Amz-Algorithm"))
        assertNotNull("x-amz-user-agent")
    }

    @Test
    fun `server session event tracked`() {
        val event = ServerSessionInformationEvent(
            sessionInformation = SessionInformation(
                challenge = ServerChallenge(
                    faceMovementAndLightChallenge = FaceMovementAndLightServerChallenge(
                        ovalParameters = OvalParameters(1.0f, 2.0f, .5f, .7f),
                        lightChallengeType = LightChallengeType.SEQUENTIAL,
                        challengeConfig = ChallengeConfig(
                            1.0f,
                            1.1f,
                            1.2f,
                            1.3f,
                            1.4f,
                            1.5f,
                            1.6f,
                            1.7f,
                            1.8f,
                            1.9f
                        ),
                        colorSequences = listOf(
                            ColorSequence(FreshnessColor(listOf(0, 1, 2)), 4.0f, 5.0f)
                        )
                    )
                )
            )
        )
        val headers = mapOf(
            ":event-type" to "ServerSessionInformationEvent",
            ":content-type" to "application/json",
            ":message-type" to "event"
        )

        val data = json.encodeToString(event)
        val encodedByteString = LivenessEventStream.encode(data.toByteArray(), headers).array().toByteString()

        livenessWebSocket.webSocketListener.onMessage(mockk(), encodedByteString)

        verify { onSessionInformationReceived.accept(event.sessionInformation) }
    }

    @Test
    fun `disconnect event stops websocket`() {
        livenessWebSocket.webSocket = mockk()
        val event = DisconnectionEvent(1)
        val headers = mapOf(
            ":exception-type" to "DisconnectionEvent",
            ":content-type" to "application/json",
            ":message-type" to "event"
        )

        val data = json.encodeToString(event)
        val encodedByteString = LivenessEventStream.encode(data.toByteArray(), headers).array().toByteString()

        livenessWebSocket.webSocketListener.onMessage(mockk(), encodedByteString)

        verify { livenessWebSocket.webSocket!!.close(1000, any()) }
    }

    @Test
    fun `web socket error closes websocket`() {
        livenessWebSocket.webSocket = mockk()
        val event = ValidationException("ValidationException")
        val headers = mapOf(
            ":exception-type" to "ValidationException",
            ":content-type" to "application/json",
            ":message-type" to "event"
        )

        val data = json.encodeToString(event)
        val encodedByteString = LivenessEventStream.encode(data.toByteArray(), headers).array().toByteString()

        livenessWebSocket.webSocketListener.onMessage(mockk(), encodedByteString)

        verify { livenessWebSocket.webSocket!!.close(1000, any()) }
    }

    @Test
    @Ignore("Need to work on parsing the onMessage byteString from ServerWebSocketListener")
    fun `sendInitialFaceDetectedEvent test`() {
    }

    @Test
    @Ignore("Need to work on parsing the onMessage byteString from ServerWebSocketListener")
    fun `sendFinalEvent test`() {
    }

    @Test
    @Ignore("Need to work on parsing the onMessage byteString from ServerWebSocketListener")
    fun `sendColorDisplayedEvent test`() {
    }

    @Test
    @Ignore("Need to work on parsing the onMessage byteString from ServerWebSocketListener")
    fun `sendClientInfoEvent test`() {
    }

    @Test
    @Ignore("Need to work on parsing the onMessage byteString from ServerWebSocketListener")
    fun `sendVideoEvent test`() {
    }
}

class LatchingWebSocketResponseListener(
    private val webSocketListener: WebSocketListener,
    private val openLatch: CountDownLatch = CountDownLatch(1),
) : WebSocketListener() {

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        webSocketListener.onClosed(webSocket, code, reason)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocketListener.onClosing(webSocket, code, reason)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        webSocketListener.onFailure(webSocket, t, response)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        webSocketListener.onMessage(webSocket, text)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        webSocketListener.onMessage(webSocket, bytes)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        webSocketListener.onOpen(webSocket, response)
        openLatch.countDown()
    }
}

class ServerWebSocketListener : WebSocketListener() {
    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {}
    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {}
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {}
    override fun onMessage(webSocket: WebSocket, text: String) {}
    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {}
    override fun onOpen(webSocket: WebSocket, response: Response) {}
}
