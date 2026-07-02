package fr.bsodium.cron.travel

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoutesClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    /** RoutesClient hardcodes the routes.googleapis.com host, so requests are redirected to the
     *  MockWebServer via an interceptor that keeps the original path, query and body intact. */
    private fun client(): RoutesClient {
        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val redirected = chain.request().url.newBuilder()
                    .scheme("http")
                    .host(server.hostName)
                    .port(server.port)
                    .build()
                chain.proceed(chain.request().newBuilder().url(redirected).build())
            }
            .build()
        return RoutesClient(apiKey = "test-key", http = http)
    }

    @Test
    fun successful_response_parses_duration_and_distance() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"routes":[{"duration":"620s","distanceMeters":4200}]}"""),
        )

        val result = client().estimate(1.0, 2.0, 3.0, 4.0, mode = RoutesClient.TravelMode.TRANSIT).getOrThrow()
        assertEquals(620L, result.durationSeconds)
        assertEquals(4200, result.distanceMeters)

        val request = server.takeRequest()
        assertEquals("test-key", request.getHeader("X-Goog-Api-Key"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("TRANSIT", body.getString("travelMode"))
    }

    @Test
    fun arrival_time_is_only_sent_for_transit_mode() = runTest {
        server.enqueue(MockResponse().setBody("""{"routes":[{"duration":"1s","distanceMeters":1}]}"""))
        client().estimate(1.0, 2.0, 3.0, 4.0, mode = RoutesClient.TravelMode.DRIVE, arrivalTimeEpochMs = 1_000L).getOrThrow()

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(!body.has("arrivalTime"))
    }

    @Test
    fun arrival_time_is_sent_as_iso_instant_for_transit_mode() = runTest {
        server.enqueue(MockResponse().setBody("""{"routes":[{"duration":"1s","distanceMeters":1}]}"""))
        client().estimate(1.0, 2.0, 3.0, 4.0, mode = RoutesClient.TravelMode.TRANSIT, arrivalTimeEpochMs = 0L).getOrThrow()

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("1970-01-01T00:00:00Z", body.getString("arrivalTime"))
    }

    @Test
    fun no_routes_in_response_is_a_failure() = runTest {
        server.enqueue(MockResponse().setBody("""{"routes":[]}"""))
        val result = client().estimate(1.0, 2.0, 3.0, 4.0)
        assertTrue(result.isFailure)
    }

    @Test
    fun missing_routes_key_is_a_failure() = runTest {
        server.enqueue(MockResponse().setBody("""{}"""))
        val result = client().estimate(1.0, 2.0, 3.0, 4.0)
        assertTrue(result.isFailure)
    }

    @Test
    fun unparsable_duration_is_a_failure() = runTest {
        server.enqueue(MockResponse().setBody("""{"routes":[{"duration":"not-a-duration","distanceMeters":1}]}"""))
        val result = client().estimate(1.0, 2.0, 3.0, 4.0)
        assertTrue(result.isFailure)
    }

    @Test
    fun http_error_response_is_a_failure() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"internal"}"""))
        val result = client().estimate(1.0, 2.0, 3.0, 4.0)
        assertTrue(result.isFailure)
    }

    @Test
    fun missing_distance_defaults_to_zero() = runTest {
        server.enqueue(MockResponse().setBody("""{"routes":[{"duration":"5s"}]}"""))
        val result = client().estimate(1.0, 2.0, 3.0, 4.0).getOrThrow()
        assertEquals(0, result.distanceMeters)
    }
}
