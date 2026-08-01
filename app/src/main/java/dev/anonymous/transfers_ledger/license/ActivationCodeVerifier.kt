package dev.anonymous.transfers_ledger.license

import android.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Offline activation code verifier using ECDSA (Asymmetric Cryptography).
 *
 * Security model:
 *   - This app contains ONLY the PUBLIC KEY → can VERIFY signatures but CANNOT generate them.
 *   - The keygen app contains the PRIVATE KEY → can SIGN (generate activation codes).
 *   - Even if someone decompiles this app and extracts the public key,
 *     they CANNOT generate valid activation codes.
 *
 * Algorithm: SHA256withECDSA (secp256r1 / P-256)
 *
 * Flow:
 *   1. Keygen signs deviceHash with private key → Base64 signature = activation code
 *   2. This app verifies the signature using the public key
 */
object ActivationCodeVerifier {

    private const val PUBLIC_KEY_BASE64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE3PDWjb3iUpvMAX1BeQhhPSW8pX63/WHG0HXVCGN+no9DYNjFzq13Ze9GhgY74ZvGuxYGgSj0nLhpNJgJLLUJqA=="

    /**
     * Verifies whether [activationCode] is a valid ECDSA signature for [deviceHash].
     *
     * Returns false if the public key is not configured or the signature is invalid.
     */
    fun verify(deviceHash: String, activationCode: String): Boolean {
        if (PUBLIC_KEY_BASE64.isBlank()) return false

        return try {
            val keyBytes = Base64.decode(PUBLIC_KEY_BASE64, Base64.NO_WRAP)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("EC")
            val publicKey = keyFactory.generatePublic(keySpec)

            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(deviceHash.toByteArray(Charsets.UTF_8))

            val signatureBytes = Base64.decode(
                activationCode.trim().replace("\\s+".toRegex(), ""),
                Base64.NO_WRAP
            )
            sig.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }
}
