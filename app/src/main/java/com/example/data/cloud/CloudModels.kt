package com.example.data.cloud

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserFavorite(
    @DocumentId val id: String = "",
    val userId: String = "",
    val itemId: String = "",
    val itemType: String = "match",
    val title: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudCatalogItem(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val itemType: String = "match",
    val visibility: String = "public",
    val title: String = "",
    val payloadJson: String = "{}",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
