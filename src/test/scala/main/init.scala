package main

import AVL.IssuerBox.{IssuerHelpersAVL, IssuerValue}
import AVL.NFT.{IndexKey, IssuanceAVLHelpers, IssuanceValueAVL}
import AVL.utils.avlUtils.prepareAVL
import configs.{Data, collectionParser, masterMeta, serviceOwnerConf}
import contracts.LiliumContracts
import initilization.createCollection
import mint.{Client, DefaultNodeInfo}
import org.ergoplatform.appkit.Address
import testMint.akkaFunctions
import utils.{MetadataTranscoder, explorerApi}

import scala.collection.mutable
import scala.collection.JavaConverters._
import java.util.{Map => JMap}

object AVLHelperTest extends App {

  val issuanceTree = new IssuanceAVLHelpers
  val issuerTree = new IssuerHelpersAVL
  private val metadataTranscoder = new MetadataTranscoder
  private val encoder = new metadataTranscoder.Encoder
  private val decoder = new metadataTranscoder.Decoder

  prepareAVL(
    masterMeta.read("metadata_complex.json"),
    issuerTree,
    issuanceTree
  )

  val r6 = 0

  val decodedMetadata = decoder.decodeMetadata(
    issuerTree.lookUp(IndexKey(r6)).response.head.get.metaData
  )

  println(
    "Attributes Map: " + decodedMetadata(0)
      .asInstanceOf[mutable.Map[String, String]]
  )
  println(decodedMetadata(1).asInstanceOf[mutable.Map[String, (Int, Int)]])
  println(decodedMetadata(2).asInstanceOf[mutable.Map[String, (Int, Int)]])
}

object init extends App {
  private def convertToMutableMap(
      jmap: JMap[String, String]
  ): mutable.LinkedHashMap[String, String] = {
    mutable.LinkedHashMap(jmap.asScala.toSeq: _*)
  }

  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext
  private val metadataTranscoder = new MetadataTranscoder
  private val encoder = new metadataTranscoder.Encoder
  private val decoder = new metadataTranscoder.Decoder
  private val serviceFilePath = "serviceOwner.json"
  private val serviceConf = serviceOwnerConf.read(serviceFilePath)

  val issuanceTree = new IssuanceAVLHelpers
  val issuerTree = new IssuerHelpersAVL

  prepareAVL(
    masterMeta.read("metadata.json"),
    issuerTree,
    issuanceTree
  )

  private val dummyArist =
    Address.create("3WwnvL9PBXHyR72UyUJqbqAxH1gFT7kbWmivgVXfhUV362i76qRY")
  private val collectionJsonFilePath = "collection.json"

  private val royaltyMap: mutable.LinkedHashMap[Address, Int] =
    mutable.LinkedHashMap()
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
    encodedRoyalty = encodedRoyalty.toHex,
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
    liliumFeePercent = serviceConf.liliumFeePercent,
    minTxOperatorFeeNanoErg = serviceConf.minTxOperatorFeeNanoErg,
    minerFee = serviceConf.minerFeeNanoErg
  )

//  val oneMin = 60000
//
//  Thread.sleep(oneMin * 5)
//
//  val akka = new akkaFunctions(issuanceTree, issuerTree)
//  akka.main()
}

//object runMintTest extends App {
//  val akka = new akkaFunctions()
//  akka.main()
//}
