package dev.anonymous.transfers_ledger.core.backup

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import dev.anonymous.transfers_ledger.data.local.db.CustomerEntity
import dev.anonymous.transfers_ledger.data.local.db.CustomerIdentifierEntity
import dev.anonymous.transfers_ledger.data.local.db.TransactionEntity
import dev.anonymous.transfers_ledger.data.repository.BackupData
import dev.anonymous.transfers_ledger.domain.model.DirectionSource
import dev.anonymous.transfers_ledger.domain.model.IdentifierType
import dev.anonymous.transfers_ledger.domain.model.TransactionDirection
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object BackupCodec {
    private const val SECRET = "MySuperSecretKey123"
    private const val HMAC_ALGORITHM = "HmacSHA256"

    fun encode(data: BackupData): String {
        val payload = JSONObject()
            .put("transactions", JSONArray().also { array ->
                data.transactions.forEach { array.put(it.toJson()) }
            })
            .put("customers", JSONArray().also { array ->
                data.customers.forEach { array.put(it.toJson()) }
            })
            .put("customerIdentifiers", JSONArray().also { array ->
                data.identifiers.forEach { array.put(it.toJson()) }
            })

        val payloadText = payload.toString()
        val payloadBase64 = Base64.encodeToString(payloadText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return JSONObject()
            .put("payload", payloadBase64)
            .put("hmac", hmac(payloadText))
            .toString(2)
    }

    fun decode(content: String): BackupData {
        val root = JSONObject(content)
        val payloadBase64 = root.getString("payload")
        val payloadText = String(Base64.decode(payloadBase64, Base64.NO_WRAP), Charsets.UTF_8)
        val expectedHmac = hmac(payloadText)
        val actualHmac = root.getString("hmac")
        if (!expectedHmac.equals(actualHmac, ignoreCase = true)) {
            throw SecurityException("Backup HMAC mismatch")
        }

        val payload = JSONObject(payloadText)
        return BackupData(
            transactions = payload.getJSONArray("transactions").mapObjects { it.toTransaction() },
            customers = payload.getJSONArray("customers").mapObjects { it.toCustomer() },
            identifiers = payload.getJSONArray("customerIdentifiers").mapObjects { it.toIdentifier() }
        )
    }

    private fun hmac(value: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(SECRET.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))
        return mac.doFinal(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }
    }

    private fun TransactionEntity.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("notificationKey", notificationKey)
            .put("transactionReference", transactionReference ?: JSONObject.NULL)
            .put("senderName", senderName)
            .put("normalizedSender", normalizedSender)
            .put("customerId", customerId ?: JSONObject.NULL)
            .put("amount", amount)
            .put("direction", direction.name)
            .put("directionSource", directionSource.name)
            .put("paymentType", paymentType)
            .put("timestamp", timestamp)
            .put("rawText", rawText)
            .put("rawTitle", rawTitle)
            .put("walletSource", walletSource)
            .put("excluded", excluded)
    }

    private fun CustomerEntity.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("displayName", displayName)
            .put("createdAt", createdAt)
            .put("updatedAt", updatedAt)
            .put("defaultOutgoing", defaultOutgoing)
            .put("defaultExcluded", defaultExcluded)
    }

    private fun CustomerIdentifierEntity.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("customerId", customerId)
            .put("type", type.name)
            .put("value", value)
            .put("normalizedValue", normalizedValue)
            .put("createdAt", createdAt)
    }

    private fun JSONObject.toTransaction(): TransactionEntity {
        return TransactionEntity(
            id = getLong("id"),
            notificationKey = getString("notificationKey"),
            transactionReference = nullableString("transactionReference"),
            senderName = getString("senderName"),
            normalizedSender = getString("normalizedSender"),
            customerId = nullableLong("customerId"),
            amount = getDouble("amount"),
            direction = TransactionDirection.valueOf(getString("direction")),
            directionSource = DirectionSource.valueOf(getString("directionSource")),
            paymentType = getString("paymentType"),
            timestamp = getLong("timestamp"),
            rawText = getString("rawText"),
            rawTitle = getString("rawTitle"),
            walletSource = getString("walletSource"),
            excluded = optBoolean("excluded", false)
        )
    }

    private fun JSONObject.toCustomer(): CustomerEntity {
        return CustomerEntity(
            id = getLong("id"),
            displayName = getString("displayName"),
            createdAt = getLong("createdAt"),
            updatedAt = getLong("updatedAt"),
            defaultOutgoing = optBoolean("defaultOutgoing", false),
            defaultExcluded = optBoolean("defaultExcluded", false)
        )
    }

    private fun JSONObject.toIdentifier(): CustomerIdentifierEntity {
        return CustomerIdentifierEntity(
            id = getLong("id"),
            customerId = getLong("customerId"),
            type = IdentifierType.valueOf(getString("type")),
            value = getString("value"),
            normalizedValue = getString("normalizedValue"),
            createdAt = getLong("createdAt")
        )
    }

    private fun JSONObject.nullableString(name: String): String? {
        return if (isNull(name)) null else getString(name)
    }

    private fun JSONObject.nullableLong(name: String): Long? {
        return if (isNull(name)) null else getLong(name)
    }

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
        return List(length()) { index -> transform(getJSONObject(index)) }
    }
}
