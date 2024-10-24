package com.example.swipecards

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.viewpager2.widget.ViewPager2
import com.ringofrings.sdk.core.nfc.NFCStatus
import com.ringofrings.sdk.core.nfc.RingOfRingsNFC
import io.metamask.androidsdk.DappMetadata

import io.metamask.androidsdk.Ethereum
import io.metamask.androidsdk.EthereumMethod
import io.metamask.androidsdk.EthereumRequest
import io.metamask.androidsdk.Result
import io.metamask.androidsdk.SDKOptions
import org.web3j.abi.DefaultFunctionReturnDecoder
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.utils.Numeric
import java.lang.Thread.sleep
import java.math.BigInteger

class MainActivity : AppCompatActivity() {

    private lateinit var ethereumAddress: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize NFC and update the status
        RingOfRingsNFC.initializeRingOfRingsNFC(context = this)
        if (RingOfRingsNFC.getNFCStatus() == NFCStatus.NFC_UNSUPPORTED) {
            Toast.makeText(this, "NFT not supported", Toast.LENGTH_SHORT).show()
        } else if (RingOfRingsNFC.getNFCStatus() == NFCStatus.NFC_ENABLED) {
            Toast.makeText(this, "NFT ready", Toast.LENGTH_SHORT).show()
        }


        val images = listOf(
            R.drawable.aapl,
            R.drawable.ibm,
            R.drawable.nvda,
            R.drawable.tlsa,
            R.drawable.save,
            R.drawable.mara,
            R.drawable.djt,
            R.drawable.bivi,
            R.drawable.plug,
            R.drawable.pltr,
        )

        val dappMetadata = DappMetadata("Droid Dapp", "https://www.droiddapp.io")
        val infuraAPIKey = "1234567890"
        val readonlyRPCMap = mapOf("0x1" to "https://www.testrpc.com")

        // Create the Ethereum object and attempt to connect to the wallet
        val ethereum = Ethereum(this, dappMetadata, SDKOptions(infuraAPIKey, readonlyRPCMap))
        var ethereumAddress = ""
        ethereum.getEthAccounts { result ->
            when (result) {
                is Result.Error -> {
                    Log.e("test", "Error reading account: ${result.error.message}")
                }
                is Result.Success.Item -> {
                    ethereumAddress = result.value
                }
                is Result.Success.ItemMap -> {
                    TODO()
                }
                is Result.Success.Items -> {
                    ethereumAddress = result.value[0]
                }
            }
        }
        if (ethereumAddress.isEmpty()){
            Log.i("test", "Wallet not connected")
            val intent = Intent(this@MainActivity, WalletConnectActivity::class.java)
            startActivity(intent)
            finish()  // Close the current activity
        } else {
            Log.i("test", "Wallet connected")
        }

