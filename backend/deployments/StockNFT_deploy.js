async function main() {
    const StockNFT = await ethers.getContractFactory("StockNFT");
    const contract = await StockNFT.deploy("0x12E6Edf57ccb3e6fd55bc6d5A29C7Ebe87B0D5e7");
    console.log("StockNFT Contract Deployed to Address:", contract.address);
}
main()
  .then(() => process.exit(0))
  .catch(error => {
    console.error(error);
    process.exit(1);
  });