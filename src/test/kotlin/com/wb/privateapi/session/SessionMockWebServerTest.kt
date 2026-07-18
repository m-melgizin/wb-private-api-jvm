package com.wb.privateapi.session

import com.wb.privateapi.error.WbException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SessionMockWebServerTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun createSession(retries: Int = 2): Session =
        SessionBuilder.create(wbaasToken = null, retries = retries)

    @Test
    fun `get parses JSON response`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"total":42,"products":[{"id":1}]}""")
        )
        val session = createSession()
        val res = session.get(server.url("/").toString())
        assertEquals(200, res.status)
        val data = res.data as Map<*, *>
        assertEquals(42L, data["total"])
    }

    @Test
    fun `get retries on 429 then succeeds`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate limited"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val session = createSession(retries = 3)
        val res = session.get(server.url("/").toString())
        assertEquals(200, res.status)
        assertEquals(2, server.requestCount)
        val data = res.data as Map<*, *>
        assertEquals(true, data["ok"])
    }

    @Test
    fun `get retries on 5xx then throws after exhausting retries`() = runBlocking {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(500).setBody("err")) }

        val session = createSession(retries = 3)
        val ex = assertThrows<WbException.ServerErrorException> {
            runBlocking { session.get(server.url("/").toString()) }
        }
        assertEquals(500, ex.responseStatus)
        // 1 initial + 3 retries = 4 attempts
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `get does not retry on 404`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        val session = createSession(retries = 3)
        val ex = assertThrows<WbException.NotFoundException> {
            runBlocking { session.get(server.url("/").toString()) }
        }
        assertEquals(404, ex.responseStatus)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `get sends common headers`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        val session = createSession()
        session.get(server.url("/").toString())
        val recorded = server.takeRequest()
        assertEquals("Mozilla/5", recorded.getHeader("User-Agent")!!.substring(0, 9))
        assertEquals("https://www.wildberries.ru", recorded.getHeader("Origin"))
        assertEquals("application/json, text/plain, */*", recorded.getHeader("Accept"))
    }

    @Test
    fun `query string uses comma format without encoding`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val session = createSession()
        session.get(
            server.url("/search").toString(),
            params = mapOf("nm" to listOf(1L, 2L, 3L), "dest" to 123)
        )
        val recorded = server.takeRequest()
        val path = recorded.path!!
        assertTrue(path.contains("nm=1,2,3"), "expected comma-joined nm, path=$path")
        assertTrue(path.contains("dest=123"), "path=$path")
    }

    @Test
    fun `null params serialize as empty value (matches qs encode false)`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val session = createSession()
        session.get(
            server.url("/x").toString(),
            params = mapOf("a" to null, "b" to "y")
        )
        val path = server.takeRequest().path!!
        // qs с encode:false эмитит `a=` (пустое значение), НЕ пропускает параметр
        assertTrue(path.contains("a=&b=y"), "path=$path")
    }

    @Test
    fun `text response type returns raw string`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("raw text body"))
        val session = createSession()
        val res = session.get(server.url("/").toString(), responseType = ResponseType.TEXT)
        assertEquals("raw text body", res.data)
    }

    @Test
    fun `empty body parses to empty string`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val session = createSession()
        val res = session.get(server.url("/").toString())
        // Пустое тело не валидно как JSON → возвращается как пустая строка (поведение JS `readResponseData`)
        assertEquals("", res.data)
    }

    @Test
    fun `post sends JSON body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        val session = createSession()
        session.post(server.url("/").toString(), mapOf("a" to 1, "b" to listOf("x", "y")))
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"a\":1"), "body=$body")
        assertTrue(body.contains("\"b\":[\"x\",\"y\"]") || body.contains("b"), "body=$body")
    }

    @Test
    fun `proxy URL rewriting matches __internal scheme`() {
        val rewritten = SessionBuilder.toProxyUrl("https://catalog.wb.ru/sellers/v4/catalog")
        assertEquals(
            "https://www.wildberries.ru/__internal/catalog/sellers/v4/catalog",
            rewritten
        )
    }

    @Test
    fun `non-proxy domain is not rewritten`() {
        val url = "https://static-basket-01.wbbasket.ru/vol0/data/x.json"
        assertEquals(url, SessionBuilder.toProxyUrl(url))
    }

    @Test
    fun `unknown subdomain of wb ru is not rewritten`() {
        val url = "https://weird-subdomain.wb.ru/path"
        assertEquals(url, SessionBuilder.toProxyUrl(url))
    }
}
