package com.example.swipecards

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ringofrings.sdk.core.nfc.RingOfRingsNFC
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.Sign

class NFCAuthActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_auth)

        // Show custom toast message
        showCustomToast("Authenticate with ring")

        // Here you will add NFC ring authentication logic...
        signAndVerifyTransaction { ethereumAddress ->
            val intent = Intent()
            intent.putExtra("ethereumAddress", ethereumAddress)
            setResult(RESULT_OK, intent)
            finish() // Close NFCAuthActivity
        }
    }
    private fun signTransactionWithMFA(
        message: ByteArray, // The message to be signed
        onSuccess: (Boolean, String?) -> Unit, // Callback with the success result and Ethereum address
        onFailure: (Exception) -> Unit
    ) {
        var success = false
        var ethereumAddress: String? = null
        try {
            // Start NFC polling to read the private key from the ring
            RingOfRingsNFC.startNFCTagPolling(this) { tag ->
                // Assuming the private key is read from the NFC tag's data as a string
                val privateKeyString = tag.data.data.toString()

                // Convert the private key string to a usable format for signing
                val credentials = Credentials.create(privateKeyString) // Convert the private key to credentials

                // Sign the message using the private key
                val signedMessage = signMessageWithPrivateKey(credentials, message)

                // Recover the public key from the signed message
                val publicKey = recoverPublicKeyFromSignature(message, signedMessage)
                // Derive the Ethereum address from the public key
                ethereumAddress = deriveEthereumAddressFromPublicKey(publicKey)
                success = true
                onSuccess(success, ethereumAddress)
                tag // Transaction signed successfully, return the Ethereum address
            }
        } catch (e: Exception) {
            onFailure(e) // Handle signing failure
        }
    }

    // Step 2: Sign the message with the private key (using web3j)
    private fun signMessageWithPrivateKey(credentials: Credentials, message: ByteArray): ByteArray {
        // Sign the message with the private key using web3j
        val signatureData = Sign.signPrefixedMessage(message, credentials.ecKeyPair)

        // Combine the R, S, and V values into a single byte array (Ethereum signature format)
        return signatureData.r + signatureData.s + signatureData.v
    }

    // Step 3: Recover the public key from the signature
    private fun recoverPublicKeyFromSignature(message: ByteArray, signature: ByteArray): ByteArray {
        val signatureData = Sign.SignatureData(
            signature[64], // V value
            signature.sliceArray(0..31), // R value
            signature.sliceArray(32..63)  // S value
        )

        // Recover the public key from the signature
        return Sign.signedPrefixedMessageToKey(message, signatureData).toByteArray()
    }

    // Step 4: Derive the Ethereum address from the public key
    fun deriveEthereumAddressFromPublicKey(publicKey: ByteArray): String {
        // Check if the public key starts with 0x04 (uncompressed format indicator)
        val publicKeyWithoutPrefix = if (publicKey.isNotEmpty() && publicKey[0] == 0x0.toByte()) {
            publicKey.copyOfRange(1, publicKey.size) // Remove the 0x04 prefix
        } else {
            publicKey // No prefix to remove, use the original public key
        }

        // Hash the public key with Keccak-256
        val publicKeyHash = Hash.sha3(publicKeyWithoutPrefix)

        // The Ethereum address is the last 20 bytes of the Keccak-256 hash
        val ethereumAddress = publicKeyHash.takeLast(20).joinToString("") { "%02x".format(it) }

        return "0x$ethereumAddress"
    }

    fun signAndVerifyTransaction(onSuccess: (String?) -> Unit) {
        val message = "This is a test message".toByteArray() // The message to be signed

        signTransactionWithMFA(message, { success, ethereumAddress ->
            if (success) {
                onSuccess(ethereumAddress)
            }
        }, { error ->
            showCustomToast("Failed to sign transaction: ${error.message}")
        })
    }

    // Function to show custom toast
    private fun showCustomToast(message: String) {
        val inflater: LayoutInflater = layoutInflater
        val layout: View = inflater.inflate(R.layout.custom_toast, findViewById(R.id.toastRoot))

        // Set the text for the toast
        val toastMessage: TextView = layout.findViewById(R.id.toastMessage)
        toastMessage.text = message

        // Create and show the toast
        val toast = Toast(applicationContext)
        toast.duration = Toast.LENGTH_LONG
        toast.view = layout
        toast.show()
    }
}
