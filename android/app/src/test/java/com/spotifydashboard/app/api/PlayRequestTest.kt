package com.spotifydashboard.app.api

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayRequestTest {
    @Test
    fun singleTrackUsesUrisInsteadOfContext() {
        val json = Gson().toJson(PlayRequest(uris = listOf("spotify:track:t1")))
        assertEquals("{\"uris\":[\"spotify:track:t1\"]}", json)
    }

    @Test
    fun albumContextKeepsContextUri() {
        val json = Gson().toJson(PlayRequest(context_uri = "spotify:album:a1"))
        assertEquals("{\"context_uri\":\"spotify:album:a1\"}", json)
    }
}