        // Register the activity result launcher to handle result from NFCAuthActivity
        val nfcAuthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // Get the ethereumAddress from the result
                val address = result.data?.getStringExtra("ethereumAddress")
                if (address != null) {
                    Log.i("test", "Address: $address")
                    Log.i("test", "Ethereum Address: $ethereumAddress")
                    Toast.makeText(this, "Address matches: ${address == ethereumAddress}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Failed to get Ethereum Address", Toast.LENGTH_LONG).show()
            }
        }

        val viewPager: ViewPager2 = findViewById(R.id.viewPager)


        viewPager.getChildAt(0).setOnTouchListener { _, _ -> true }

        val btnReject: Button = findViewById(R.id.btnReject)
        val btnAccept: Button = findViewById(R.id.btnAccept)
        val btnRefrsh: Button = findViewById(R.id.btnRefresh)

        btnReject.setOnClickListener {
            viewPager.currentItem = viewPager.currentItem + 1
        }

        var adapter = ImageAdapter(images, listOf("$0"))
        btnRefrsh.setOnClickListener {
            getStockPrice(ethereum, "AAPL", ethereumAddress) { price ->
                Log.i("test", "******Price: $price")
                runOnUiThread {
                    adapter.updatePrices(listOf(price))
                }
            }
        }

        viewPager.adapter = adapter

        btnAccept.setOnClickListener {
//            showCustomToast("test")
            val intent = Intent(this, NFCAuthActivity::class.java)
            nfcAuthLauncher.launch(intent) // Start NFCAuthActivity
            viewPager.currentItem = viewPager.currentItem + 1
        }

        val btnLogout: Button = findViewById(R.id.btnLogout)
        btnLogout.setOnClickListener {
            // Handle MetaMask logout
            ethereum.disconnect(true)
            val intent = Intent(this@MainActivity, WalletConnectActivity::class.java)
            startActivity(intent)
            finish()
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

    private fun getStockPrice(ethereum: Ethereum, symbol: String, userAddress: String, onSuccess: (String) -> Unit) {
        requestStockTargetPriceBySymbol(ethereum, symbol, userAddress) {
            Log.i("test", "ok5")
            //sleep(5000)
            Log.i("test", "ok7")
            getStockTargetPrice(ethereum, userAddress) { price ->
                Log.i("test", "ok8 price is $price")
                onSuccess(price)
            }
        }
    }

    private fun requestStockTargetPriceBySymbol(ethereum: Ethereum, symbol: String, userAddress: String, onSuccess: () -> Unit) {
        // Create the contract function (replace "yourFunctionName" with the actual function name)
        val function = Function(
            "requestStockTargetPriceBySymbol", // Replace with your contract function name
            listOf(Utf8String(symbol)), // Replace with actual parameters
            emptyList() // If the function returns something, replace with the correct output type
        )

        // Encode the function call as transaction data
        val encodedFunction = FunctionEncoder.encode(function)
        val currentGasPrice = BigInteger("1000000000") // Example: current gas price (1 Gwei)
        val increasedGasPrice = currentGasPrice.multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100)) // Increase by 20%

        // Prepare transaction parameters
        val params: Map<String, Any> = mutableMapOf(
            "from" to userAddress, // Populate with the user's address
            "to" to "0x700482dcac96d6b1dC17871202b7678B5de6bC8f", // Replace with the contract address
            "value" to "0x0", // Amount of Ether to send, 0 for calling function without sending Ether
            "data" to encodedFunction, // Encoded function call
            //"gas" to "0x5460", // Gas limit in hexadecimal (e.g., 21000 gas limit)
            //"gasPrice" to increasedGasPrice.toString(16) // Optional: Gas price in hexadecimal (e.g., 1 Gwei)
        )

        // Create an Ethereum request
        val sendTransactionRequest = EthereumRequest(
            method = EthereumMethod.ETH_SEND_TRANSACTION.value, // this should be ETH_SEND_TRANSACTION FOR state changing transactions
            params = listOf(params)
        )

        // Connect and send the transaction
        ethereum.connectWith(sendTransactionRequest) { result ->
            when (result) {
                is Result.Error -> {
                    Log.e("callContract", "Error calling contract: ${result.error.message}")
                }

                is Result.Success.Item -> {
                    Log.i("test", "ok1")
                    onSuccess()
                }

                is Result.Success.ItemMap -> {
                    Log.i("test", "ok2")
                    onSuccess()
                }

                is Result.Success.Items -> {
                    Log.i("test", "ok3")
                    onSuccess()
                }
            }
        }
    }

    private fun getStockTargetPrice(ethereum: Ethereum, userAddress: String, onSuccess: (String) -> Unit) {
        // Create the contract function (replace "yourFunctionName" with the actual function name)
        val function = Function(
            "stock_target_price", // The public variable name in the contract
            listOf(), // No input parameters
            listOf(object : TypeReference<Uint256>() {}) // Expect a Uint256 return type
        )

        // Encode the function call as transaction data
        val encodedFunction = FunctionEncoder.encode(function)

        // Prepare transaction parameters
        val params: Map<String, Any> = mutableMapOf(
            "from" to userAddress, // Populate with the user's address
            "to" to "0x700482dcac96d6b1dC17871202b7678B5de6bC8f", // Replace with the contract address
            "value" to "0x0", // Amount of Ether to send, 0 for calling function without sending Ether
            "data" to encodedFunction // Encoded function call
        )

        // Create an Ethereum request
        val sendTransactionRequest = EthereumRequest(
            method = EthereumMethod.ETH_CALL.value, // this should be ETH_SEND_TRANSACTION FOR state changing transactions
            params = listOf(params)
        )

        // Connect and send the transaction
        Log.i("test", "before making a call")
        try{
            ethereum.connectWith(sendTransactionRequest) { result ->
                Log.i("test", "ok9, result is $result")
                when (result) {
                    is Result.Error -> {
                        Log.e("callContract", "Error calling contract: ${result.error.message}")
                    }

                    is Result.Success.Item -> {
                        Log.i("test", "ok10")
                        onSuccess(decodeABIEncodedString(result.value))
                    }

                    is Result.Success.ItemMap -> {
                        Log.i("test", "ok11")
                        onSuccess(result.value.toString())
                    }

                    is Result.Success.Items -> {
                        Log.i("test", "ok12")
                        onSuccess(decodeABIEncodedString(result.value[0]))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("test", "Error calling contract: ${e.message}")
        }

    }
}