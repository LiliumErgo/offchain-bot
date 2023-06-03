package constants

import configs.{Royalty, ServiceOwnerConfig, collectionParser, serviceOwnerConf}
import contracts.LiliumContracts
import main.stateBoxConstantsTest.{client, ctx, mockCollectionToken}
import mint.Client
import org.ergoplatform.appkit.{Address, ErgoContract, ErgoToken, ErgoValue}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import sigmastate.SType
import special.collection.Coll
import utils.{ContractCompile, MetadataTranscoder}

import java.lang
import scala.collection.mutable

class ConstantsSpec extends AnyFlatSpec with should.Matchers {

  val liliumTxOperatorMnemonic: String = ""
  val liliumTxOperatorMnemonicPw: String = ""
  val txOperatorMnemonic: String = ""
  val txOperatorMnemonicPw: String = ""
  val liliumFeeAddressString: String =
    "3WxR2UxZihv7NTYkUj5U6Pzg1K3UTAip6oDCwDcn1AjDws49WDYg"
  val liliumFeePercent: Long = 5
  val minTxOperatorFeeNanoErg: Long = 1000000
  val minerFeeNanoErg: Long = 1600000
  val nodeUrl: String = "http://168.138.185.215:9052"
  val apiUrl: String = "https://tn-ergo-explorer.anetabtc.io"
  val dataBaseKey: String = ""
  val dataBaseURL: String = ""

  private val royaltyAddress1 =
    "3Wwc8kHEHY6cPbumS5bYHciLjEFVmGEzZQsDrexFsB7Fq7kJeLaP"
  private val royaltyAmount1: Double = 4
  private val royaltyAddress2 =
    "3WyKx3risvvj9HCdsD69dNdXWwrCUuf1hp4PHBS7Y9WrsaZR7M9H"
  private val royaltyAmount2: Double = 2

  val artistAddress: Address =
    Address.create("3WwHSFPhz6867vwYmuGQ4m634DQvT5u2Hpp2jit94EVLkacLEn44")
  val collectionToken = new ErgoToken(
    "a633ef9cfdf70ef216e2ca089c6ddc64f0afb65dcb61245f4f0dc2f47ec344b7",
    500
  )
  val singletonToken = new ErgoToken(
    "793f4232b839c47e806c3cc9657a4222f24efe3a4320ea6be1dee3dfcaf29349",
    1
  )

  val artistAddressConstant = 3
  val minerFeeConstant = 38
  val collectionTokenConstant = 1
  val liliumFeeAddressConstant = 36
  val liliumFeeValueConstant = 35
  val priceOfNFTNanoErgConstant = 34

  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext

  private val serviceConf = ServiceOwnerConfig(
    liliumTxOperatorMnemonic,
    liliumTxOperatorMnemonicPw,
    txOperatorMnemonic,
    txOperatorMnemonicPw,
    liliumFeeAddressString,
    liliumFeePercent,
    minTxOperatorFeeNanoErg,
    minerFeeNanoErg,
    nodeUrl,
    apiUrl,
    dataBaseKey,
    dataBaseURL
  )

  val compiler = new ContractCompile(ctx)

  val proxyContract: ErgoContract =
    compiler.compileProxyContract(
      LiliumContracts.ProxyContract.contractScript,
      serviceConf.minerFeeNanoErg
    )

  val issuerContract: ErgoContract = compiler.compileIssuerContract(
    LiliumContracts.IssuerContract.contractScript
  )

  val metadataTranscoder = new MetadataTranscoder
  val encoder = new metadataTranscoder.Encoder
  val decoder = new metadataTranscoder.Decoder

  val royaltyMap: mutable.LinkedHashMap[Address, Int] = mutable.LinkedHashMap()

  val royalty: Array[Royalty] = Array(
    Royalty(royaltyAddress1, royaltyAmount1),
    Royalty(royaltyAddress2, royaltyAmount2)
  )

  royalty.foreach { item =>
    royaltyMap += (Address.create(item.address) -> item.amount.round.toInt)
  }

  val encodedRoyalty: ErgoValue[Coll[(Coll[lang.Byte], Integer)]] =
    encoder.encodeRoyalty(royaltyMap)

  val hashedRoyalty: Array[Byte] = decoder.hashRoyalty(encodedRoyalty.toHex)

  val priceOfNFTNanoErg: Long = 5000000
  val liliumFeeAddress: Address = Address.create(serviceConf.liliumFeeAddress)

  val stateContract: ErgoContract = compiler.compileStateContract(
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
    minerFeeNanoErg
  )

  "Miner Fee constant" should "match" in {

    val minerFeeDecoded: SType#WrappedType = stateContract.getErgoTree
      .constants(minerFeeConstant)
      .value

    minerFeeNanoErg should be(minerFeeDecoded)
  }

  "Collection Token constant" should "match" in {

    val mockCollectionToken: ErgoToken =
      new ErgoToken( // called mock since value is not accurate, we just want the token methods
        stateContract.getErgoTree
          .constants(collectionTokenConstant)
          .value
          .asInstanceOf[Coll[Byte]]
          .toArray,
        1
      )

    collectionToken.getId.toString should be(mockCollectionToken.getId.toString)
  }

  "Artist Address constant" should "match" in {

    val artistAddressDecoded: Address = new org.ergoplatform.appkit.SigmaProp(
      stateContract.getErgoTree
        .constants(artistAddressConstant)
        .value
        .asInstanceOf[special.sigma.SigmaProp]
    ).toAddress(ctx.getNetworkType)

    artistAddress.toString should be(artistAddressDecoded.toString)
  }

  "NFT Price constant" should "match" in {

    val priceOfNFTNanoErgDecoded: Long =
      stateContract.getErgoTree
        .constants(priceOfNFTNanoErgConstant)
        .value
        .asInstanceOf[Long]

    priceOfNFTNanoErg should be(priceOfNFTNanoErgDecoded)
  }

  "Lilium Fee Numerator" should "match" in {

    val liliumFeeValue: Long =
      stateContract.getErgoTree
        .constants(liliumFeeValueConstant)
        .value
        .asInstanceOf[Long]

    liliumFeePercent should be(
      (liliumFeeValue.toDouble / priceOfNFTNanoErg.toDouble) * 100
    )
  }

  "Lilium Fee Address" should "match" in {

    val liliumFeeAddressDecoded: Address =
      new org.ergoplatform.appkit.SigmaProp(
        stateContract.getErgoTree
          .constants(liliumFeeAddressConstant)
          .value
          .asInstanceOf[special.sigma.SigmaProp]
      )
        .toAddress(this.ctx.getNetworkType)

    liliumFeeAddress.toString should be(liliumFeeAddressDecoded.toString)
  }

}
