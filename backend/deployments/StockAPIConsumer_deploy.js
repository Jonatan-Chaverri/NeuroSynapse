async function main() {
    const StockAPIConsumer = await ethers.getContractFactory("StockAPIConsumer");
    const contract = await StockAPIConsumer.deploy();
    console.log("Contract Deployed to Address:", contract.address);
}
main()
  .then(() => process.exit(0))
  .catch(error => {
    console.error(error);
    process.exit(1);
  });