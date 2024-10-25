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
import java.math.BigDecimal
import java.math.BigInteger

class MainActivity : AppCompatActivity() {

    private lateinit var ethereumAddress: String
    companion object {
        const val API_CONTRACT_ADDRESS = "0x837b764C97F542bC881ac010C5984b187704d3E4"
    }

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

        var stockSymbols = listOf("AAPL", "IBM", "NVDA", "TSLA", "SAVE", "MARA", "DJT", "BIVI", "PLUG", "PLTR")

        val viewPager: ViewPager2 = findViewById(R.id.viewPager)
        viewPager.getChildAt(0).setOnTouchListener { _, _ -> true }

        // Register the activity result launcher to handle result from NFCAuthActivity
        val nfcAuthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // Get the ethereumAddress from the result
                val address = result.data?.getStringExtra("ethereumAddress")
                if (address != null) {
                    Log.i("test", "Address: $address")
                    Log.i("test", "Ethereum Address: $ethereumAddress")
                    if (address.lowercase() == ethereumAddress.lowercase()){
                        getStockPriceWei(ethereum, ethereumAddress) { price ->
                            Log.i("test", "price obtained success $price")
                            buyStock(ethereum, stockSymbols.get(viewPager.currentItem), ethereumAddress, price) {
                                Log.i("test", "buy success")
                                Toast.makeText(this, "Buy success", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Authentication failed", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(this, "Failed to get Ethereum Address", Toast.LENGTH_LONG).show()
            }
        }

        val btnReject: Button = findViewById(R.id.btnReject)
        val btnAccept: Button = findViewById(R.id.btnAccept)
        val btnRefrsh: Button = findViewById(R.id.btnRefresh)

        btnReject.setOnClickListener {
            viewPager.currentItem = viewPager.currentItem + 1
        }

        var adapter = ImageAdapter(images, listOf("$0", "$1"))
        btnRefrsh.setOnClickListener {
            var prices = mutableListOf("$0", "$0", "$0", "$0", "$0", "$0", "$0", "$0", "$0","$0")
            prices[viewPager.currentItem] = "Searching..."
            runOnUiThread {
                adapter.updatePrices(prices)
            }
            getStockPrice(ethereum, stockSymbols.get(viewPager.currentItem), ethereumAddress) { price ->
                Log.i("test", "******Price: $price for symbol: ${stockSymbols.get(viewPager.currentItem)}")
                prices[viewPager.currentItem] = "$$price"
                runOnUiThread {
                    adapter.updatePrices(prices)
                }
            }
        }

        viewPager.adapter = adapter

        btnAccept.setOnClickListener {
            val intent = Intent(this, NFCAuthActivity::class.java)
            nfcAuthLauncher.launch(intent) // Start NFCAuthActivity
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

    private fun getStockPriceAux(ethereum: Ethereum, symbol: String, userAddress: String, iterations: Int, onSuccess: (String) -> Unit) {
        sleep(8000)
        getStockTargetPrice(ethereum, userAddress) { price ->
            if (price == "0.00" && iterations < 10) {
                // every time we call the function to request price it sets it to 0 before getting the result
                Log.i("test", "price is not yet available")
                getStockPriceAux(ethereum, symbol, userAddress, iterations + 1, onSuccess)
            } else if (iterations >= 10){
                onSuccess("Not found")
            } else {
                Log.i("test", "Price is now available")
                onSuccess(price)
            }
        }
    }

    private fun getStockPrice(ethereum: Ethereum, symbol: String, userAddress: String, onSuccess: (String) -> Unit) {
        requestStockTargetPriceBySymbol(ethereum, symbol, userAddress) {
            getStockPriceAux(ethereum, symbol, userAddress, 0) { price ->
                onSuccess(price)
            }
        }
    }

    private fun requestStockTargetPriceBySymbol(ethereum: Ethereum, symbol: String, userAddress: String, onSuccess: () -> Unit) {
        // Create the contract function (replace "yourFunctionName" with the actual function name)
        val function = Function(
            "requestStockPrice", // Replace with your contract function name
            listOf(Utf8String(symbol)), // Replace with actual parameters
            emptyList() // If the function returns something, replace with the correct output type
        )

        // Encode the function call as transaction data
        val encodedFunction = FunctionEncoder.encode(function)

        // Prepare transaction parameters
        val params: Map<String, Any> = mutableMapOf(
            "from" to userAddress, // Populate with the user's address
            "to" to API_CONTRACT_ADDRESS, // Replace with the contract address
            "value" to "0x0", // Amount of Ether to send, 0 for calling function without sending Ether
            "data" to encodedFunction, // Encoded function call
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
                is Result.Success.Item -> onSuccess()
                is Result.Success.ItemMap -> onSuccess()
                is Result.Success.Items -> onSuccess()
            }
        }
    }

    private fun getStockTargetPrice(ethereum: Ethereum, userAddress: String, onSuccess: (String) -> Unit) {
        // Create the contract function (replace "yourFunctionName" with the actual function name)
        val function = Function(
            "stock_price", // The public variable name in the contract
            listOf(), // No input parameters
            listOf(object : TypeReference<Uint256>() {}) // Expect a Uint256 return type
        )

        // Encode the function call as transaction data
        val encodedFunction = FunctionEncoder.encode(function)

        // Prepare transaction parameters
        val params: Map<String, Any> = mutableMapOf(
            "from" to userAddress, // Populate with the user's address
            "to" to API_CONTRACT_ADDRESS, // Replace with the contract address
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
                Log.i("test", "result: $result")
                when (result) {
                    is Result.Error -> {
                        Log.e("callContract", "Error calling contract: ${result.error.message}")
                    }

                    is Result.Success.Item -> onSuccess(parseHexToDecimal(result.value))
                    is Result.Success.ItemMap -> onSuccess(parseHexToDecimal(result.value.toString()))
                    is Result.Success.Items -> onSuccess(parseHexToDecimal(result.value[0]))
                }
            }
        } catch (e: Exception) {
            Log.e("test", "Error calling contract: ${e.message}")
        }
    }

    fun parseHexToDecimal(hexString: String): String {
        // Remove the "0x" prefix if it exists
        if (hexString.length == 0) {
            return "0"
        }
        val cleanedHex = hexString.removePrefix("0x")
        val decimalValue = BigInteger(cleanedHex, 16)
        // Convert the hex string to a BigInteger
        val decimalBigDecimal = BigDecimal(decimalValue)
        // Divide the BigDecimal by 1000 and keep the precision
        val dividedValue = decimalBigDecimal.divide(BigDecimal(1000))
        // Format the result to 2 decimal places
        return dividedValue.setScale(2, BigDecimal.ROUND_HALF_UP).toString()
    }

    private fun getStockPriceWei(ethereum: Ethereum, userAddress: String, onSuccess: (String) -> Unit) {
        // Create the contract function (replace "yourFunctionName" with the actual function name)
        val function = Function(
            "requiredETHInWei", // The public variable name in the contract
            listOf(), // No input parameters
            listOf(object : TypeReference<Uint256>() {}) // Expect a Uint256 return type
        )

        // Encode the function call as transaction data
        val encodedFunction = FunctionEncoder.encode(function)

        // Prepare transaction parameters
        val params: Map<String, Any> = mutableMapOf(
            "from" to userAddress, // Populate with the user's address
            "to" to API_CONTRACT_ADDRESS, // Replace with the contract address
            "value" to "0x0", // Amount of Ether to send, 0 for calling function without sending Ether
            "data" to encodedFunction // Encoded function call
        )

        // Create an Ethereum request
        val sendTransactionRequest = EthereumRequest(
            method = EthereumMethod.ETH_CALL.value, // this should be ETH_SEND_TRANSACTION FOR state changing transactions
            params = listOf(params)
        )

        // Connect and send the transaction
        try{
            ethereum.connectWith(sendTransactionRequest) { result ->
                Log.i("test", "result: $result")
                when (result) {
                    is Result.Error -> {
                        Log.e("callContract", "Error calling contract: ${result.error.message}")
                    }

                    is Result.Success.Item -> onSuccess(result.value)
                    is Result.Success.ItemMap -> onSuccess(result.value.toString())
                    is Result.Success.Items -> onSuccess(result.value[0])
                }
            }
        } catch (e: Exception) {
            Log.e("test", "Error calling contract: ${e.message}")
        }
    }

    private fun buyStock(ethereum: Ethereum, symbol: String, userAddress: String, weiPrice: String, onSuccess: () -> Unit) {
        // Create the contract function (replace "yourFunctionName" with the actual function name)
        val function = Function(
            "buyStock", // Replace with your contract function name
            listOf(Utf8String(symbol)), // Replace with actual parameters
            emptyList() // If the function returns something, replace with the correct output type
        )

        // Encode the function call as transaction data
        val encodedFunction = FunctionEncoder.encode(function)

        // Prepare transaction parameters
        val params: Map<String, Any> = mutableMapOf(
            "from" to userAddress, // Populate with the user's address
            "to" to API_CONTRACT_ADDRESS, // Replace with the contract address
            "value" to weiPrice, // Amount of Ether to send, 0 for calling function without sending Ether
            "data" to encodedFunction, // Encoded function call
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
                is Result.Success.Item -> onSuccess()
                is Result.Success.ItemMap -> onSuccess()
                is Result.Success.Items -> onSuccess()
            }
        }
    }
}