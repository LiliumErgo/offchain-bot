package constants

import configs.{collectionParser, serviceOwnerConf}
import contracts.LiliumContracts
import mint.Client
import org.ergoplatform.appkit.{Address, ErgoContract, ErgoToken, ErgoValue}
import special.collection.Coll
import utils.{ContractCompile, MetadataTranscoder, explorerApi}

import java.lang
import scala.collection.mutable

object ConstantFinder extends App {

  val client: Client = new Client()
  client.setClient
  val ctx = client.getContext
  val serviceConf = serviceOwnerConf.read("serviceOwner.json")
  val compiler = new ContractCompile(ctx)
  val proxyContract = compiler.compileProxyContract(
    LiliumContracts.ProxyContract.contractScript,
    serviceConf.minerFeeNanoErg
  )
  val minerFeeNanoErg = serviceConf.minerFeeNanoErg
  val issuerContract = compiler.compileIssuerContract(
    LiliumContracts.IssuerContract.contractScript,
    minerFeeNanoErg
  )

  private val collectionJsonFilePath = "collection.json"
  private val collectionFromJson = collectionParser.read(collectionJsonFilePath)

  val royaltyMap: mutable.LinkedHashMap[Address, Int] =
    collectionFromJson.royalty
      .map(item => Address.create(item.address) -> item.amount.round.toInt)(
        collection.breakOut
      )

  val metadataTranscoder = new MetadataTranscoder
  val encoder = new metadataTranscoder.Encoder
  val decodedRoyalty = encoder.encodeRoyalty(royaltyMap)
  val hashedRoyalty =
    (new metadataTranscoder.Decoder).hashRoyalty(decodedRoyalty.toHex)

  val artistAddress =
    Address.create("3WwHSFPhz6867vwYmuGQ4m634DQvT5u2Hpp2jit94EVLkacLEn44")
  val collectionToken = new ErgoToken(
    "a633ef9cfdf70ef216e2ca089c6ddc64f0afb65dcb61245f4f0dc2f47ec344b7",
    500
  )
  val singletonToken = new ErgoToken(
    "793f4232b839c47e806c3cc9657a4222f24efe3a4320ea6be1dee3dfcaf29349",
    1
  )
  val liliumFeeAddress = Address.create(serviceConf.liliumFeeAddress)
  val priceOfNFTNanoErg = collectionFromJson.priceOfNFTNanoErg
  val liliumFeePercent = serviceConf.liliumFeePercent
  val minBoxValue = 423944325
  val paymentTokenAmount = 34625
  val minTxOperatorFeeNanoErg = 4239084

  val stateContract = compiler.compileStateContract(
    LiliumContracts.StateContract.contractScript,
    proxyContract,
    issuerContract,
    artistAddress,
    hashedRoyalty,
    collectionToken,
    singletonToken,
    priceOfNFTNanoErg,
    paymentTokenAmount,
    liliumFeeAddress,
    liliumFeePercent,
    minTxOperatorFeeNanoErg,
    minerFeeNanoErg,
    minBoxValue
  )

  val potentialMinerFeeConstantList = mutable.ListBuffer[Int]()
  val potentialCollectionTokenConstantList = mutable.ListBuffer[Int]()
  val potentialPriceOfNFTConstantList = mutable.ListBuffer[Int]()
  val potentialPaymentTokenAmountList = mutable.ListBuffer[Int]()
  val potentialTXOperatorFeeList = mutable.ListBuffer[Int]()
  val potentialMinBoxValueList = mutable.ListBuffer[Int]()
  val potentialLiliumFeeNumConstantList = mutable.ListBuffer[Int]()
  var artistAddressConstant = -1
  var liliumFeeAddressConstant = -1

  stateContract.getErgoTree.constants.zipWithIndex.foreach { case (c, i) =>
    c.value match {
      case value: Long =>
        if (value == minerFeeNanoErg) {
          potentialMinerFeeConstantList += i
        } else if (value == priceOfNFTNanoErg) {
          potentialPriceOfNFTConstantList += i
        } else if (value == paymentTokenAmount) {
          potentialPaymentTokenAmountList += i
        } else if (value == minTxOperatorFeeNanoErg) {
          potentialTXOperatorFeeList += i
        } else if (value == minBoxValue) {
          potentialMinBoxValueList += i
        } else if (
          liliumFeePercent == (value.toDouble / priceOfNFTNanoErg.toDouble) * 100
        ) {
          potentialLiliumFeeNumConstantList += i
        }
      case value: Coll[Byte] =>
        val mockCollectionToken = new ErgoToken(value.toArray, 1)
        if (
          mockCollectionToken.getId.toString == collectionToken.getId.toString
        ) {
          potentialCollectionTokenConstantList += i
        }
      case value: special.sigma.SigmaProp =>
        val decodedAddress: Address =
          new org.ergoplatform.appkit.SigmaProp(value)
            .toAddress(ctx.getNetworkType)

        if (decodedAddress.toString == artistAddress.toString) {
          artistAddressConstant = i
        } else if (decodedAddress.toString == liliumFeeAddress.toString) {
          liliumFeeAddressConstant = i
        }

      case _ => // Do nothing
    }
  }

  println(
    "Potential Miner Fee Constant: " + potentialMinerFeeConstantList.mkString(
      ", "
    )
  )
  println(
    "Potential Collection Token Constant: " + potentialCollectionTokenConstantList
      .mkString(", ")
  )
  println(
    "Potential Price of NFT Constant: " + potentialPriceOfNFTConstantList
      .mkString(", ")
  )
  println(
    "Potential Payment Amount Constant: " + potentialPaymentTokenAmountList
      .mkString(", ")
  )
  println(
    "Potential TX Operator Fee Constant: " + potentialTXOperatorFeeList
      .mkString(", ")
  )
  println(
    "Potential Min Box Value Constant: " + potentialMinBoxValueList
      .mkString(", ")
  )
  println(
    "Potential Lilium Fee Numerator Constant: " + potentialLiliumFeeNumConstantList
      .mkString(", ")
  )
  println("Artist Address Constant: " + artistAddressConstant)
  println("Lilium Fee Address Constant: " + liliumFeeAddressConstant)
}
