package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.ringofrings.sdk.core.data.MFAChallengeResponse
import com.ringofrings.sdk.core.data.RingOfRingsMFAChallengeInterface
import com.ringofrings.sdk.core.data.RingOfRingsDataInterface
import com.ringofrings.sdk.core.mfa.RingOfRingsMFA
import com.ringofrings.sdk.core.nfc.RingOfRingsNFC
import com.ringofrings.sdk.core.nfc.RingOfRingsTag
import com.ringofrings.sdk.core.nfc.NFCStatus
import com.ringofrings.ringofrings.core.RingCore
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import com.ringofrings.sdk.core.nfc.RingOfRingsData
import android.util.Base64
import android.util.Log
import io.metamask.androidsdk.Ethereum
import io.metamask.androidsdk.EthereumRequest
import io.metamask.androidsdk.EthereumMethod
import io.metamask.androidsdk.DappMetadata
import io.metamask.androidsdk.Logger
import io.metamask.androidsdk.SDKOptions
import io.metamask.androidsdk.Result
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.log
import org.web3j.crypto.Hash
import org.web3j.crypto.Sign
import org.web3j.crypto.Credentials
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.crypto.Keys
import org.web3j.utils.Numeric
import org.web3j.abi.DefaultFunctionReturnDecoder
import org.web3j.abi.datatypes.Type
import java.security.SecureRandom


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize NFC and update the status
        RingOfRingsNFC.initializeRingOfRingsNFC(context = this)
        var nfcStatus = ""
        if (RingOfRingsNFC.getNFCStatus() == NFCStatus.NFC_UNSUPPORTED) {
            nfcStatus = "NFC is not supported on this device"
        } else if (RingOfRingsNFC.getNFCStatus() == NFCStatus.NFC_ENABLED) {
            nfcStatus = "NFC is enabled"
        }

        setContent {
            MyApplicationTheme {
                MFAAndNFCScannerScreen(nfcStatus = nfcStatus)
            }
        }
    }

    // Function to initialize MFA on the NFC tag
    private fun initializeMFA(index: List<RingOfRingsMFAChallengeInterface>, onSuccess: (Boolean) -> Unit, onFailure: (Exception) -> Unit) {
        try {
            val isInitialized = RingOfRingsMFA.initializeRingOfRingsMFA(index)
            onSuccess(isInitialized) // Handle success
        } catch (e: RingOfRingsMFA.MFAInitializationFailure) {
            onFailure(e) // Handle failure
        }
    }



    // Function to verify MFA response
    private fun verifyMFAAuthentication(response: MFAChallengeResponse, onSuccess: (Boolean) -> Unit, onFailure: (Exception) -> Unit) {
        try {
            val isVerified = RingOfRingsMFA.verifyRingOfRingsMFAAuthentication(response)
            onSuccess(isVerified) // Handle successful verification
        } catch (e: Exception) {
            onFailure(e) // Handle verification failure
        }
    }

    // Refactor ConnectWallet to be a regular function
    fun connectWallet(
        context: android.content.Context,
        ethereum: Ethereum,
        onConnectionResult: (Boolean) -> Unit // Pass a callback to return the connection status
    ) {
        // Attempt to connect to the wallet
        ethereum.connect { result ->
            when (result) {
                is Result.Error -> {
                    Log.e("ConnectWallet", "Error connecting to wallet: ${result.error.message.toString()}")
                    onConnectionResult(false)
                }
                is Result.Success.Item -> {
                    Log.i("ConnectWallet", "Success connecting to wallet")
                    onConnectionResult(true)
                }
                is Result.Success.ItemMap -> {
                    Log.i("ConnectWallet", "Success connecting to wallet")
                    onConnectionResult(true)
                }
                is Result.Success.Items -> {
                    Log.i("ConnectWallet", "Success connecting to wallet")
                    onConnectionResult(true)
                }
            }
        }
    }

    // Step 1: Read the private key from the NFC ring and sign the message
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


    // Example usage:
    fun signAndVerifyTransaction(onSuccess: (String?) -> Unit) {
        val message = "This is a test message".toByteArray() // The message to be signed

        signTransactionWithMFA(message, { success, ethereumAddress ->
            if (success) {
                onSuccess(ethereumAddress)
            }
        }, { error ->
            Log.i("test", "Failed to sign transaction: ${error.message}")
        })
    }

    // Function to start NFC polling and update status on the UI
    private fun startNFCPolling(onTagDiscovered: (RingOfRingsTag) -> RingOfRingsTag) {
        RingOfRingsNFC.startNFCTagPolling(this) { tag ->
            onTagDiscovered(tag) // Pass the tag to the callback
        }
    }

    fun decodeABIEncodedString(encodedValue: String): String {
        // Remove the "0x" prefix
        val encodedWithoutPrefix = Numeric.cleanHexPrefix(encodedValue)

        // Decode the ABI-encoded string using web3j's FunctionReturnDecoder
        val decoded = DefaultFunctionReturnDecoder.decode(
            encodedWithoutPrefix,
            listOf<TypeReference<*>>(object : TypeReference<Utf8String>() {}) as MutableList<TypeReference<Type<Any>>>?
        )

        // Extract the string value from the decoded data
        return (decoded[0] as Utf8String).value
    }

    private fun callContract(ethereum: Ethereum) {
        // Create the contract function (replace "yourFunctionName" with the actual function name)
//        val function = Function(
//            "requestStockTargetPriceBySymbol", // Replace with your contract function name
//            listOf(Utf8String("IBM")), // Replace with actual parameters
//            emptyList() // If the function returns something, replace with the correct output type
//        )

        val function = Function(
            "stock_target_price", // The public variable name in the contract
            listOf(), // No input parameters
            listOf(object : TypeReference<Uint256>() {}) // Expect a Uint256 return type
        )

        // Encode the function call as transaction data
        val encodedFunction = FunctionEncoder.encode(function)

        // Prepare transaction parameters
        val params: Map<String, Any> = mutableMapOf(
            "from" to "0x12E6Edf57ccb3e6fd55bc6d5A29C7Ebe87B0D5e7", // Populate with the user's address
            "to" to "0x63E036eCAC887CC774eaE38b64114b9227Bde600", // Replace with the contract address
            "value" to "0x0", // Amount of Ether to send, 0 for calling function without sending Ether
            "data" to encodedFunction // Encoded function call
        )

        // Create an Ethereum request
        val sendTransactionRequest = EthereumRequest(
            method = EthereumMethod.ETH_CALL.value, // this should be ETH_SEND_TRANSACTION FOR state changing transactions
            params = listOf(params)
        )

        // Connect and send the transaction
        ethereum.connectWith(sendTransactionRequest) { result ->
            when (result) {
                is Result.Error -> {
                    Log.e("callContract", "Error calling contract: ${result.error.message}")
                }

                is Result.Success.Item -> {
                    Log.i("callContract", "Transaction hash2: ${decodeABIEncodedString(result.value)}") // The transaction hash
                }

                is Result.Success.ItemMap -> {
                    // Handle response when a map is returned
                    Log.i("callContract", "Transaction hash3: ${result}") // The transaction hash
                }

                is Result.Success.Items -> {
                    // Handle response when multiple items are returned
                    Log.i("callContract", "Transaction hash4: ${result}") // The transaction hash
                }
            }
        }
    }

    @Composable
    fun MFAAndNFCScannerScreen(nfcStatus: String) {
        var isConnected by remember { mutableStateOf(false) } // Track connection state
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope() // Coroutine scope for launching async tasks
        var ethereum_address = ""

        val dappMetadata = DappMetadata("Droid Dapp", "https://www.droiddapp.io")
        val infuraAPIKey = "1234567890"
        val readonlyRPCMap = mapOf("0x1" to "https://www.testrpc.com")

        // Create the Ethereum object and attempt to connect to the wallet
        val ethereum = Ethereum(context, dappMetadata, SDKOptions(infuraAPIKey, readonlyRPCMap))
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "NFC Status: $nfcStatus")

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    // Trigger wallet connection in a coroutine
                    coroutineScope.launch {
                        connectWallet(context, ethereum) { result ->
                            isConnected = result
                            ethereum.getEthAccounts { result ->
                                Log.i("test", "Ethereum Accounts: ${result}")
                                when (result) {
                                    is Result.Error -> {
                                        Log.e("test", "Error getting Ethereum accounts: ${result.error.message.toString()}")
                                    }
                                    is Result.Success.Item -> {
                                        ethereum_address = result.value
                                    }
                                    is Result.Success.ItemMap -> {
                                        TODO()
                                    }
                                    is Result.Success.Items -> {
                                        ethereum_address = result.value[0]
                                    }
                                }
                            }
                        }
                    }
                }) {
                    Text(text = "Connect Wallet")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    signAndVerifyTransaction { ethereumAddress ->
                        val addressMatch = ethereumAddress == ethereum_address
                        Log.i("test", "The calculated Address is: $ethereumAddress")
                        Log.i("test", "The metamask Address is: $ethereum_address")
                        Log.i("test", "Ethereum Address match: $addressMatch")
                    }
                }) {
                    Text(text = "Sign transaction")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    callContract(ethereum)
                }) {
                    Text(text = "Call contract")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    startNFCPolling { tag ->
                        var data = tag.data
                        //data.encrypt("0x13f7eadb03bf5aa950b226caa82857737ed3ce5f3be09f3b9d9b51a37848f8d5")
                        data.encrypt("0xa5994b10a76d3b35cc5a05d75c5a254ae9a43e2a75d65c508f0c3dc07291c9fd")
                        RingOfRingsNFC.write(null, tag, data)
                        tag // Return the tag
                    }
                }) {
                    Text(text = "Write private key")
                }

                // Show connection status
                if (isConnected) {
                    Text(text = "Wallet Connected")
                } else {
                    Text(text = "Wallet Not Connected")
                }
            }
        }
    }


}
