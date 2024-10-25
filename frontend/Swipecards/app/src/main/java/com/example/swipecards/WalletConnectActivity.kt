package com.example.swipecards

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.metamask.androidsdk.DappMetadata
import io.metamask.androidsdk.Ethereum
import io.metamask.androidsdk.Result
import io.metamask.androidsdk.SDKOptions

class WalletConnectActivity : AppCompatActivity() {

    private lateinit var ethereum: Ethereum

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet_connect)

        // Set up Ethereum object for MetaMask connection
        val dappMetadata = DappMetadata("TinderStock", "https://www.neurosynapse.io")
        val infuraAPIKey = "1234567890"
        val readonlyRPCMap = mapOf("0x1" to "https://www.testrpc.com")
        ethereum = Ethereum(this, dappMetadata, SDKOptions(infuraAPIKey, readonlyRPCMap))

        // Find the "connect" button
        val btnConnect: Button = findViewById(R.id.btnConnect)

        // Set up the connect button's click listener
        btnConnect.setOnClickListener {
            connectWallet(this, ethereum) { isConnected ->
                if (isConnected) {
                    // If the wallet is connected, navigate back to the MainActivity
                    try {
                        val intent = Intent(this@WalletConnectActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    } catch (e: Exception) {
                        Log.e("WalletConnectActivity", "Error occurred while starting main: ${e.message}")
                    }
                     // Close this activity
                } else {
                    // If connection fails, show an error message
                    Log.i("test", "Wallet connected failing main")
                    Toast.makeText(this, "Failed to connect to wallet", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Function to handle wallet connection
    private fun connectWallet(
        context: android.content.Context,
        ethereum: Ethereum,
        onConnectionResult: (Boolean) -> Unit
    ) {
        // Attempt to connect to the wallet
        Log.i("ConnectWallet", "Attempting to connect to wallet")
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
}
