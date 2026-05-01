package com.playground.siply.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

data class CloudConfig(
    val apiKey: String,
    val appId: String,
    val projectId: String,
)

data class CloudSession(
    val cafeId: String,
    val cafeName: String,
    val userId: String,
    val userName: String,
    val userRole: String,
    val inviteCode: String,
    val canUseHouseAccount: Boolean = false,
    val canUseMusic: Boolean = false,
)

data class CloudMemberProfile(
    val name: String,
    val role: String,
    val canUseHouseAccount: Boolean,
    val canUseMusic: Boolean,
)

data class CloudInvitePayload(
    val apiKey: String,
    val appId: String,
    val projectId: String,
    val cafeId: String,
    val inviteCode: String,
    val role: String = "waiter",
)

data class CloudReceiptLine(
    val productId: Long? = null,
    val name: String,
    val quantity: Int,
    val lineTotalCents: Int,
)

data class CloudReceiptItemLine(
    val productId: Long?,
    val name: String,
    val quantity: Int,
    val lineTotalCents: Int,
)

data class CloudCatalogProductDraft(
    val categoryId: Long,
    val categoryName: String,
    val name: String,
    val priceCents: Int,
    val emoji: String,
    val imageDataUrl: String,
    val accentColor: Long,
    val sortOrder: Int,
    val isActive: Boolean,
    val stockQuantityUnits: Int,
    val stockUpdatedAtMillis: Long,
)

data class CloudCatalogProduct(
    val cafeId: String,
    val id: String,
    val categoryId: Long,
    val categoryName: String,
    val name: String,
    val priceCents: Int,
    val emoji: String,
    val imageDataUrl: String,
    val accentColor: Long,
    val sortOrder: Int,
    val isActive: Boolean,
    val stockQuantityUnits: Int,
    val stockUpdatedAtMillis: Long,
)

data class CloudReceiptItem(
    val id: String,
    val receiptNumber: String,
    val waiterName: String,
    val createdAtMillis: Long,
    val totalCents: Int,
    val note: String,
    val items: List<CloudReceiptItemLine>,
    val itemsSummary: String,
)

