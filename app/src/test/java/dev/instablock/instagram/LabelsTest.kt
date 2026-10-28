package dev.instablock.instagram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelsTest {
    @Test
    fun matchesWholeLabelsInBothLanguages() {
        assertTrue(Labels.matches(Label.BLOCK, "Block"))
        assertTrue(Labels.matches(Label.BLOCK, "Bloquer"))
        assertTrue(Labels.matches(Label.UNBLOCK, "Débloquer"))
        // "Block and report" must never be taken for "Block".
        assertFalse(Labels.matches(Label.BLOCK, "Block and report"))
        assertFalse(Labels.matches(Label.BLOCK, "Bloquer et signaler"))
    }

    @Test
    fun matchesMessagesInsideLongerTexts() {
        assertTrue(Labels.matches(Label.NOT_AVAILABLE, "Utilisateur(ice) introuvable"))
        assertTrue(Labels.matches(Label.TRY_AGAIN_LATER, "Please try again later."))
    }

    @Test
    fun ignoresInvisibleCharactersCaseAndApostrophes() {
        assertEquals("débloquer", Labels.normalize("‎ Débloquer⁩ "))
        assertTrue(Labels.matches(Label.FOLLOW, "S’abonner"))
        assertTrue(Labels.matches(Label.UNRESTRICT, "Ne plus restreindre"))
    }

    @Test
    fun emptyTextMatchesNothing() {
        assertFalse(Labels.matches(Label.BLOCK, null))
        assertFalse(Labels.matches(Label.NOT_AVAILABLE, ""))
    }
}
