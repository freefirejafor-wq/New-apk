package com.example.data.cloud

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun addFavorite_and_getUserFavorites_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = CloudSyncRepository(firestore, auth)
        val uniqueMatchId = "match_${UUID.randomUUID().toString().take(8)}"

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addFavorite(uniqueMatchId, "match", "India vs Australia")
        }
        assertTrue(createResult.isSuccess)
        val favId = createResult.getOrThrow()

        val listResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getUserFavorites()
        }
        assertTrue(listResult.isSuccess)
        assertTrue(listResult.getOrThrow().any { it.id == favId })
    }

    @Test
    fun getFavoriteById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudSyncRepository(firestore, auth)
        val uniqueMatchId = "match_${UUID.randomUUID().toString().take(8)}"
        val favId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addFavorite(uniqueMatchId, "match", "Bangladesh vs Sri Lanka").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CloudSyncRepository(firestore, auth)
        val crossResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getFavoriteById(aliceUid, favId)
        }
        assertTrue(crossResult.isFailure)
        val ex = crossResult.exceptionOrNull() as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun upsertPublicCatalogItem_and_observePublicCatalogItems_emitsRealtimeUpdate() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = CloudSyncRepository(firestore, auth)
        val rawId = "live_${UUID.randomUUID().toString().take(8)}"

        val writeResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertPublicCatalogItem(
                rawItemId = rawId,
                itemType = "match",
                title = "IND vs AUS Live Stream",
                payloadJson = """{"id":"$rawId","team1Name":"India","team2Name":"Australia"}"""
            )
        }
        assertTrue(writeResult.isSuccess)
        val docId = writeResult.getOrThrow()

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePublicCatalogItems().first { list -> list.any { it.id == docId } }
        }
        assertTrue(emitted.any { it.id == docId })
    }

    @Test
    fun observePublicCatalogItems_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = CloudSyncRepository(firestore, auth)

        var caughtCode: FirebaseFirestoreException.Code? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observePublicCatalogItems().first()
            }
        } catch (e: FirebaseFirestoreException) {
            caughtCode = e.code
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caughtCode)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@sportzx.test"
        const val BOB_EMAIL = "bob@sportzx.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
