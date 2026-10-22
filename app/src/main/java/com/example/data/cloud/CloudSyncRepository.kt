package com.example.data.cloud

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    // CRITICAL: Always initialize with R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun sanitizeDocId(rawId: String): String {
        val cleaned = rawId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        return cleaned.ifBlank { "item_${System.currentTimeMillis()}" }
    }

    /**
     * Real-time stream of public cloud catalog items (matches, channels, and global config)
     * pushed from the Admin Panel or Firebase Console / Web Server.
     */
    fun observePublicCatalogItems(): Flow<List<CloudCatalogItem>> {
        val path = "cloud_items"
        return db.collection(path)
            .whereEqualTo("visibility", "public")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudCatalogItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    /**
     * Real-time stream of a user's synced favorites.
     */
    fun observeUserFavorites(userId: String): Flow<List<UserFavorite>> {
        val path = "users/$userId/favorites"
        return db.collection("users").document(userId).collection("favorites")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    UserFavorite::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun addFavorite(itemId: String, itemType: String, title: String): Result<String> {
        val uid = requireUserId()
        val safeFavId = sanitizeDocId("${itemType}_$itemId")
        val docRef = db.collection("users").document(uid).collection("favorites").document(safeFavId)
        return try {
            val payload = mapOf(
                "userId" to uid,
                "itemId" to itemId.take(128),
                "itemType" to if (itemType == "channel") "channel" else "match",
                "title" to title.ifBlank { itemId }.take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(safeFavId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun removeFavorite(favId: String): Result<Unit> {
        val uid = requireUserId()
        val safeFavId = sanitizeDocId(favId)
        val docRef = db.collection("users").document(uid).collection("favorites").document(safeFavId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getFavoriteById(targetUserId: String, favId: String): Result<UserFavorite?> {
        val docRef = db.collection("users").document(targetUserId).collection("favorites").document(favId)
        return try {
            val snap = docRef.get(com.google.firebase.firestore.Source.SERVER).await()
            Result.success(snap.toObject(UserFavorite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUserFavorites(): Result<List<UserFavorite>> {
        val uid = requireUserId()
        val path = "users/$uid/favorites"
        return try {
            val snap = db.collection("users").document(uid).collection("favorites")
                .whereEqualTo("userId", uid)
                .get()
                .await()
            Result.success(snap.toObjects(UserFavorite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    /**
     * Publishes or updates a cloud broadcast item (match, channel, or global app config)
     * so all connected users receive it in real time.
     */
    suspend fun upsertPublicCatalogItem(
        rawItemId: String,
        itemType: String,
        title: String,
        payloadJson: String
    ): Result<String> {
        val uid = requireUserId()
        val docId = sanitizeDocId("${itemType}_$rawItemId")
        val docRef = db.collection("cloud_items").document(docId)
        return try {
            val existingSnap = try {
                docRef.get().await()
            } catch (_: Exception) {
                null
            }
            val safeType = when (itemType) {
                "channel" -> "channel"
                "config" -> "config"
                else -> "match"
            }
            val safeTitle = title.ifBlank { docId }.take(300)
            val safePayload = payloadJson.ifBlank { "{}" }.take(200000)

            if (existingSnap != null && existingSnap.exists()) {
                val updateData = mapOf(
                    "itemType" to safeType,
                    "visibility" to "public",
                    "title" to safeTitle,
                    "payloadJson" to safePayload,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updateData).await()
            } else {
                val createData = mapOf(
                    "ownerId" to uid,
                    "itemType" to safeType,
                    "visibility" to "public",
                    "title" to safeTitle,
                    "payloadJson" to safePayload,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createData).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deletePublicCatalogItem(rawItemId: String, itemType: String): Result<Unit> {
        requireUserId()
        val docId = sanitizeDocId("${itemType}_$rawItemId")
        val docRef = db.collection("cloud_items").document(docId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
