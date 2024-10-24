//SPDX-License-Identifier: MIT
pragma solidity ^0.8.7;

import {Chainlink, ChainlinkClient} from "@chainlink/contracts/src/v0.8/ChainlinkClient.sol";
import {ConfirmedOwner} from "@chainlink/contracts/src/v0.8/shared/access/ConfirmedOwner.sol";
import {LinkTokenInterface} from "@chainlink/contracts/src/v0.8/shared/interfaces/LinkTokenInterface.sol";
import "@chainlink/contracts/src/v0.8/shared/interfaces/AggregatorV3Interface.sol";

interface IStockNFT {
    function mintStockNFT(
        address to,
        string memory stockSymbol,
        uint256 quantity,
        uint256 currentPrice
    ) external returns (uint256);
}

contract StockAPIConsumer is ChainlinkClient, ConfirmedOwner {
    using Chainlink for Chainlink.Request;

    // variable bytes(arbitrary-length raw byte data) returned in a single oracle response
    bytes private response_data;
    string public stock_target_price;
    string public symbol_str;

    bytes32 private jobId;
    uint256 private fee;

    AggregatorV3Interface internal ethUsdPriceFeed;
    uint256 public stockPriceInEth;
    mapping(string => uint256) public sym_eth_price_map;

    address public stockNFT_address;
    mapping(string => uint256) public address_nft_id_map;

    event StockBought(address buyer, string symbol, uint256 amount, uint256 eth_price);

    /**
     * @notice Initialize the link token and target oracle
     * @dev The oracle address must be an Operator contract for multiword response
     *
     *
     * Sepolia Testnet details:
     * Link Token: 0x779877A7B0D9E8603169DdbD7836e478b4624789
     * Oracle: 0x6090149792dAAeE9D1D568c9f9a6F6B46AA29eFD (Ethereum Sepolia Testnet)
     *  https://docs.chain.link/any-api/testnet-oracles/#operator-contracts
     * jobId: 7da2702f37fd48e5b1b9a5715e3509b6
     *
     */
    constructor(address _stockNFTContractAddress) ConfirmedOwner(msg.sender) {
        _setChainlinkToken(0x779877A7B0D9E8603169DdbD7836e478b4624789);
        _setChainlinkOracle(0x6090149792dAAeE9D1D568c9f9a6F6B46AA29eFD);
        jobId = "7da2702f37fd48e5b1b9a5715e3509b6";
        fee = (1 * LINK_DIVISIBILITY) / 10; // 0,1 * 10**18 (Varies by network and job)

        ethUsdPriceFeed = AggregatorV3Interface(0x694AA1769357215DE4FAC081bf1f309aDC325306);
        stockNFT_address = _stockNFTContractAddress;
    }

    /**
     * @notice Buy stock and mint token from StockNFT ERC721 contract
     */
    function buyStock(string memory symbol, uint256 amount) payable public {
        // assert(amount > 0, "Amount should be greater than 0");
        assert(amount > 0);
        // Buy stock
        // Logic to get price from the map
        uint256 eth_price = sym_eth_price_map[symbol];
        uint256 total_price = eth_price * amount;

        // Ask the user to send the total_price amount of ETH to the contract


        // Check if the user has sent the total_price amount of ETH to the contract
        require(msg.value == total_price, "Insufficient ETH sent");
        // Execute the transaction to buy the stock
        // Transfer ETH amount to the contract

        address userAddress = msg.sender;

        // Redundant code, as the transfer happens already thanks to payable
        // address payable contract_address = payable(address(this));
        // contract_address.transfer(total_price);

        // Mint NFT
        // uint256 tokenId = IStockNFT(stockNFT_address).mintStockNFT(userAddress, symbol, amount, eth_price);
        // address_nft_id_map[symbol] = tokenId;
        // Invoke the mintStockNFT function of the StockNFT contract
        try IStockNFT(stockNFT_address).mintStockNFT(userAddress, symbol, amount, eth_price) {
            emit StockBought(userAddress, symbol, amount, eth_price);
            // Mint successful
        } catch {
            // Mint failed
            revert("IStockNFT Mint failed");
        }
        // uint256 tokenId = IStockNFT(stockNFT_address).mintStockNFT(userAddress, symbol, amount, eth_price);
    }

    /**
     * @notice Request Stock target price from the oracle
     */
    function requestStockTargetPriceBySymbol(string memory symbol) public {
        Chainlink.Request memory req = _buildChainlinkRequest(
            jobId,
            address(this),
            this.fulfillBytes.selector
        );

        symbol_str = string(symbol);
        bytes memory base_uri = bytes("https://www.alphavantage.co/query?function=OVERVIEW&symbol=");
        bytes memory base_uri_symbol = bytes(string(symbol_str));
        bytes memory api_key = bytes("&apikey=KIOUQ166A2SBNOA6");
        bytes memory url = bytes.concat(base_uri, base_uri_symbol, api_key);

        req._add("get", string(url));

        req._add("path", "AnalystTargetPrice");
        _sendChainlinkRequest(req, fee);
    }

    event RequestFulfilled(bytes32 indexed requestId, bytes indexed data);
    event PriceConverted(uint256 priceInUsd, uint256 priceInEth);

    /**
     * @notice Fulfillment function for variable bytes
     * @dev This is called by the oracle. recordChainlinkFulfillment must be used.
     */
    function fulfillBytes(
        bytes32 requestId,
        bytes memory bytesData
    ) public recordChainlinkFulfillment(requestId) {
        emit RequestFulfilled(requestId, bytesData);
        response_data = bytesData;
        stock_target_price = string(response_data);

        convertUsdToEth();
    }

    /**
     * @notice Converts the stock target price from USD to ETH
     * @dev Uses Chainlink Price Feed to get the latest ETH/USD price
     */
    function convertUsdToEth() internal {
        // Parse the USD string to uint
        uint256 priceInUsd = parseUsdString(stock_target_price);
        
        // Get the latest ETH/USD price from Chainlink
        (
            /* uint80 roundID */,
            int256 ethUsdPrice,
            /*uint startedAt*/,
            /*uint timeStamp*/,
            /*uint80 answeredInRound*/
        ) = ethUsdPriceFeed.latestRoundData();
        
        require(ethUsdPrice > 0, "Invalid ETH/USD price");
        
        // Convert USD to ETH (with 18 decimals)
        // price from chainlink comes with 8 decimals, so it is divided by 1e8 to get ETH.
        stockPriceInEth = (priceInUsd * 1e8) / uint256(ethUsdPrice);
        // sym_eth_price_map[symbol_str] = getStockPriceInEth();
        sym_eth_price_map[symbol_str] = 0;
        
        emit PriceConverted(priceInUsd, stockPriceInEth);
    }
/**
     * @notice Parses a USD string price to uint256
     * @param _priceString The USD price string (e.g., "150.75")
     * @return uint256 The price in wei (multiplied by 1e18)
     */
    function parseUsdString(string memory _priceString) internal pure returns (uint256) {
        bytes memory priceBytes = bytes(_priceString);
        uint256 decimal = 0;
        uint256 result = 0;
        bool decimalPoint = false;
        
        for (uint i = 0; i < priceBytes.length; i++) {
            if (priceBytes[i] == ".") {
                decimalPoint = true;
                continue;
            }
            
            if (priceBytes[i] >= "0" && priceBytes[i] <= "9") {
                result = result * 10 + (uint8(priceBytes[i]) - 48);
                if (decimalPoint) {
                    decimal++;
                }
            }
        }
        
        // Adjust to 18 decimals
        while (decimal < 18) {
            result *= 10;
            decimal++;
        }
        
        return result;
    }

    /**
     * @notice Returns the stock price components for better decimal handling
     * @return whole The whole number part of ETH
     * @return decimal The decimal part with 18 digits precision
     */
    function getStockPriceComponents() public view returns (uint256 whole, uint256 decimal) {
        whole = stockPriceInEth / 1e18;
        decimal = stockPriceInEth % 1e18;
        return (whole, decimal);
    }

    /**
     * @notice Returns the stock price formatted as a string with 4 decimal places
     * @return string The ETH price as a string (e.g., "0.5431")
     */
    function getStockPriceString() public view returns (string memory) {
        uint256 whole = stockPriceInEth / 1e18;
        uint256 decimal = stockPriceInEth % 1e18;
        
        // Convert to 4 decimal places
        decimal = decimal / 1e14;  // Reduce from 18 to 4 decimal places
        
        // Convert to string
        string memory wholeStr = uint2str(whole);
        string memory decimalStr = uint2str(decimal);
        
        // Pad decimals with leading zeros if needed
        while (bytes(decimalStr).length < 4) {
            decimalStr = string(bytes.concat(bytes("0"), bytes(decimalStr)));
        }
        
        return string(bytes.concat(bytes(wholeStr), bytes("."), bytes(decimalStr)));
    }

    /**
     * @notice Helper function to convert uint to string
     */
    function uint2str(uint256 _i) internal pure returns (string memory str) {
        if (_i == 0) {
            return "0";
        }
        uint256 j = _i;
        uint256 length;
        while (j != 0) {
            length++;
            j /= 10;
        }
        bytes memory bstr = new bytes(length);
        uint256 k = length;
        j = _i;
        while (j != 0) {
            bstr[--k] = bytes1(uint8(48 + j % 10));
            j /= 10;
        }
        str = string(bstr);
    }

    /**
     * @notice Returns the latest stock price in ETH
     * @return uint256 The stock price in ETH (18 decimals)
     */
    function getStockPriceInEth() public view returns (uint256) {
        return stockPriceInEth / 1e18;
    }


    /**
     * Allow withdraw of Link tokens from the contract
     */
    function withdrawLink() public onlyOwner {
        LinkTokenInterface link = LinkTokenInterface(_chainlinkTokenAddress());
        require(
            link.transfer(msg.sender, link.balanceOf(address(this))),
            "Unable to transfer"
        );
    }

    /**
     * Allow withdraw of Link tokens from the contract
     */
    function withdrawEth() public onlyOwner {
        payable(msg.sender).transfer(address(this).balance);
    }
}
