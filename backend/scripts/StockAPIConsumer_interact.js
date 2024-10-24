const API_URL = process.env.API_URL;
const PRIVATE_KEY = process.env.PRIVATE_KEY;
const CONTRACT_ADDRESS = process.env.CONTRACT_ADDRESS;

const contract = require("../artifacts/contracts/StockAPIConsumer.sol/StockAPIConsumer.json");
// console.log(JSON.stringify(contract.abi));

const ethers = require('ethers');

// Provider
const alchemyProvider = new ethers.providers.JsonRpcProvider(API_URL);

// Signer
const signer = new ethers.Wallet(PRIVATE_KEY, alchemyProvider);

// Contract
const contractInstance = new ethers.Contract(CONTRACT_ADDRESS, contract.abi, signer);

async function main() {
  await contractInstance.requestStockTargetPriceBySymbol("IBM");
  const price = await contractInstance.stock_target_price();
  console.log("The stock_target_price is: " + price);
  const stockPriceInEth = await contractInstance.stockPriceInEth();
  console.log("The stockPriceInEth in ETH is: " + stockPriceInEth);
  const responseGetStockPriceInEth = await contractInstance.getStockPriceInEth();
  console.log("The getStockPriceInEth in ETH is: " + responseGetStockPriceInEth);
}
main();


