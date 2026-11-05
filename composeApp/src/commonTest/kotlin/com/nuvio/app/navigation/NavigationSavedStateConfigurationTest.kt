package com.nuvio.app.navigation

import androidx.navigation3.runtime.NavKey
import com.nuvio.app.navigationSavedStateConfiguration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertNotNull

@OptIn(ExperimentalSerializationApi::class)
class NavigationSavedStateConfigurationTest {
    @Test
    fun whatsNewRouteIsRegisteredForSavedStateSerialization() {
        val serializer = navigationSavedStateConfiguration.serializersModule
            .getPolymorphic(NavKey::class, WhatsNewSettingsRoute("What's New"))

        assertNotNull(serializer)
    }

    @Test
    fun magnetRouteIsRegisteredForSavedStateSerialization() {
        val serializer = navigationSavedStateConfiguration.serializersModule
            .getPolymorphic(
                NavKey::class,
                MagnetRoute(url = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"),
            )

        assertNotNull(serializer)
    }
}