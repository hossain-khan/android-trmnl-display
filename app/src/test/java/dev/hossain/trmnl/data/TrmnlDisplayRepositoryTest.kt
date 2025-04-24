package dev.hossain.trmnl.data

import com.slack.eithernet.ApiResult
import dev.hossain.trmnl.network.TrmnlApiService
import dev.hossain.trmnl.network.model.TrmnlCurrentImageResponse
import dev.hossain.trmnl.network.model.TrmnlDisplayResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TrmnlDisplayRepository].
 */
class TrmnlDisplayRepositoryTest {
    private lateinit var repository: TrmnlDisplayRepository
    private lateinit var apiService: TrmnlApiService
    private lateinit var imageMetadataStore: ImageMetadataStore
    private lateinit var repositoryConfigProvider: RepositoryConfigProvider

    private val testAccessToken = "test-access-token"

    @Before
    fun setup() {
        apiService = mockk()
        repositoryConfigProvider = mockk()
        imageMetadataStore = mockk(relaxed = true)

        every { repositoryConfigProvider.shouldUseFakeData } returns false

        repository =
            TrmnlDisplayRepository(
                apiService = apiService,
                imageMetadataStore = imageMetadataStore,
                repositoryConfigProvider = repositoryConfigProvider,
            )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getNextDisplayData should return mapped display info when API call succeeds`() =
        runTest {
            // Arrange
            val successResponse =
                TrmnlDisplayResponse(
                    status = 200,
                    imageUrl = "https://test.com/image.png",
                    imageName = "test-image.png",
                    refreshRate = 300L,
                    error = null,
                    updateFirmware = null,
                    firmwareUrl = null,
                    resetFirmware = null,
                )
            coEvery { apiService.getNextDisplayData(testAccessToken) } returns ApiResult.success(successResponse)

            // Act
            val result = repository.getNextDisplayData(testAccessToken)

            // Assert
            assertEquals(200, result.status)
            assertEquals("https://test.com/image.png", result.imageUrl)
            assertEquals("test-image.png", result.imageName)
            assertEquals(300L, result.refreshIntervalSeconds)
            assertNull(result.error)

            // Verify metadata was saved
            coVerify { imageMetadataStore.saveImageMetadata("https://test.com/image.png", 300L) }
        }

    @Test
    fun `getNextDisplayData should handle error response`() =
        runTest {
            // Arrange
            val errorResponse =
                TrmnlDisplayResponse(
                    status = 500,
                    imageUrl = null,
                    imageName = null,
                    refreshRate = null,
                    error = "Error fetching display",
                    updateFirmware = null,
                    firmwareUrl = null,
                    resetFirmware = null,
                )
            coEvery { apiService.getNextDisplayData(testAccessToken) } returns ApiResult.success(errorResponse)

            // Act
            val result = repository.getNextDisplayData(testAccessToken)

            // Assert
            assertEquals(500, result.status)
            assertEquals("", result.imageUrl)
            assertEquals("", result.imageName)
            assertNull(result.refreshIntervalSeconds)
            assertEquals("Error fetching display", result.error)

            // Verify metadata was NOT saved (empty URL)
            coVerify(exactly = 0) { imageMetadataStore.saveImageMetadata(any(), any()) }
        }

    @Test
    fun `getCurrentDisplayData should return mapped display info when API call succeeds`() =
        runTest {
            // Arrange
            val successResponse =
                TrmnlCurrentImageResponse(
                    status = 200,
                    imageUrl = "https://test.com/current.png",
                    filename = "current-image.png",
                    refreshRateSec = 600L,
                    renderedAt = 1234567890L,
                    error = null,
                )
            coEvery { apiService.getCurrentDisplayData(testAccessToken) } returns ApiResult.success(successResponse)

            // Act
            val result = repository.getCurrentDisplayData(testAccessToken)

            // Assert
            assertEquals(200, result.status)
            assertEquals("https://test.com/current.png", result.imageUrl)
            assertEquals("current-image.png", result.imageName)
            assertEquals(600L, result.refreshIntervalSeconds)
            assertNull(result.error)

            // Verify metadata was saved
            coVerify { imageMetadataStore.saveImageMetadata("https://test.com/current.png", 600L) }
        }

    @Test
    fun `getCurrentDisplayData should handle error response`() =
        runTest {
            // Arrange
            val errorResponse =
                TrmnlCurrentImageResponse(
                    status = 500,
                    imageUrl = null,
                    filename = null,
                    refreshRateSec = null,
                    renderedAt = null,
                    error = "Device not found",
                )
            coEvery { apiService.getCurrentDisplayData(testAccessToken) } returns ApiResult.success(errorResponse)

            // Act
            val result = repository.getCurrentDisplayData(testAccessToken)

            // Assert
            assertEquals(500, result.status)
            assertEquals("", result.imageUrl)
            assertEquals("", result.imageName)
            assertNull(result.refreshIntervalSeconds)
            assertEquals("Device not found", result.error)

            // Verify metadata was NOT saved (empty URL)
            coVerify(exactly = 0) { imageMetadataStore.saveImageMetadata(any(), any()) }
        }

    @Test
    fun `getNextDisplayData should return fake data when FAKE_API_RESPONSE is true`() =
        runTest {
            // Arrange
            every { repositoryConfigProvider.shouldUseFakeData } returns true

            // Act
            val result = repository.getNextDisplayData(testAccessToken)

            // Assert
            assertEquals(200, result.status)
            assert(result.imageUrl.contains("picsum.photos"))
            assert(result.imageName.contains("picsum-mocked-image"))
            assertEquals(600L, result.refreshIntervalSeconds)
            assertNull(result.error)

            // Verify API was NOT called
            coVerify(exactly = 0) { apiService.getNextDisplayData(any()) }
        }

    @Test
    fun `getCurrentDisplayData should return fake data when FAKE_API_RESPONSE is true`() =
        runTest {
            // Arrange
            every { repositoryConfigProvider.shouldUseFakeData } returns true

            // Act
            val result = repository.getCurrentDisplayData(testAccessToken)

            // Assert
            assertEquals(200, result.status)
            assert(result.imageUrl.contains("picsum.photos"))
            assert(result.imageName.contains("picsum-mocked-image"))
            assertEquals(600L, result.refreshIntervalSeconds)
            assertNull(result.error)

            // Verify API was NOT called
            coVerify(exactly = 0) { apiService.getCurrentDisplayData(any()) }
        }
}
