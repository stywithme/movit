package com.movit.feature.account

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

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
        assertEquals(true, state.isSignedIn)
        assertEquals("Mahmoud Hassan", state.profile?.name)
    }
}
