package main

import AVL.IssuerBox.IssuerHelpersAVL
import AVL.NFT.IssuanceAVLHelpers
import configs.{collectionParser, serviceOwnerConf}
import contracts.LiliumContracts
import initilization.createCollection

import mint.{Client, DefaultNodeInfo}
import org.ergoplatform.ErgoBox.{R4, R7, R9}
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.appkit.{Address, ErgoContract, ErgoToken, ErgoValue}
import special.collection.Coll
import special.sigma.SigmaProp
import testMint.akkaFunctions
import utils.{BoxAPI, ContractCompile, MetadataTranscoder, explorerApi}

import scala.collection.JavaConverters._
import java.util.{Map => JMap}
import scala.collection.mutable

object contractPrintTest extends App {

  val stateContract = LiliumContracts.CollectionIssuance.contractScript
  println(stateContract)

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

//object runMint extends App {
//  val akka = new akkaFunctions
//  akka.main()
//}

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

object BoxAPITest extends App {
  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext

  println(DefaultNodeInfo(ctx.getNetworkType).nodeUrl)

  val api = new BoxAPI(
    DefaultNodeInfo(ctx.getNetworkType).explorerUrl,
    DefaultNodeInfo(ctx.getNetworkType).nodeUrl
  )

  val res = api
    .getUnspentBoxesFromNode(
      "38qJGm1BqDYQND1YjLchusTF5cD33SY6uCCqCJ4G4AApjr6uiufak2fACMttcyNVn4viX98Wy1JY78StQYGtBj4C9nPamUr7G37guo2HhivjHKTgjMHdL9fJukd5S19Rr2vuQiCJQGPrDdV1LGGxArQayS8Q8fMqThXK6xAYe984FYmADWzcNv1epYkpW3"
    )
    .head

  val apiTokenColl = api
    .convertJsonBoxToErgoBox(res)
    .getRegisters
    .get(1)
    .getValue
    .asInstanceOf[Coll[Byte]]

  val tokenIDFromRegister = api
    .convertJsonBoxToErgoBox(res)
    .getRegisters
    .get(1)
    .getValue
    .asInstanceOf[Coll[Byte]]
    .toArray
  val tokenID = new ErgoToken(tokenIDFromRegister, 1).getId.toString

  val tokenHex = res.additionalRegisters.R5

  println(
    apiTokenColl
  )

  println(
    ErgoValue
      .of(new ErgoToken(tokenIDFromRegister, 1).getId.getBytes)
      .getValue == apiTokenColl
  )

  println(
    ErgoValue
      .of(new ErgoToken(tokenIDFromRegister, 1).getId.getBytes)
      .toHex == tokenHex
  )

  println(
    api
      .convertJsonBoxToErgoBox(res)
      .getRegisters
      .get(1)
      .toHex
  )
}

object boxTest extends App {
  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext

  private val exp = new explorerApi(
    DefaultNodeInfo(ctx.getNetworkType).explorerUrl
  )
  val res = exp.getUnspentBoxesByAddress(
    "mPdcmWTSJ6EJtnWk8LpK4ZXa7koomoiXgzZHGw8twRQ3U5W2npaixKAq6Fz5V5gfEhSXUBJ6YWMAu7pZ"
  )

  println(res.toArray.head)

}

object stateBoxConstantsTest extends App {
  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext

  private val serviceFilePath = "serviceOwner.json"
  private val serviceConf = serviceOwnerConf.read(serviceFilePath)

  private val exp = new explorerApi(
    DefaultNodeInfo(ctx.getNetworkType).explorerUrl
  )

  val compiler = new ContractCompile(ctx)

  val proxyContract: ErgoContract =
    compiler.compileProxyContract(
      LiliumContracts.ProxyContract.contractScript,
      serviceConf.minerFeeNanoErg
    )

  val issuerContract = compiler.compileIssuerContract(
    LiliumContracts.IssuerContract.contractScript
  )

  val metadataTranscoder = new MetadataTranscoder
  val encoder = new metadataTranscoder.Encoder
  val decoder = new metadataTranscoder.Decoder

  val royaltyMap: mutable.LinkedHashMap[Address, Int] = mutable.LinkedHashMap()