class CloudSyncService(
    private val context: Context,
) {
    fun defaultConfig(): CloudConfig =
        defaultConfigOrNull() ?: error("Firebase config nije dostupan u aplikaciji.")

    fun defaultConfigOrNull(): CloudConfig? {
        val app = FirebaseApp.getApps(context)
            .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(context)
            ?: return null

        val options = app.options
        val apiKey = options.apiKey.orEmpty()
        val appId = options.applicationId.orEmpty()
        val projectId = options.projectId.orEmpty()
        if (apiKey.isBlank() || appId.isBlank() || projectId.isBlank()) {
            return null
        }

        return CloudConfig(
            apiKey = apiKey,
            appId = appId,
            projectId = projectId,
        )
    }

    suspend fun createAdminCafe(
        config: CloudConfig,
        cafeName: String,
        adminName: String,
    ): CloudSession {
        val user = ensureSignedIn(config)
        val firestore = firestore(config)
        val cafeRef = firestore.collection("cafes").document()
        val inviteCode = newInviteCode()
        val now = FieldValue.serverTimestamp()

        cafeRef.set(
            mapOf(
                "name" to cafeName,
                "ownerUid" to user.uid,
                "createdAt" to now,
            ),
        ).await()

        cafeRef.collection("members")
            .document(user.uid)
            .set(
                mapOf(
                    "uid" to user.uid,
                    "name" to adminName,
                    "role" to "admin",
                    "canUseHouseAccount" to true,
                    "canUseMusic" to true,
                    "joinedAt" to now,
                ),
            )
            .await()

        cafeRef.collection("invites")
            .document(inviteCode)
            .set(
                mapOf(
                    "code" to inviteCode,
                    "active" to true,
                    "role" to "waiter",
                    "createdAt" to now,
                ),
            )
            .await()

        return CloudSession(
            cafeId = cafeRef.id,
            cafeName = cafeName,
            userId = user.uid,
            userName = adminName,
            userRole = "admin",
            inviteCode = inviteCode,
            canUseHouseAccount = true,
            canUseMusic = true,
        )
    }

    suspend fun refreshInvite(
        config: CloudConfig,
        session: CloudSession,
        role: String = "waiter",
    ): String {
        val inviteCode = newInviteCode()
        firestore(config)
            .collection("cafes")
            .document(session.cafeId)
            .collection("invites")
            .document(inviteCode)
            .set(
                mapOf(
                    "code" to inviteCode,
                    "active" to true,
                    "role" to role,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
        return inviteCode
    }

    suspend fun joinCafeAsWaiter(
        payload: CloudInvitePayload,
        waiterName: String,
    ): CloudSession {
        val config = CloudConfig(
            apiKey = payload.apiKey,
            appId = payload.appId,
            projectId = payload.projectId,
        )
        val user = ensureSignedIn(config)
        val firestore = firestore(config)
        val cafeRef = firestore.collection("cafes").document(payload.cafeId)
        val inviteRef = cafeRef.collection("invites").document(payload.inviteCode)
        val invite = inviteRef.get().await()

        if (!invite.exists() || invite.getBoolean("active") != true) {
            error("QR code više nije valjan.")
        }
        val inviteRole = invite.getString("role").orEmpty().ifBlank { "waiter" }
        if (inviteRole != "waiter") {
            error("Ovaj QR nije za spajanje konobara.")
        }

        val cafe = cafeRef.get().await()
        val cafeName = cafe.getString("name").orEmpty()
        if (cafeName.isBlank()) {
            error("Kafić nije pronađen.")
        }

        val memberRef = cafeRef.collection("members").document(user.uid)
        val existingMember = memberRef.get().await()
        if (existingMember.exists()) {
            val existingRole = existingMember.getString("role").orEmpty().ifBlank { "waiter" }
            val isAdmin = existingRole == "admin"
            return CloudSession(
                cafeId = payload.cafeId,
                cafeName = cafeName,
                userId = user.uid,
                userName = existingMember.getString("name").orEmpty().ifBlank { waiterName },
                userRole = existingRole,
                inviteCode = payload.inviteCode,
                canUseHouseAccount = isAdmin || (existingMember.getBoolean("canUseHouseAccount") ?: false),
                canUseMusic = isAdmin || (existingMember.getBoolean("canUseMusic") ?: false),
            )
        }

        memberRef.set(
            mapOf(
                "uid" to user.uid,
                "name" to waiterName,
                "role" to "waiter",
                "canUseHouseAccount" to false,
                "canUseMusic" to false,
                "joinedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()

        return CloudSession(
            cafeId = payload.cafeId,
            cafeName = cafeName,
            userId = user.uid,
            userName = waiterName,
            userRole = "waiter",
            inviteCode = payload.inviteCode,
            canUseHouseAccount = false,
            canUseMusic = false,
        )
    }

    suspend fun fetchMemberProfile(
        config: CloudConfig,
        session: CloudSession,
    ): CloudMemberProfile? {
        if (session.cafeId.isBlank() || session.userId.isBlank()) {
            return null
        }

        val snapshot = firestore(config)
            .collection("cafes")
            .document(session.cafeId)
            .collection("members")
            .document(session.userId)
            .get()
            .await()

        if (!snapshot.exists()) {
            return null
        }

        val role = snapshot.getString("role").orEmpty().ifBlank { session.userRole }
        val isAdmin = role == "admin"
        return CloudMemberProfile(
            name = snapshot.getString("name").orEmpty().ifBlank { session.userName },
            role = role,
            canUseHouseAccount = isAdmin || (snapshot.getBoolean("canUseHouseAccount") ?: false),
            canUseMusic = isAdmin || (snapshot.getBoolean("canUseMusic") ?: false),
        )
    }

    fun observeMemberProfile(
        config: CloudConfig,
        session: CloudSession,
    ): Flow<CloudMemberProfile?> = callbackFlow {
        if (session.cafeId.isBlank() || session.userId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val registration = firestore(config)
            .collection("cafes")
            .document(session.cafeId)
            .collection("members")
            .document(session.userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }

                val role = snapshot.getString("role").orEmpty().ifBlank { session.userRole }
                val isAdmin = role == "admin"
                trySend(
                    CloudMemberProfile(
                        name = snapshot.getString("name").orEmpty().ifBlank { session.userName },
                        role = role,
                        canUseHouseAccount = isAdmin || (snapshot.getBoolean("canUseHouseAccount") ?: false),
                        canUseMusic = isAdmin || (snapshot.getBoolean("canUseMusic") ?: false),
                    ),
                )
            }

        awaitClose {
            registration.remove()
        }
    }

    suspend fun pushReceipt(
        config: CloudConfig,
        session: CloudSession,
        receiptNumber: String,
        totalCents: Int,
        note: String,
        lines: List<CloudReceiptLine>,
    ) {
        if (session.cafeId.isBlank() || session.userId.isBlank()) {
            return
        }

        firestore(config)
            .collection("cafes")
            .document(session.cafeId)
            .collection("receipts")
            .add(
                mapOf(
                    "receiptNumber" to receiptNumber,
                    "waiterId" to session.userId,
                    "waiterName" to session.userName,
                    "role" to session.userRole,
                    "createdAt" to System.currentTimeMillis(),
                    "totalCents" to totalCents,
                    "note" to note,
                    "canUseHouseAccount" to session.canUseHouseAccount,
                    "canUseMusic" to session.canUseMusic,
                    "items" to lines.map { line ->
                        mapOf(
                            "productId" to line.productId,
                            "name" to line.name,
                            "quantity" to line.quantity,
                            "lineTotalCents" to line.lineTotalCents,
                        )
                    },
                ),
            )
            .await()
    }

    suspend fun pushBarOrder(
        config: CloudConfig,
        session: CloudSession,
        orderNumber: String,
        note: String,
        lines: List<CloudReceiptLine>,
    ) {
        if (session.cafeId.isBlank() || session.userId.isBlank() || lines.isEmpty()) {
            return
        }

        firestore(config)
            .collection("cafes")
            .document(session.cafeId)
            .collection("barOrders")
            .add(
                mapOf(
                    "orderNumber" to orderNumber,
                    "waiterId" to session.userId,
                    "waiterName" to session.userName,
                    "role" to session.userRole,
                    "createdAt" to System.currentTimeMillis(),
                    "completed" to false,
                    "note" to note,
                    "items" to lines.map { line ->
                        mapOf(
                            "productId" to line.productId,
                            "name" to line.name,
                            "quantity" to line.quantity,
                            "lineTotalCents" to line.lineTotalCents,
                        )
                    },
                ),
            )
            .await()
    }

    suspend fun upsertCatalogProduct(
        config: CloudConfig,
        session: CloudSession,
        cloudProductId: String?,
        product: CloudCatalogProductDraft,
    ): String {
        if (session.userRole != "admin") {
            error("Samo admin može spremati artikle u cloud.")
        }

        val collection = firestore(config)
            .collection("cafes")
            .document(session.cafeId)
            .collection("catalogProducts")
        val document = if (cloudProductId.isNullOrBlank()) {
            collection.document()
        } else {
            collection.document(cloudProductId)
        }

        document.set(
            mapOf(
                "categoryId" to product.categoryId,
                "categoryName" to product.categoryName,
                "name" to product.name,
                "priceCents" to product.priceCents,
                "emoji" to product.emoji,
                "imageDataUrl" to product.imageDataUrl,
                "accentColor" to product.accentColor,
                "sortOrder" to product.sortOrder,
                "isActive" to product.isActive,
                "stockQuantityUnits" to product.stockQuantityUnits,
                "stockUpdatedAt" to product.stockUpdatedAtMillis,
                "createdByUid" to session.userId,
                "updatedAt" to System.currentTimeMillis(),
            ),
            SetOptions.merge(),
        ).await()

        return document.id
    }

    fun observeCatalogProducts(
        config: CloudConfig,
        session: CloudSession,
    ): Flow<List<CloudCatalogProduct>> = callbackFlow {
        if (session.cafeId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration = firestore(config)
            .collection("cafes")
            .document(session.cafeId)
            .collection("catalogProducts")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                trySend(
                    snapshot.documents
                        .mapNotNull { doc -> doc.toCloudCatalogProduct(session.cafeId) }
                        .sortedWith(
                            compareBy<CloudCatalogProduct>({ it.categoryId }, { it.sortOrder }, { it.name }),
                        ),
                )
            }

        awaitClose {
            registration.remove()
        }
    }

    suspend fun fetchCatalogProducts(
        config: CloudConfig,
        session: CloudSession,
    ): List<CloudCatalogProduct> {
        if (session.cafeId.isBlank()) {
            return emptyList()
        }

        return firestore(config)
            .collection("cafes")
            .document(session.cafeId)
            .collection("catalogProducts")
            .get()
            .await()
            .documents
            .mapNotNull { doc -> doc.toCloudCatalogProduct(session.cafeId) }
            .sortedWith(compareBy<CloudCatalogProduct>({ it.categoryId }, { it.sortOrder }, { it.name }))
    }

    fun observeReceipts(
        config: CloudConfig,
        session: CloudSession,
    ): Flow<List<CloudReceiptItem>> = callbackFlow {
        if (session.cafeId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val query = firestore(config)
            .collection("cafes")
            .document(session.cafeId)
            .collection("receipts")
            .orderBy("createdAt", Query.Direction.DESCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                trySend(emptyList())
                return@addSnapshotListener
            }

            trySend(
                snapshot.documents.mapNotNull { doc ->
                    if (doc.getString("role") == "admin") {
                        null
                    } else {
                        doc.toCloudReceipt()
                    }
                }
            )
        }

        awaitClose {
            registration.remove()
        }
    }

    suspend fun deleteAllReceipts(
        config: CloudConfig,
        session: CloudSession,
    ) {
        if (session.userRole != "admin") {
            error("Samo admin može obrisati sve račune.")
        }

        val firestore = firestore(config)
        val collection = firestore
            .collection("cafes")
            .document(session.cafeId)
            .collection("receipts")
        val ordersCollection = firestore
            .collection("cafes")
            .document(session.cafeId)
            .collection("barOrders")
        val productsCollection = firestore
            .collection("cafes")
            .document(session.cafeId)
            .collection("catalogProducts")

        while (true) {
            val snapshot = collection.limit(400).get().await()
            if (snapshot.isEmpty) {
                break
            }

            firestore.runBatch { batch ->
                snapshot.documents.forEach { document ->
                    batch.delete(document.reference)
                }
            }.await()
        }

        while (true) {
            val snapshot = ordersCollection.limit(400).get().await()
            if (snapshot.isEmpty) {
                break
            }

            firestore.runBatch { batch ->
                snapshot.documents.forEach { document ->
                    batch.delete(document.reference)
                }
            }.await()
        }

        productsCollection.get().await().documents.chunked(400).forEach { documents ->
            firestore.runBatch { batch ->
                documents.forEach { document ->
                    batch.set(
                        document.reference,
                        mapOf(
                            "stockQuantityUnits" to 0,
                            "stockUpdatedAt" to System.currentTimeMillis(),
                            "updatedAt" to System.currentTimeMillis(),
                        ),
                        SetOptions.merge(),
                    )
                }
            }.await()
        }
    }

    fun buildInvitePayload(
        config: CloudConfig,
        cafeId: String,
        inviteCode: String,
        role: String = "waiter",
    ): String = JSONObject()
        .put("apiKey", config.apiKey)
        .put("appId", config.appId)
        .put("projectId", config.projectId)
        .put("cafeId", cafeId)
        .put("inviteCode", inviteCode)
        .put("role", role)
        .toString()

    fun parseInvitePayload(raw: String): CloudInvitePayload {
        val json = JSONObject(raw)
        val fallback = defaultConfigOrNull()
        val apiKey = json.optString("apiKey", fallback?.apiKey.orEmpty())
        val appId = json.optString("appId", fallback?.appId.orEmpty())
        val projectId = json.optString("projectId", fallback?.projectId.orEmpty())
        if (apiKey.isBlank() || appId.isBlank() || projectId.isBlank()) {
            error("QR payload nema valjan Firebase config.")
        }
        return CloudInvitePayload(
            apiKey = apiKey,
            appId = appId,
            projectId = projectId,
            cafeId = json.getString("cafeId"),
            inviteCode = json.getString("inviteCode"),
            role = json.optString("role", "waiter").ifBlank { "waiter" },
        )
    }

    fun buildQrMatrix(
        payload: String,
        size: Int,
    ) = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)

    private suspend fun ensureSignedIn(config: CloudConfig): FirebaseUser {
        val auth = FirebaseAuth.getInstance(firebaseApp(config))
        auth.currentUser?.let { return it }
        val result = auth.signInAnonymously().await()
        return result.user ?: error("Ne mogu prijaviti uređaj u cloud.")
    }

    private fun firestore(config: CloudConfig): FirebaseFirestore =
        FirebaseFirestore.getInstance(firebaseApp(config))

    private fun firebaseApp(config: CloudConfig): FirebaseApp {
        val appName = "bk-${config.projectId}-${config.appId.hashCode()}"
        runCatching { FirebaseApp.getInstance(appName) }.getOrNull()?.let { return it }
        FirebaseApp.getApps(context).firstOrNull { it.name == appName }?.let { return it }

        synchronized(firebaseInitLock) {
            runCatching { FirebaseApp.getInstance(appName) }.getOrNull()?.let { return it }
            FirebaseApp.getApps(context).firstOrNull { it.name == appName }?.let { return it }

            val options = FirebaseOptions.Builder()
                .setApiKey(config.apiKey)
                .setApplicationId(config.appId)
                .setProjectId(config.projectId)
                .build()

            return runCatching {
                FirebaseApp.initializeApp(context, options, appName)
            }.getOrElse {
                FirebaseApp.getInstance(appName)
            }
        }
    }

    companion object {
        private val firebaseInitLock = Any()
    }

    private fun newInviteCode(): String =
        UUID.randomUUID().toString().replace("-", "").take(10).uppercase()

    private fun DocumentSnapshot.toCloudReceipt(): CloudReceiptItem {
        val items = (get("items") as? List<*>)?.mapNotNull { raw ->
            val row = raw as? Map<*, *> ?: return@mapNotNull null
            val name = row["name"] as? String ?: return@mapNotNull null
            CloudReceiptItemLine(
                productId = (row["productId"] as? Number)?.toLong(),
                name = name,
                quantity = (row["quantity"] as? Number)?.toInt() ?: 0,
                lineTotalCents = (row["lineTotalCents"] as? Number)?.toInt() ?: 0,
            )
        }.orEmpty()

        return CloudReceiptItem(
            id = id,
            receiptNumber = getString("receiptNumber").orEmpty(),
            waiterName = getString("waiterName").orEmpty(),
            createdAtMillis = getLong("createdAt") ?: 0L,
            totalCents = (getLong("totalCents") ?: 0L).toInt(),
            note = getString("note").orEmpty(),
            items = items,
            itemsSummary = items.joinToString(", ") { item -> "${item.name} x${item.quantity}" },
        )
    }

    private fun DocumentSnapshot.toCloudCatalogProduct(cafeId: String): CloudCatalogProduct? {
        val categoryId = getLong("categoryId") ?: return null
        val categoryName = getString("categoryName")
            .orEmpty()
            .ifBlank { fallbackCategoryName(categoryId) }
        val name = getString("name").orEmpty()
        if (name.isBlank() || categoryName.isBlank()) {
            return null
        }

        return CloudCatalogProduct(
            cafeId = cafeId,
            id = id,
            categoryId = categoryId,
            categoryName = categoryName,
            name = name,
            priceCents = (getLong("priceCents") ?: 0L).toInt(),
            emoji = getString("emoji").orEmpty().ifBlank { "🥤" },
            imageDataUrl = getString("imageDataUrl").orEmpty(),
            accentColor = (getLong("accentColor") ?: 0L),
            sortOrder = (getLong("sortOrder") ?: 0L).toInt(),
            isActive = getBoolean("isActive") ?: true,
            stockQuantityUnits = (getLong("stockQuantityUnits") ?: 0L).toInt(),
            stockUpdatedAtMillis = getLong("stockUpdatedAt") ?: 0L,
        )
    }

    private fun fallbackCategoryName(categoryId: Long): String = when (categoryId) {
        1L -> "Kava"
        2L -> "Pivo"
        3L -> "Bezalkoholno"
        4L -> "Bar"
        else -> "Kategorija $categoryId"
    }
}
