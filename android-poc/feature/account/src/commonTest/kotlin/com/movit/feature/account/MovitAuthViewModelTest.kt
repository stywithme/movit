package com.movit.feature.account

import com.movit.shared.AppResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovitAuthViewModelTest {

    @Test
    fun validateSignIn_requiresEmailAndPassword() {
        assertNotNull(MovitAuthViewModel.validateSignIn("", "secret"))
        assertNotNull(MovitAuthViewModel.validateSignIn("user@test.com", ""))
        assertNull(MovitAuthViewModel.validateSignIn("user@test.com", "secret"))
    }

    @Test
    fun validateSignIn_requiresValidEmail() {
        assertNotNull(MovitAuthViewModel.validateSignIn("invalid", "secret"))
    }

    @Test
    fun validateSignUp_requiresNameEmailAndPasswordLength() {
        assertNotNull(MovitAuthViewModel.validateSignUp("", "user@test.com", "12345678"))
        assertNotNull(MovitAuthViewModel.validateSignUp("Athlete", "bad", "12345678"))
        assertNotNull(MovitAuthViewModel.validateSignUp("Athlete", "user@test.com", "short"))
        assertNull(MovitAuthViewModel.validateSignUp("Athlete", "user@test.com", "12345678"))
    }

    @Test
    fun validateForgotPassword_requiresValidEmail() {
        assertNotNull(MovitAuthViewModel.validateForgotPassword(""))
        assertNotNull(MovitAuthViewModel.validateForgotPassword("invalid"))
        assertNull(MovitAuthViewModel.validateForgotPassword("user@test.com"))
    }

    @Test
    fun resolveBootstrapTarget_activeSessionSkipsSplash() {
        val target = MovitAuthViewModel.resolveBootstrapTarget(
            AuthBootstrapContext(
                movitDataInstalled = true,
                hasActiveSession = true,
                introSeen = false,
            ),
        )
        assertEquals(AuthBootstrapTarget.ActiveSession, target)
    }

    @Test
    fun resolveBootstrapTarget_introSeenGoesToSignIn() {
        val target = MovitAuthViewModel.resolveBootstrapTarget(
            AuthBootstrapContext(
                movitDataInstalled = true,
                hasActiveSession = false,
                introSeen = true,
            ),
        )
        assertEquals(AuthBootstrapTarget.SignIn, target)
    }

    @Test
    fun resolveBootstrapTarget_firstLaunchShowsSplashThenIntro() {
        val target = MovitAuthViewModel.resolveBootstrapTarget(
            AuthBootstrapContext(
                movitDataInstalled = true,
                hasActiveSession = false,
                introSeen = false,
            ),
        )
        assertEquals(AuthBootstrapTarget.SplashThenIntro, target)
    }

    @Test
    fun introSkip_movesToSignInAndMarksIntroSeen() {
        var introMarked = false
        val viewModel = MovitAuthViewModel(
            repository = FakeAuthRepository(),
            bootstrap = AuthBootstrapContext(
                movitDataInstalled = true,
                hasActiveSession = false,
                introSeen = false,
                onIntroSeen = { introMarked = true },
            ),
            initialScreen = AuthScreen.Intro,
        )
        viewModel.onEvent(MovitAuthEvent.IntroSkipClicked)

        assertEquals(AuthScreen.SignIn, viewModel.state.value.screen)
        assertTrue(introMarked)
    }

    @Test
    fun introContinue_lastPageMovesToSignIn() {
        var introMarked = false
        val viewModel = MovitAuthViewModel(
            repository = FakeAuthRepository(),
            bootstrap = AuthBootstrapContext(
                movitDataInstalled = true,
                hasActiveSession = false,
                introSeen = false,
                onIntroSeen = { introMarked = true },
            ),
            initialScreen = AuthScreen.Intro,
        )
        repeat(MovitAuthViewModel.INTRO_PAGE_COUNT) {
            viewModel.onEvent(MovitAuthEvent.IntroContinueClicked)
        }

        assertEquals(AuthScreen.SignIn, viewModel.state.value.screen)
        assertTrue(introMarked)
    }

    @Test
    fun googleSignIn_emitsStubMessage() = runBlocking {
        val viewModel = MovitAuthViewModel(
            repository = FakeAuthRepository(),
            bootstrap = previewBootstrap(),
            initialScreen = AuthScreen.SignIn,
        )
        viewModel.onEvent(MovitAuthEvent.GoogleSignInClicked)

        val effect = viewModel.effects.first()
        assertTrue(effect is MovitAuthEffect.ShowMessage)
        assertEquals(
            MovitAuthViewModel.GOOGLE_SIGN_IN_STUB_MESSAGE,
            (effect as MovitAuthEffect.ShowMessage).message,
        )
    }

    @Test
    fun forgotPassword_successShowsSentState() = runBlocking {
        val viewModel = MovitAuthViewModel(
            repository = FakeAuthRepository(),
            bootstrap = previewBootstrap(),
            initialScreen = AuthScreen.Forgot,
        )
        viewModel.onEvent(MovitAuthEvent.EmailChanged("user@test.com"))
        viewModel.onEvent(MovitAuthEvent.ForgotSubmitClicked)
        delay(50)

        val state = viewModel.state.value
        assertTrue(state.forgotPasswordSent)
        assertFalse(state.isLoading)
        assertEquals(AuthScreen.Forgot, state.screen)
    }

    @Test
    fun fakeLogin_successWithDemoCredentials() = runBlocking {
        val repository = FakeAuthRepository()
        val result = repository.login("demo@movit.app", "demo1234")
        assertTrue(result is AppResult.Success)
        assertEquals("demo@movit.app", result.value.email)
    }

    @Test
    fun fakeRegister_successWithValidFields() = runBlocking {
        val repository = FakeAuthRepository()
        val result = repository.register("Athlete", "new@test.com", "password1")
        assertTrue(result is AppResult.Success)
        assertEquals("new@test.com", result.value.email)
    }

    private fun previewBootstrap(): AuthBootstrapContext = AuthBootstrapContext(
        movitDataInstalled = false,
        hasActiveSession = false,
        introSeen = true,
    )
}
