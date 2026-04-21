package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TypedViewModelFactoryTest {

    class SampleViewModel(val payload: String) : ViewModel()

    class OtherViewModel : ViewModel()

    @Test
    fun create_returnsInstanceFromCreator() {
        val factory = typedViewModelFactory { SampleViewModel("hello") }

        val instance = factory.create(SampleViewModel::class.java)

        assertEquals("hello", instance.payload)
    }

    @Test
    fun create_invokesCreatorEachTime() {
        var callCount = 0
        val factory = typedViewModelFactory {
            callCount += 1
            SampleViewModel("#$callCount")
        }

        val first = factory.create(SampleViewModel::class.java)
        val second = factory.create(SampleViewModel::class.java)

        assertEquals(2, callCount)
        assertNotSame(first, second)
        assertEquals("#1", first.payload)
        assertEquals("#2", second.payload)
    }

    @Test
    fun create_acceptsSuperclassRequests() {
        val sample = SampleViewModel("ok")
        val factory = typedViewModelFactory { sample }

        val instance = factory.create(ViewModel::class.java)

        assertSame(sample, instance)
    }

    @Test
    fun create_rejectsUnrelatedViewModelClass() {
        val factory = typedViewModelFactory { SampleViewModel("ignored") }

        val error = assertThrows(IllegalArgumentException::class.java) {
            factory.create(OtherViewModel::class.java)
        }
        assertTrue(
            "expected class name in message, was: ${error.message}",
            error.message?.contains("OtherViewModel") == true,
        )
    }
}
