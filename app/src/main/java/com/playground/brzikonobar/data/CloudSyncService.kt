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
)

data class CloudInvitePayload(
    val apiKey: String,
    val appId: String,
    val projectId: String,
    val cafeId: String,
    val inviteCode: String,
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
    val name: String,
    val priceCents: Int,
    val emoji: String,
    val accentColor: Long,
    val sortOrder: Int,
)

data class CloudCatalogProduct(
    val cafeId: String,
    val id: String,
    val categoryId: Long,
    val name: String,
    val priceCents: Int,
    val emoji: String,
    val accentColor: Long,
    val sortOrder: Int,
)

data class CloudReceiptItem(
    val id: String,
    val receiptNumber: String,
    val waiterName: String,
    val createdAtMillis: Long,
    val totalCents: Int,
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
        )
    }

    suspend fun refreshInvite(
        config: CloudConfig,
        session: CloudSession,
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

        val cafe = cafeRef.get().await()
        val cafeName = cafe.getString("name").orEmpty()
        if (cafeName.isBlank()) {
            error("Kafić nije pronađen.")
        }

        cafeRef.collection("members")
            .document(user.uid)
            .set(
                mapOf(
                    "uid" to user.uid,
                    "name" to waiterName,
                    "role" to "waiter",
                    "joinedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()

        return CloudSession(
            cafeId = payload.cafeId,
            cafeName = cafeName,
            userId = user.uid,
            userName = waiterName,
            userRole = "waiter",
            inviteCode = payload.inviteCode,
        )
    }

    suspend fun pushReceipt(
        config: CloudConfig,
        session: CloudSession,
        receiptNumber: String,
        totalCents: Int,
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

    suspend fun addCatalogProduct(
        config: CloudConfig,
        session: CloudSession,
        product: CloudCatalogProductDraft,
    ): String {
        if (session.userRole != "admin") {
            error("Samo admin može spremati artikle u cloud.")
        }

        val document = firestore(config)
            .collection("cafes")
            .document(session.cafeId)
            .collection("catalogProducts")
            .document()

        document.set(
            mapOf(
                "categoryId" to product.categoryId,
                "name" to product.name,
                "priceCents" to product.priceCents,
                "emoji" to product.emoji,
                "accentColor" to product.accentColor,
                "sortOrder" to product.sortOrder,
                "createdByUid" to session.userId,
                "updatedAt" to System.currentTimeMillis(),
            ),
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

    fun buildInvitePayload(
        config: CloudConfig,
        cafeId: String,
        inviteCode: String,
    ): String = JSONObject()
        .put("apiKey", config.apiKey)
        .put("appId", config.appId)
        .put("projectId", config.projectId)
        .put("cafeId", cafeId)
        .put("inviteCode", inviteCode)
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
            items = items,
            itemsSummary = items.joinToString(", ") { item -> "${item.name} x${item.quantity}" },
        )
    }

    private fun DocumentSnapshot.toCloudCatalogProduct(cafeId: String): CloudCatalogProduct? {
        val categoryId = getLong("categoryId") ?: return null
        val name = getString("name").orEmpty()
        if (name.isBlank()) {
            return null
        }

        return CloudCatalogProduct(
            cafeId = cafeId,
            id = id,
            categoryId = categoryId,
            name = name,
            priceCents = (getLong("priceCents") ?: 0L).toInt(),
            emoji = getString("emoji").orEmpty().ifBlank { "🥤" },
            accentColor = (getLong("accentColor") ?: 0L),
            sortOrder = (getLong("sortOrder") ?: 0L).toInt(),
        )
    }
}
