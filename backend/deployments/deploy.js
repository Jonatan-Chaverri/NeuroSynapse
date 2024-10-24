async function main() {
    const StockNFT = await ethers.getContractFactory("StockNFT");
    const StockNFTContract = await StockNFT.deploy(process.env.WALLET_ADDRESS);
    console.log("StockNFT Contract Deployed to Address:", StockNFTContract.address);

    const StockAPIConsumer = await ethers.getContractFactory("StockAPIConsumer");
    const StockAPIConsumerContract = await StockAPIConsumer.deploy(StockNFTContract.address);
    console.log("StockAPIConsumer Contract Deployed to Address:", StockAPIConsumerContract.address);
}
main()
  .then(() => process.exit(0))
  .catch(error => {
    console.error(error);
    process.exit(1);
  });