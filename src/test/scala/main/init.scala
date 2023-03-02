package main

import AVL.IssuerBox.{IssuerHelpersAVL, IssuerValue}
import AVL.NFT.{IndexKey, IssuanceAVLHelpers, IssuanceValueAVL}
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

object AVLHelperTest extends App {}

object init extends App {
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

  val issuanceTree = new IssuanceAVLHelpers
  val issuerTree = new IssuerHelpersAVL

  def prepareAVL(
      metadataFromJson: Array[Data],
      issuerTree: IssuerHelpersAVL,
      issuanceTree: IssuanceAVLHelpers
  ): Unit = {
    //create AVL trees
    var index = 0
    for (res: Data <- metadataFromJson) {
      val attributesMap = mutable.Map(
        res.attributes.map(a => a.trait_type -> a.value): _*
      )

      println(attributesMap)

      val levelsMap = mutable.Map(
        res.levels.map(a => a.trait_type -> (a.value, a.max_value)): _*
      )
      val statsMap = mutable.Map(
        res.stats.map(a => a.trait_type -> (a.value, a.max_value)): _*
      )

      val issuanceDataToInsert = IssuanceValueAVL.createMetadata(
        res.name,
        res.description,
        "picture",
        res.imageSHA256,
        res.image
      )

      val issuerDataToInsert = IssuerValue.createMetadata(
        encoder
          .encodeMetaData(
            attributesMap,
            levelsMap,
            statsMap
          )
          .getValue
      )

      val key = new IndexKey(index.toLong)

      issuanceTree.insertMetaData(key, issuanceDataToInsert)
      issuerTree.insertMetaData(key, issuerDataToInsert)

      index += 1
    }
  }

  prepareAVL(
    masterMeta.read("metadata.json"),
    issuerTree,
    issuanceTree
  )

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
    liliumFeeNanoErg = serviceConf.liliumFeeNanoErg,
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
