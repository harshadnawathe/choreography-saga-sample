package com.github.harshadnawathe.coffeehut.util

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.springframework.http.server.RequestPath
import org.springframework.web.util.pattern.PathPattern
import org.springframework.web.util.pattern.PathPatternParser

class PathMatchingDispatcher : Dispatcher() {
    private val configurations = mutableListOf<PathConfig>()

    fun addConfig(path: String, vararg responses: MockResponse) {
        require(responses.isNotEmpty()) {
            "MockResponse must be provided in the configuration"
        }

        synchronized(configurations) {
            configurations.add(
                PathConfig(
                    pattern = PathPatternParser.defaultInstance.parse(path),
                    responses = responses.asList()
                )
            )
        }
    }

    override fun dispatch(request: RecordedRequest) = responseFor(request.path)

    override fun shutdown() {
        synchronized(configurations) {
            configurations.clear()
        }
    }

    private fun responseFor(path: String) = synchronized(configurations) {
        val pathConfig = configurations.firstOrNull {
            it.matches(path)
        } ?: return@synchronized notFound(path)

        return@synchronized pathConfig.nextResponse()
    }

    private fun notFound(path: String): MockResponse = MockResponse()
        .addHeader("Content-Type", "application/json")
        .setBody("Not found: $path")
        .setResponseCode(404)
}

private class PathConfig(
    private val pattern: PathPattern,
    private var responses: List<MockResponse>
) {
    fun nextResponse() = responses.first().also {
        if (responses.size > 1) {
            responses = responses.drop(1)
        }
    }

    fun matches(path: String) = pattern.matches(RequestPath.parse(path, null))
}

fun MockWebServer.enqueue(path: String, vararg responses: MockResponse) {
    checkNotNull(this.dispatcher as? PathMatchingDispatcher) {
        "This extension can only work when dispatcher is set to a PathMatchingDispatcher"
    }.addConfig(path, *responses)
}