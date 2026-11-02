package com.nuvio.tv.ui.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextScriptTest {
    @Test fun `an english summary does not read like an arabic title`() {
        assertFalse(writtenLikeTitle("دافنينه سوا", "Halawa and Mimi are released from prison"))
        assertTrue(writtenLikeTitle("دافنينه سوا", "ميمي وحلاوة لصّان مبتدئان"))
    }

    @Test fun `latin or unknown titles accept any text`() {
        assertTrue(writtenLikeTitle("Interstellar", "رحلة إلى الفضاء"))
        assertTrue(writtenLikeTitle("2024", "anything"))
        assertTrue(writtenLikeTitle(null, "anything"))
    }
}
