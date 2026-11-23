package com.example

import com.example.data.model.EsosaReactions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionPickerTest {

    @Test
    fun `esosa reactions include quick and expanded emojis without duplicates`() {
        assertEquals(6, EsosaReactions.QUICK.size)
        assertTrue(EsosaReactions.EXTRA.isNotEmpty())
        assertEquals(EsosaReactions.QUICK + EsosaReactions.EXTRA, EsosaReactions.ALLOWED)
        assertEquals(EsosaReactions.ALLOWED.size, EsosaReactions.ALLOWED.distinct().size)
        assertEquals(10, EsosaReactions.MAX_TYPES_PER_MESSAGE)
    }
}
