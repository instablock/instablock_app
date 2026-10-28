package dev.instablock.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoreTest {
    @Test
    fun parsesNamesHandlesAndLinks() {
        assertEquals("some.user_1", Store.parseUsername("  Some.User_1 "))
        assertEquals("some.user", Store.parseUsername("@some.user"))
        assertEquals("some.user", Store.parseUsername("https://www.instagram.com/some.user/"))
        assertEquals("some.user", Store.parseUsername("instagram.com/some.user?igsh=abc"))
    }

    @Test
    fun rejectsInvalidUsernames() {
        assertNull(Store.parseUsername(""))
        assertNull(Store.parseUsername("not a username"))
        assertNull(Store.parseUsername("a".repeat(31)))
    }
}
