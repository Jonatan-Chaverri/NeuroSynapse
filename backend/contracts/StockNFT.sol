// SPDX-License-Identifier: MIT
pragma solidity ^0.8.7;

import "@openzeppelin/contracts/token/ERC721/ERC721.sol";
import "@openzeppelin/contracts/access/Ownable.sol";
import "@chainlink/contracts/src/v0.8/shared/interfaces/AggregatorV3Interface.sol";

contract StockNFT is ERC721, Ownable {
    struct StockInfo {
        string stockSymbol;    
        uint256 purchasePrice; 
        uint256 quantity;      
        uint256 purchaseDate; 
    }

    uint256 private _nextTokenId;
    mapping(string => address) public priceFeeds;
    mapping(uint256 => StockInfo) public stockInfo;
    
    mapping(address => bool) public whitelistedMinters;
    
    event PriceFeedSet(string stockSymbol, address priceFeed);
    event MinterAdded(address minter);
    event MinterRemoved(address minter);
    
    modifier onlyWhitelisted() {
        require(whitelistedMinters[msg.sender], "Caller is not whitelisted");
        _;
    }
    
    constructor(address initialOwner) 
        ERC721("Stock NFT", "SNFT")
        // Ownable(initialOwner)
    {
        // Agregar al owner como minter por defecto
        whitelistedMinters[initialOwner] = true;
        emit MinterAdded(initialOwner);
    }

    // Función para agregar direcciones a la whitelist
    function addMinter(address minter) external onlyOwner {
        require(minter != address(0), "Invalid minter address");
        require(!whitelistedMinters[minter], "Minter already whitelisted");
        
        whitelistedMinters[minter] = true;
        emit MinterAdded(minter);
    }

    // Función para remover direcciones de la whitelist
    function removeMinter(address minter) external onlyOwner {
        require(whitelistedMinters[minter], "Minter not whitelisted");
        require(minter != owner(), "Cannot remove owner from whitelist");
        
        whitelistedMinters[minter] = false;
        emit MinterRemoved(minter);
    }

    function setPriceFeed(string memory symbol, address priceFeed) external onlyOwner {
        require(priceFeed != address(0), "Invalid price feed address");
        
        AggregatorV3Interface aggregator = AggregatorV3Interface(priceFeed);
        (, int256 price,,,) = aggregator.latestRoundData();
        require(price > 0, "Price feed not working");

        priceFeeds[symbol] = priceFeed;
        emit PriceFeedSet(symbol, priceFeed);
    }

    function getCurrentPrice(string memory stockSymbol) public view returns (
        uint256 price,
        uint8 decimals
    ) {
        address priceFeedAddress = priceFeeds[stockSymbol];
        require(priceFeedAddress != address(0), "Price feed not configured for this stock");

        AggregatorV3Interface priceFeed = AggregatorV3Interface(priceFeedAddress);
        
        (
            uint80 roundID,
            int256 price_,
            uint256 startedAt,
            uint256 timeStamp,
            uint80 answeredInRound
        ) = priceFeed.latestRoundData();

        decimals = priceFeed.decimals();
        
        require(price_ > 0, "Invalid price");
        require(timeStamp > 0, "Round not complete");
        require(answeredInRound >= roundID, "Stale price");

        return (uint256(price_), decimals);
    }

    // Modificada para usar onlyWhitelisted en lugar de onlyOwner
    function mintStockNFT(
        address to,
        string memory stockSymbol,
        uint256 quantity,
        uint256 currentPrice
    ) external onlyWhitelisted returns (uint256) {
        // (uint256 currentPrice, uint8 decimals) = getCurrentPrice(stockSymbol);
        
        uint256 newTokenId = _nextTokenId++;
        
        stockInfo[newTokenId] = StockInfo({
            stockSymbol: stockSymbol,
            purchasePrice: currentPrice,
            quantity: quantity,
            purchaseDate: block.timestamp
        });

        _safeMint(to, newTokenId);

        return newTokenId;
    }

    function getFormattedPrice(string memory stockSymbol) external view returns (uint256) {
        (uint256 price, uint8 decimals) = getCurrentPrice(stockSymbol);
        return price / (10 ** (decimals - 2));
    }
}
