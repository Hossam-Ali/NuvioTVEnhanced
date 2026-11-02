package com.nuvio.tv.core.collections

import com.nuvio.tv.core.sync.HomeCatalogSettingsSyncService
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.domain.model.HomeLayout
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeSetupServiceTest {
    private val layout = mockk<LayoutPreferenceDataStore>(relaxed = true)
    private val collections = mockk<CollectionsDataStore>(relaxed = true)
    private val sync = mockk<HomeCatalogSettingsSyncService>(relaxed = true)
    private val service = HomeSetupService(layout, collections, sync)

    @Test
    fun `files that are not a home setup are refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { service.import("""{"format":"something-else"}""") }
        }
    }

    @Test
    fun `an import applies the parts the file has and leaves the rest`() = runTest {
        every { collections.importFromJson(any()) } returns emptyList()
        val result = service.import(
            """{"format":"nuvio-home-setup","version":1,
                "home":{"layout":"GRID","landscapePosters":true,"rowOrder":["a","b","c"],"hiddenRows":["b"],"rowTitles":{"a":"Mine"}}}"""
        )
        assertEquals(3, result.rows)
        coVerify { layout.setLayout(HomeLayout.GRID) }
        coVerify { layout.setModernLandscapePostersEnabled(true) }
        coVerify { layout.setHomeCatalogOrderKeys(listOf("a", "b", "c")) }
        coVerify { layout.setDisabledHomeCatalogKeys(listOf("b")) }
        coVerify { layout.setCustomCatalogTitles(mapOf("a" to "Mine")) }
        coVerify(exactly = 0) { layout.setHeroSectionEnabled(any()) }
        coVerify(exactly = 0) { collections.setCollections(any()) }
        verify { sync.triggerPush() }
    }

    @Test
    fun `imported collections replace those with the same id and keep the others`() = runTest {
        val existing = listOf(
            com.nuvio.tv.domain.model.Collection(id = "keep", title = "Keep"),
            com.nuvio.tv.domain.model.Collection(id = "same", title = "Old"),
        )
        val incoming = listOf(com.nuvio.tv.domain.model.Collection(id = "same", title = "New"))
        every { collections.importFromJson(any()) } returns incoming
        coEvery { collections.getCurrentCollections() } returns existing
        val result = service.import("""{"format":"nuvio-home-setup","collections":[{"id":"same"}]}""")
        assertEquals(1, result.collections)
        coVerify { collections.setCollections(listOf(existing[0]) + incoming) }
    }
}
