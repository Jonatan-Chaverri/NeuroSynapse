const API_URL = process.env.API_URL;
const PRIVATE_KEY = process.env.PRIVATE_KEY;
const STOCK_API_CONSUMER_CONTRACT_ADDRESS = process.env.STOCK_API_CONSUMER_CONTRACT_ADDRESS;
const STOCK_NFT_CONTRACT_ADDRESS = process.env.STOCK_NFT_CONTRACT_ADDRESS;
const WALLET_ADDRESS = process.env.WALLET_ADDRESS;

const StockAPIConsumerContract = require("../artifacts/contracts/StockAPIConsumer.sol/StockAPIConsumer.json");
// console.log(JSON.stringify(StockAPIConsumerContract.abi));

const StockNFTContract = require("../artifacts/contracts/StockNFT.sol/StockNFT.json");
// console.log(JSON.stringify(StockAPIConsumerContract.abi));

const ethers = require('ethers');

// Provider
const alchemyProvider = new ethers.providers.JsonRpcProvider(API_URL);

// Signer
const signer = new ethers.Wallet(PRIVATE_KEY, alchemyProvider);

// Contract
const StockAPIConsumerContractInstance = new ethers.Contract(STOCK_API_CONSUMER_CONTRACT_ADDRESS, StockAPIConsumerContract.abi, signer);
const StockNFTContractInstance = new ethers.Contract(STOCK_NFT_CONTRACT_ADDRESS, StockNFTContract.abi, signer);

async function main() {
  // await StockNFTContractInstance.addMinter(STOCK_API_CONSUMER_CONTRACT_ADDRESS);
  // await StockNFTContractInstance.addMinter(WALLET_ADDRESS);
  
  const isWhitelistedMinter = await StockNFTContractInstance.whitelistedMinters(STOCK_API_CONSUMER_CONTRACT_ADDRESS);
  console.log(`Is ${STOCK_API_CONSUMER_CONTRACT_ADDRESS} whitelisted minter: ${isWhitelistedMinter}`);

  const stockSymbol = "AAPL";
  const amountToBuy = 1;

  // TODO: Send LINK to StockAPIConsumerContract so it can make API calls
  await StockAPIConsumerContractInstance.requestStockTargetPriceBySymbol(stockSymbol);
  const price = await StockAPIConsumerContractInstance.stock_target_price();
  console.log("The stock_target_price is: " + price);
  const stockPriceInEth = await StockAPIConsumerContractInstance.stockPriceInEth();
  console.log("The stockPriceInEth in ETH is: " + stockPriceInEth);
  const responseGetStockPriceInEth = await StockAPIConsumerContractInstance.getStockPriceInEth();
  console.log("The getStockPriceInEth in ETH is: " + responseGetStockPriceInEth);
  sym_eth_price_map = await StockAPIConsumerContractInstance.sym_eth_price_map(stockSymbol);
  console.log("The sym_eth_price_map: " + sym_eth_price_map);

  const gasLimit = 30000000;
  
  // await StockAPIConsumerContractInstance.buyStock(stockSymbol, amountToBuy, { gasLimit: gasLimit });
  const address_nft_id_map = await StockAPIConsumerContractInstance.address_nft_id_map(stockSymbol);
  console.log("The address_nft_id_map: " + address_nft_id_map);
}
main();


