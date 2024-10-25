// SPDX-License-Identifier: MIT
pragma solidity ^0.8.7;

import {Chainlink, ChainlinkClient} from "@chainlink/contracts/src/v0.8/ChainlinkClient.sol";
import {ConfirmedOwner} from "@chainlink/contracts/src/v0.8/shared/access/ConfirmedOwner.sol";
import {LinkTokenInterface} from "@chainlink/contracts/src/v0.8/shared/interfaces/LinkTokenInterface.sol";
import "@chainlink/contracts/src/v0.8/shared/interfaces/AggregatorV3Interface.sol";

/**
 * Request testnet LINK and ETH here: https://faucets.chain.link/
 * Find information on LINK Token Contracts and get the latest ETH and LINK faucets here: https://docs.chain.link/docs/link-token-contracts/
 */

/**
 * THIS IS AN EXAMPLE CONTRACT WHICH USES HARDCODED VALUES FOR CLARITY.
 * THIS EXAMPLE USES UN-AUDITED CODE.
 * DO NOT USE THIS CODE IN PRODUCTION.
 * This contract is deployed on 0x837b764C97F542bC881ac010C5984b187704d3E4
 */

contract APIConsumer is ChainlinkClient, ConfirmedOwner {
    using Chainlink for Chainlink.Request;

    uint256 public stock_price;
    string public symbol_str;
    bytes32 private jobId;
    uint256 private fee;
    uint256 public requiredETHInWei;
    AggregatorV3Interface internal ethUsdPriceFeed;
    mapping(string => uint256) public assetPricesInUSD;

    event RequestVolume(bytes32 indexed requestId, uint256 volume);

    /**
     * @notice Initialize the link token and target oracle
     *
     * Sepolia Testnet details:
     * Link Token: 0x779877A7B0D9E8603169DdbD7836e478b4624789
     * Oracle: 0x6090149792dAAeE9D1D568c9f9a6F6B46AA29eFD (Chainlink DevRel)
     * jobId: ca98366cc7314957b8c012c72f05aeeb
     *
     */
    constructor() ConfirmedOwner(msg.sender) {
        _setChainlinkToken(0x779877A7B0D9E8603169DdbD7836e478b4624789);
        _setChainlinkOracle(0x6090149792dAAeE9D1D568c9f9a6F6B46AA29eFD);
        jobId = "ca98366cc7314957b8c012c72f05aeeb";
        ethUsdPriceFeed = AggregatorV3Interface(0x694AA1769357215DE4FAC081bf1f309aDC325306);
        fee = (1 * LINK_DIVISIBILITY) / 10; // 0,1 * 10**18 (Varies by network and job)
    }

    /**
     * Create a Chainlink request to retrieve API response, find the target
     * data, then multiply by 1000 to remove decimals
     */
    function requestStockPrice(string memory stockSymbol) public returns (bytes32 requestId) {
        Chainlink.Request memory req = _buildChainlinkRequest(
            jobId,
            address(this),
            this.fulfill.selector
        );

        symbol_str = stockSymbol;

        // Build the URL with the stock symbol passed as parameter
        stock_price = 0;
        string memory baseURL = "https://api.polygon.io/v2/aggs/ticker/";
        string memory dateRange = "/range/1/day/2024-09-09/2024-09-09?apiKey=<PUT_YOUR_OWN>";
        string memory fullURL = string(abi.encodePacked(baseURL, stockSymbol, dateRange));

        // Set the URL to perform the GET request on
        req._add("get", fullURL);

        req._add("path", "results,0,o");

        // Multiply the result by 1000
        int256 timesAmount = 10 ** 3;
        req._addInt("times", timesAmount);

        // Sends the request
        return _sendChainlinkRequest(req, fee);
    }


    /**
     * Receive the response in the form of uint256
     */
    function fulfill(
        bytes32 _requestId,
        uint256 _volume
    ) public recordChainlinkFulfillment(_requestId) {
        emit RequestVolume(_requestId, _volume);
        (
            /* uint80 roundID */,
            int256 ethUsdPrice,
            /*uint startedAt*/,
            /*uint timeStamp*/,
            /*uint80 answeredInRound*/
        ) = ethUsdPriceFeed.latestRoundData();

        require(ethUsdPrice > 0, "Invalid ETH price");

        // Calculate required ETH based on USD amount required (converted to wei)
        requiredETHInWei = (_volume * 1e23) / uint256(ethUsdPrice);
        assetPricesInUSD[symbol_str] = requiredETHInWei;
        stock_price = _volume;
    }

    event StockBought(address buyer, string symbol, uint256 amount, uint256 eth_price);

    function buyStock(string memory stockSymbol) public payable {
        // Ensure that the sent ETH matches the required amount
        require(msg.value >= assetPricesInUSD[stockSymbol], "Insufficient ETH sent");
        stock_price = 0;
        symbol_str = "";
        requiredETHInWei = 0;
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
}