  private val collectionJsonFilePath = "collection.json"
  private val collectionFromJson = collectionParser.read(collectionJsonFilePath)

  collectionFromJson.royalty.asScala.foreach { case (key, value: Double) =>
    royaltyMap += (Address.create(key) -> value.round.toInt)
  }

  val encodedRoyalty =
    encoder.encodeRoyalty(royaltyMap)

  val hashedRoyalty = decoder.hashRoyalty(encodedRoyalty.toHex)

  val artistAddress =
    Address.create("3WwHSFPhz6867vwYmuGQ4m634DQvT5u2Hpp2jit94EVLkacLEn44")
  val collectionToken = new ErgoToken(
    "9b62881b696a83adc60735cbd68556bddc7b7a2387f568c79710fe98a8aadb52",
    500
  )
  val singletonToken = new ErgoToken(
    "0b3d74a45f11cdda86e3e3533295d66a10bb47c1a824e26e4fed1285365d41fa",
    1
  )
  val priceOfNFTNanoErg = collectionFromJson.priceOfNFTNanoErg
  val liliumFeeAddress = Address.create(serviceConf.liliumFeeAddress)
  val liliumFeePercent = serviceConf.liliumFeePercent
  val minTxOperatorFeeNanoErg = serviceConf.minTxOperatorFeeNanoErg
  val minerFee = serviceConf.minerFeeNanoErg

  val stateContract = compiler.compileStateContract(
    LiliumContracts.StateContract.contractScript,
    proxyContract,
    issuerContract,
    artistAddress,
    hashedRoyalty,
    collectionToken,
    singletonToken,
    priceOfNFTNanoErg,
    liliumFeeAddress,
    liliumFeePercent,
    minTxOperatorFeeNanoErg,
    minerFee
  )

  println(stateContract.toAddress.toString)

  val minerFeeDecoded = stateContract.getErgoTree
    .constants(38)
    .value
    .asInstanceOf[Long]

  val mockCollectionToken: ErgoToken =
    new ErgoToken( // called mock since value is not accurate, we just want the token methods
      stateContract.getErgoTree
        .constants(1)
        .value
        .asInstanceOf[Coll[Byte]]
        .toArray,
      1
    )

  val artistAddressDecoded: Address = new org.ergoplatform.appkit.SigmaProp(
    stateContract.getErgoTree
      .constants(5)
      .value
      .asInstanceOf[special.sigma.SigmaProp]
  ).toAddress(ctx.getNetworkType)

  val liliumFeeDecoded = stateContract.getErgoTree
    .constants(30)
    .value
    .asInstanceOf[Long]

  val liliumFeeAddressDecoded = new org.ergoplatform.appkit.SigmaProp(
    stateContract.getErgoTree
      .constants(31)
      .value
      .asInstanceOf[special.sigma.SigmaProp]
  )
    .toAddress(this.ctx.getNetworkType)

  val liliumFeeValue =
    stateContract.getErgoTree.constants(30).value.asInstanceOf[Long]

  val priceOfNFTNanoErgDecoded =
    stateContract.getErgoTree.constants(29).value.asInstanceOf[Long]

  println(s"Miner Fee: $minerFee, Decoded: $minerFeeDecoded")
  println(
    s"Collection Token: ${collectionToken.getId.toString}, Decoded: ${mockCollectionToken.getId.toString}"
  )
  println(
    s"Artist Address: ${artistAddress.toString}, Decoded: ${artistAddressDecoded.toString}"
  )

  println(
    s"NFT Cost: ${collectionFromJson.priceOfNFTNanoErg}, Decoded: $priceOfNFTNanoErgDecoded"
  )

  println(
    s"Lilium Fee Numerator: ${serviceConf.liliumFeePercent}, Decoded: ${(liliumFeeValue.toDouble / collectionFromJson.priceOfNFTNanoErg.toDouble) * 100}"
  )
  println(
    s"Lilium Fee Address: ${liliumFeeAddress.toString}, Decoded: ${liliumFeeAddressDecoded.toString}"
  )

//  stateContract.getErgoTree.constants.foreach(o => println(o.value))

}
