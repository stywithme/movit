package com.movit.feature.account

import com.movit.core.data.platform.MovitThemeModeStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovitProfileViewModelTest {

    @Test
    fun signedOut_showsSignInPrompt() = runBlocking {
        val viewModel = MovitProfileViewModel(
            repository = FakeProfileRepository(signedIn = false),
        )
        viewModel.load()

        val state = viewModel.state.value
        assertFalse(state.isSignedIn)
        assertNull(state.profile)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun signedIn_loadsProfile() = runBlocking {
        val viewModel = MovitProfileViewModel(
            repository = FakeProfileRepository(signedIn = true),
        )
        viewModel.load()

        val state = viewModel.state.value
        assertTrue(state.isSignedIn)
        assertEquals("Mahmoud Hassan", state.profile?.name)
    }

    @Test
    fun languageSelected_updatesProfileAndEmitsEffect() = runBlocking {
        val viewModel = MovitProfileViewModel(
            repository = FakeProfileRepository(signedIn = true),
        )
        viewModel.load()
        viewModel.onEvent(MovitProfileEvent.LanguageSelected("ar"))
        delay(100)

        val state = viewModel.state.value
        assertEquals("ar", state.profile?.languageCode)
        assertNull(state.activePicker)
    }

    @Test
    fun appearanceSelected_updatesThemeMode() = runBlocking {
        val viewModel = MovitProfileViewModel(
            repository = FakeProfileRepository(signedIn = true),
        )
        viewModel.load()
        viewModel.onEvent(MovitProfileEvent.AppearanceSelected(MovitThemeModeStorage.DARK))
        delay(100)

        assertEquals(MovitThemeModeStorage.DARK, viewModel.state.value.profile?.themeMode)
    }

    @Test
    fun hapticChanged_updatesNotificationsSetting() = runBlocking {
        val viewModel = MovitProfileViewModel(
            repository = FakeProfileRepository(signedIn = true),
        )
        viewModel.load()
        viewModel.onEvent(MovitProfileEvent.HapticChanged(false))
        delay(100)

        assertFalse(viewModel.state.value.profile?.hapticEnabled == true)
    }

    @Test
    fun logoutClicked_showsConfirmPicker() = runBlocking {
        val viewModel = MovitProfileViewModel(
            repository = FakeProfileRepository(signedIn = true),
        )
        viewModel.load()
        viewModel.onEvent(MovitProfileEvent.LogoutClicked)

        assertEquals(ProfilePicker.LogoutConfirm, viewModel.state.value.activePicker)
    }

    @Test
    fun editProfileClicked_emitsLocalizedComingSoon() = runBlocking {
        val viewModel = MovitProfileViewModel(
            repository = FakeProfileRepository(signedIn = true),
        )
        viewModel.load()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitProfileEvent.EditProfileClicked)
        val effect = effectDeferred.await()
        assertTrue(effect is MovitProfileEffect.ShowLocalizedMessage)
        assertEquals(
            "profile_edit_coming_soon",
            (effect as MovitProfileEffect.ShowLocalizedMessage).key,
        )
    }

    @Test
    fun subscribeNowClicked_emitsOpenSubscription() = runBlocking {
        val viewModel = MovitProfileViewModel(
            repository = FakeProfileRepository(signedIn = true),
        )
        viewModel.load()
        val effectDeferred = async {
            withTimeout(5_000) {
                viewModel.effects.first()
            }
        }
        yield()
        viewModel.onEvent(MovitProfileEvent.SubscribeNowClicked)
        assertEquals(MovitProfileEffect.OpenSubscription, effectDeferred.await())
        assertEquals(false, viewModel.state.value.showSubscription)
    }

    @Test
    fun logoutConfirmed_clearsSignedInState() = runBlocking {
        val viewModel = MovitProfileViewModel(
            repository = FakeProfileRepository(signedIn = true),
        )
        viewModel.load()
        viewModel.onEvent(MovitProfileEvent.LogoutConfirmed)
        delay(100)

        val state = viewModel.state.value
        assertFalse(state.isSignedIn)
        assertNull(state.profile)
    }
}
