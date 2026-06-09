package com.movit.feature.account

import com.movit.shared.AppResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun fakeLogin_successWithDemoCredentials() = runBlocking {
        val repository = FakeAuthRepository()
        val result = repository.login("demo@movit.app", "demo1234")
        assertTrue(result is AppResult.Success)
        assertEquals("demo@movit.app", result.value.email)
    }

}
