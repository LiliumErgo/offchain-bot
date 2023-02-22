package main

import AVL.IssuerBox.IssuerHelpersAVL
import AVL.NFT.IssuanceAVLHelpers
import configs.{collectionParser, serviceOwnerConf}
import contracts.LiliumContracts
import initilization.createCollection
import main.collectionCreationTest.{
  collectionJsonFilePath,
  decoder,
  encoder,
  royaltyMap
}
import mint.{Client, DefaultNodeInfo, akkaFunctions}
import org.ergoplatform.ErgoBox.{R4, R7, R9}
import org.ergoplatform.appkit.{Address, ErgoToken, ErgoValue}
import special.collection.Coll
import special.sigma.SigmaProp
import utils.{ContractCompile, MetadataTranscoder, explorerApi}

import scala.collection.JavaConverters._
import java.util.{Map => JMap}
import scala.collection.mutable

object contractPrintTest extends App {

  val stateContract = LiliumContracts.CollectionIssuance.contractScript
  println(stateContract)

}

object collectionCreationTest extends App {

  private def convertToMutableMap(
      jmap: JMap[String, String]
  ): mutable.Map[String, String] = {
    mutable.Map(jmap.asScala.toSeq: _*)
  }

  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext
  private val metadataTranscoder = new MetadataTranscoder
  private val encoder = new metadataTranscoder.Encoder
  private val decoder = new metadataTranscoder.Decoder
  private val serviceFilePath = "serviceOwner.json"
  private val issuanceAVLPath = "avlData/issuanceMetaData"
  private val issuerAVLPath = "avlData/issuerMetaData"
  private val serviceConf = serviceOwnerConf.read(serviceFilePath)
  private val exp = new explorerApi(
    DefaultNodeInfo(ctx.getNetworkType).explorerUrl
  )

  val issuanceTree = new IssuanceAVLHelpers(issuanceAVLPath)
  val issuerTree = new IssuerHelpersAVL(issuerAVLPath)

  private val dummyArist =
    Address.create("3WwnvL9PBXHyR72UyUJqbqAxH1gFT7kbWmivgVXfhUV362i76qRY")
  private val collectionJsonFilePath = "collection.json"

  private val royaltyMap: mutable.Map[Address, Int] = mutable.Map()
  private val collectionFromJson = collectionParser.read(collectionJsonFilePath)

  collectionFromJson.royalty.asScala.foreach { case (key, value: Double) =>
    royaltyMap += (Address.create(key) -> value.round.toInt)
  }
  private val encodedRoyalty =
    encoder.encodeRoyalty(royaltyMap)
  private val hashedRoyalty = decoder.hashRoyalty(encodedRoyalty.toHex)

  new createCollection(
    ctx = ctx,
    liliumTxOperatorMnemonic = serviceConf.liliumTxOperatorMnemonic,
    liliumTxOperatorMnemonicPw = serviceConf.liliumTxOperatorMnemonicPw,
    stateContractString = LiliumContracts.StateContract.contractScript,
    issuerContractString = LiliumContracts.IssuerContract.contractScript,
    proxyContractString = LiliumContracts.ProxyContract.contractScript,
    collectionIssuerContractString =
      LiliumContracts.CollectionIssuer.contractScript,
    collectionIssuanceContractString =
      LiliumContracts.CollectionIssuance.contractScript,
    singletonIssuerContractString =
      LiliumContracts.SingletonIssuer.contractScript,
    singletonIssuanceContractString =
      LiliumContracts.SingletonIssuance.contractScript,
    artistAddress = dummyArist,
    royaltyBlakeHash = hashedRoyalty,
    collectionName = collectionFromJson.collectionInfo.collectionName,
    collectionDescription =
      collectionFromJson.collectionInfo.collectionDescription,
    collectionInfo = Array(
      collectionFromJson.collectionInfo.collectionLogoURL,
      collectionFromJson.collectionInfo.collectionFeaturedImageURL,
      collectionFromJson.collectionInfo.collectionBannerImageURL,
      collectionFromJson.collectionInfo.collectionCategory
    ),
    collectionTokensAmount = collectionFromJson.collectionMaxSize,
    startingTimestamp = collectionFromJson.saleStartTimestamp,
    expiryTimestamp = collectionFromJson.saleEndTimestamp,
    mintExpiryTimestamp = collectionFromJson.mintingExpiry,
    returnCollectionTokensToArtist =
      collectionFromJson.returnCollectionTokensToArtist,
    socialMediaMap = convertToMutableMap(collectionFromJson.socialMedia),
    issuanceMetaDataMap = issuanceTree.getMap,
    issuerMetaDataMap = issuerTree.getMap,
    priceOfNFTNanoErg = collectionFromJson.priceOfNFTNanoErg,
    liliumFeeAddress = Address.create(serviceConf.liliumFeeAddress),
    liliumFeeNanoErg = serviceConf.liliumFeeNanoErg,
    minTxOperatorFeeNanoErg = serviceConf.minTxOperatorFeeNanoErg,
    minerFee = serviceConf.minerFeeNanoErg
  )
}

object ApiTest extends App {
  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext

  private val exp = new explorerApi(
    DefaultNodeInfo(ctx.getNetworkType).explorerUrl
  )

  private val metadataTranscoder = new MetadataTranscoder
  private val encoder = new metadataTranscoder.Encoder
  private val decoder = new metadataTranscoder.Decoder
  private val collectionJsonFilePath = "collection.json"
  private val collectionFromJson = collectionParser.read(collectionJsonFilePath)
  val compilerObj = new ContractCompile(ctx)

  private val dummyArist =
    Address.create("3WwnvL9PBXHyR72UyUJqbqAxH1gFT7kbWmivgVXfhUV362i76qRY")

  val collectionToken =
    "0800c8861fa269fb508167e70d1275683a8e23c23ac67e20f5558a52057719bf"

//  val ergBox = exp.getErgoBoxfromIDNoApi(collectionToken)
//  println(ergBox.additionalRegisters(R9).value.asInstanceOf[Long])
  /////////////

  val stateBox =
    "aa8d7c3b79cec7edc72c257458502d3689b047f2f3893ac6ae3ae5eec46ca5f3"

  val issuerContract = compilerObj.compileIssuerContract(
    LiliumContracts.IssuerContract.contractScript
  )

  val stateBoxInput = exp.getUnspentBoxFromMempool(stateBox)

  val propBytes = stateBoxInput.toErgoValue.getValue.propositionBytes

  val contract =
    Address.fromPropositionBytes(ctx.getNetworkType, propBytes.toArray)

//  println(
//    contract.toErgoContract.getErgoTree
//      .constants(18)
//      .value
//      .asInstanceOf[Coll[Byte]]
//      .toArray
//      .mkString("Array(", ", ", ")")
//

//  println(
//    "Lilium Fee: " +
//      contract.toErgoContract.getErgoTree
//        .constants(30)
//        .value
//        .asInstanceOf[Long]
//  )

//  println(
//    "Miner Fee: " +
//      contract.toErgoContract.getErgoTree
//        .constants(38)
//        .value
//        .asInstanceOf[Long]
//  )

//  contract.toErgoContract.getErgoTree.constants.foreach(o => println(o.value))

//  println(
//    contract.toErgoContract.getErgoTree
//      .constants(14)
//      .value
//      .asInstanceOf[Coll[Byte]]
//      .toArray
//      .mkString("Array(", ", ", ")")
//  )
//  println(
//    "Issuer Contract Bytes: " + issuerContract.toAddress
//      .asP2S()
//      .scriptBytes
//      .mkString("Array(", ", ", ")")
//  )

//  println(
//    contract.toErgoContract.getErgoTree
//      .constants(1)
//      .value
//      .asInstanceOf[Coll[Byte]]
//      .toArray
//      .mkString("Array(", ", ", ")")
//  )
//
//  println(
//    "Collection Token Bytes: " + new ErgoToken(
//      collectionToken,
//      1
//    ).getId.getBytes.mkString("Array(", ", ", ")")
//  )

//  println(
//    contract.toErgoContract.getErgoTree
//      .constants(29)
//      .value
//      .asInstanceOf[Long]
//  )

//  println(
//    contract.toErgoContract.getErgoTree
//      .constants(1)
//      .value
//      .asInstanceOf[Coll[Byte]]
//      .toArray
//      .mkString("Array(", ", ", ")")
//  )

//  val dummyArtistReconstruction = new org.ergoplatform.appkit.SigmaProp(
//    contract.toErgoContract.getErgoTree
//      .constants(5)
//      .value
//      .asInstanceOf[special.sigma.SigmaProp]
//  )
//    .toAddress(this.ctx.getNetworkType)
//
//  println(dummyArtistReconstruction)
//
//  println("Dummy Artist Bytes: " + dummyArist.getPublicKey)

//  contract.toErgoContract.getErgoTree.constants.foreach(o => println(o.value))

//  private val royaltyMap: mutable.Map[Address, Int] = mutable.Map()
//
//  collectionFromJson.royalty.asScala.foreach { case (key, value: Double) =>
//    royaltyMap += (Address.create(key) -> value.round.toInt)
//  }
//
//  private val encodedRoyalty =
//    encoder.encodeRoyalty(royaltyMap)
//  private val hashedRoyalty = decoder.hashRoyalty(encodedRoyalty.toHex)
//  println(hashedRoyalty.mkString("Array(", ", ", ")"))

//  val liliumFeeReconstruction = new org.ergoplatform.appkit.SigmaProp(
//    contract.toErgoContract.getErgoTree
//      .constants(31)
//      .value
//      .asInstanceOf[special.sigma.SigmaProp]
//  )
//    .toAddress(this.ctx.getNetworkType)
//
//  println(liliumFeeReconstruction)

}

object runMint extends App {
  val akka = new akkaFunctions
  akka.main()
}

object ts extends App {
  println(System.currentTimeMillis() + (60000 * 8))
}

object proxyTest extends App {
  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext

  val compilerObj = new ContractCompile(ctx)

  println(
    compilerObj
      .compileProxyContract(
        LiliumContracts.ProxyContract.contractScript,
        1500000
      )
      .toAddress
      .toString
  )

}
