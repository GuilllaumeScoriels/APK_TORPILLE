package com.torpille.app.util
import org.junit.Assert.*
import org.junit.Test

class CommunityLinkTest {
    @Test fun iosInvitationAndLegacyPathOpenSameCommunity() {
        for (base in listOf("https://torpille-38783.web.app/join", "https://torpille-38783.web.app/join.html", "torpille://join")) {
            assertEquals(PendingCommunityLink("Community_19"), CommunityLink.parse("$base?cid=Community_19&lang=en"))
        }
    }
    @Test fun unrelatedOrForgedHostIsRejected() {
        for (link in listOf("https://example.com/join?cid=c1", "https://torpille-38783.web.app.evil.com/join?cid=c1", "https://attacker@torpille-38783.web.app/join?cid=c1", "https://torpille-38783.web.app/join-other?cid=c1", "http://torpille-38783.web.app/join?cid=c1")) assertNull(CommunityLink.parse(link))
    }
    @Test fun missingAndUnsafeIDsCannotBecomeRoutes() {
        for (id in listOf("", "../users", "a/b", "hello world", "a".repeat(129))) assertNull(CommunityLink.parse("torpille://join?cid=$id"))
        assertNull(CommunityLink.parse("torpille://join?cid=%2Fusers"))
    }
}
