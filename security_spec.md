# SportzX Live TV - Firestore Security Specification

## 1. Data Invariants
1. **Authentication Gate**: No unauthenticated client (`request.auth == null`) may read or write any document in any collection.
2. **User Favorites Isolation (`/users/{userId}/favorites/{favId}`)**:
   - A favorite document can only be read, listed, created, updated, or deleted by its owner (`request.auth.uid == userId` and `resource.data.userId == request.auth.uid`).
   - `userId` and `createdAt` are immutable after creation.
   - `itemType` must be one of `'match'` or `'channel'`.
3. **Cloud Catalog Items (`/cloud_items/{itemId}`)**:
   - Stores cloud-synced admin/broadcast items (`itemType` in `['match', 'channel', 'config']`, `visibility` in `['public', 'private']`).
   - `get` and `list` require authentication and either ownership (`resource.data.ownerId == request.auth.uid`) or public visibility (`resource.data.visibility == 'public'`).
   - `create` requires `incoming().ownerId == request.auth.uid`, valid schema, and server timestamps (`<= request.time`).
   - `update` and `delete` require `existing().ownerId == request.auth.uid || isAdmin()`, with `ownerId` and `createdAt` strictly immutable.
4. **Admin Role Registry (`/admins/{adminUid}`)**:
   - Read-only for the authenticated user checking their own admin record (`request.auth.uid == adminUid`).
   - All client writes (`create`, `update`, `delete`) are forbidden (`if false`) to prevent self-assigned privilege escalation.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read**: `unauthDb.collection("cloud_items").get()` -> REJECTED.
2. **Unauthenticated Write**: `unauthDb.collection("cloud_items").doc("item_1").set(...)` -> REJECTED.
3. **Cross-User Favorite Read**: Alice reading `/users/bob_456/favorites/fav_1` -> REJECTED.
4. **Cross-User Favorite Write (Identity Spoofing)**: Alice creating `/users/alice_123/favorites/fav_1` with `userId: "bob_456"` -> REJECTED.
5. **Shadow Update (Ghost Field Injection)**: Updating `/users/alice_123/favorites/fav_1` with `isVerified: true` -> REJECTED by `affectedKeys().hasOnly(...)`.
6. **Immortal Field Mutation (`createdAt` / `ownerId`)**: Updating `ownerId` or `createdAt` on `/cloud_items/item_1` -> REJECTED.
7. **Value Poisoning (Oversized String / Wrong Type)**: Setting `title` to a 5000-char string or integer -> REJECTED by `isValidCloudCatalogItem()`.
8. **Future Timestamp Spoofing**: Setting `createdAt` to `request.time + 1 day` -> REJECTED by `<= request.time`.
9. **Email Spoofing Attack**: Token with `email == 'masfitanha@gmail.com'` but `email_verified == false` attempting admin override -> REJECTED.
10. **Self-Assigned Admin Escalation**: Client writing to `/admins/alice_123` -> REJECTED (`allow write: if false`).
11. **Unfiltered List Query on Private Cloud Items**: Bob listing `/cloud_items` without `visibility == 'public'` or `ownerId == bob_456` -> REJECTED.
12. **Cross-User Cloud Item Modification**: Non-admin Bob updating or deleting Alice's `/cloud_items/item_1` -> REJECTED.
