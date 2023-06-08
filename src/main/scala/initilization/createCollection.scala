package initilization

import AVL.IssuerBox.IssuerValue
import AVL.NFT.{IndexKey, IssuanceValueAVL}
import AVL.utils.avlUtils
import AVL.utils.avlUtils.exportAVL
import com.google.gson.Gson
import configs.conf
import io.getblok.getblok_plasma.collections.{LocalPlasmaMap, PlasmaMap}
import mint.DefaultNodeInfo
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import org.ergoplatform.appkit.{
  Address,
  BlockchainContext,
  ContextVar,
  ErgoContract,
  ErgoToken,
  ErgoValue,
  InputBox,
  OutBox,
  Parameters,
  SignedTransaction
}
import sigmastate.eval.Colls

import scala.collection.JavaConverters._
import scala.collection.mutable
import utils.{
  ContractCompile,
  DatabaseAPI,
  MetadataTranscoder,
  OutBoxes,
  TransactionHelper,
  explorerApi
}

import java.util
import scala.collection.mutable.ListBuffer

case class Ergo(usd: Double)
case class CoinGekoFormat(
    ergo: Ergo
)

class createCollection(
    ctx: BlockchainContext,
    liliumTxOperatorMnemonic: String,
    liliumTxOperatorMnemonicPw: String,
    stateContractString: String,
    issuerContractString: String,
    proxyContractString: String,
    collectionIssuerContractString: String,
    collectionIssuanceContractString: String,
    singletonIssuerContractString: String,
    singletonIssuanceContractString: String,
    premintIssuerContractString: String,
    whitelistIssuerContractString: String,
    artistAddress: Address,
    encodedRoyalty: String,
    royaltyBlakeHash: Array[Byte],
    collectionName: String,
    collectionDescription: String,
    collectionInfo: Array[String],
    collectionTokensAmount: Long,
    startingTimestamp: Long,
    expiryTimestamp: Long,
    mintExpiryTimestamp: Long,
    returnCollectionTokensToArtist: Boolean,
    whitelistAccepted: Boolean,
    whitelistBypass: Boolean,
    premintAccepted: Boolean,
    whitelistTokenAmount: Long,
    premintTokenAmount: Long,
    socialMediaMap: mutable.LinkedHashMap[String, String],
    issuanceMetaDataMap: PlasmaMap[IndexKey, IssuanceValueAVL],
    issuerMetaDataMap: PlasmaMap[IndexKey, IssuerValue],
    priceOfNFTNanoErg: Long,
    liliumFeeAddress: Address,
    liliumFeePercent: Long,
    minTxOperatorFeeNanoErg: Long,
    minerFee: Long,
    minBoxValue: Long
) {

  def getERGUSD: Double = {
    try {
      val ERGUSD = new HttpGet(
        s"https://api.coingecko.com/api/v3/simple/price?ids=ergo&vs_currencies=USD"
      )
      val client = HttpClients.custom().build()
      val response = client.execute(ERGUSD)
      val resp = EntityUtils.toString(response.getEntity)
      val gson = new Gson()
      gson.fromJson(resp, classOf[CoinGekoFormat]).ergo.usd
    } catch {
      case e: Exception => throw new IllegalAccessException("api error")
    }
  }

  def calulateLiliumFee(amountNFTs: Long): Long = {
    val alpha = 54.10
    val beta = 0.03
    val feeUSD =
      math.floor(alpha * math.log((beta * amountNFTs) + 1)).asInstanceOf[Int]

    val ERGUSD = getERGUSD
    val feeNanoERGs = (BigDecimal(feeUSD / ERGUSD)
      .setScale(3, BigDecimal.RoundingMode.HALF_UP)
      .toDouble * Parameters.OneErg).toLong

    feeNanoERGs
  }

  private val exp = new explorerApi(
    DefaultNodeInfo(ctx.getNetworkType).explorerUrl
  )
  private val outBoxObj = new OutBoxes(this.ctx)
  private val metadataTranscoder = new MetadataTranscoder
  private val encoder = new metadataTranscoder.Encoder
  private val txHelper =
    new TransactionHelper(
      this.ctx,
      liliumTxOperatorMnemonic,
      liliumTxOperatorMnemonicPw
    )
  private val compiler = new ContractCompile(ctx)
  private val issuerContract =
    compiler.compileIssuerContract(issuerContractString, minerFee)

  private val r4 = ErgoValue.of(1) //version of collection eip
  private val r5 =
    encoder.encodeCollectionInfo(collectionInfo) // collection info

  println("Collection Issuer R5: " + r5.toHex)

  private val r6 = encoder.encodeSocialMedaInfo(socialMediaMap) // socials
  private val r7 = ErgoValue.of(mintExpiryTimestamp) //minting expiry

  println("Collection Issuer R6: " + r6.toHex)

  private val emptyArray: Array[Byte] = Array()
  private val emptyAdditionalInfo = Colls.fromArray(
    Array((Colls.fromArray(emptyArray), Colls.fromArray(emptyArray)))
  )
  private val r8 = ErgoValueBuilder.buildFor(emptyAdditionalInfo)
  private val r9 = ErgoValue.of(collectionTokensAmount)

  private var preMintToken: ErgoToken = new ErgoToken("", 1)
  private var whitelistToken: ErgoToken = new ErgoToken("", 1)

  preMintToken = null
  whitelistToken = null

  private val collectionIssuerContract: ErgoContract =
    compiler.compileCollectionIssuerContract(
      collectionIssuerContractString,
      txHelper.senderAddress //liliumTxOperator
    )

  private val singletonIssuerContract: ErgoContract =
    compiler.compileSingletonIssuerContract(
      singletonIssuerContractString,
      txHelper.senderAddress //liliumTxOperator
    )

  println(
    "Collection Issuer Contract: " + collectionIssuerContract.toAddress.toString
  )
  println(
    "Singleton Issuer Contract: " + singletonIssuerContract.toAddress.toString
  )

  private val genesisTX: SignedTransaction =
    txHelper.simpleSend(
      List(txHelper.senderAddress),
      List(
        0.02 + convertERGLongToDouble(calulateLiliumFee(collectionTokensAmount))
      )
    )
  val genesisOutBox: InputBox = genesisTX.getOutputsToSpend.get(0)
  val inputBoxList = new util.ArrayList[InputBox]()
  inputBoxList.add(genesisOutBox)

  println("Genesis: " + txHelper.sendTx(genesisTX))

  if (premintAccepted && premintTokenAmount != -1) {
    val preMintContract = compiler.compilePreMintIssuerContract(
      premintIssuerContractString,
      txHelper.senderAddress
    )

    val preMintIssuerBox =
      outBoxObj.preMintIssuerBox(preMintContract, premintTokenAmount, 0.002)

    val preMintIssuerTxn = txHelper.signTransaction(
      txHelper.buildUnsignedTransaction(inputBoxList, List(preMintIssuerBox))
    )

    inputBoxList.remove(0)
    inputBoxList.add(preMintIssuerTxn.getOutputsToSpend.get(2))

    val preMintTokenTx: SignedTransaction = txHelper.createToken(
      artistAddress,
      List(0.001),
      List(preMintIssuerTxn.getOutputsToSpend.get(0)).asJava,
      name = collectionName + " Premint Token",
      description = "Allows mint for " + collectionName + " before public",
      tokenAmount = premintTokenAmount,
      tokenDecimals = 0
    )

    preMintToken = preMintTokenTx.getOutputsToSpend.get(0).getTokens.get(0)

    println("Premint Issuer Tx: " + txHelper.sendTx(preMintIssuerTxn))
    println("Premint Token Mint Tx: " + txHelper.sendTx(preMintTokenTx))

  }

  if (whitelistAccepted && whitelistTokenAmount != -1) {
    val whitelistContract = compiler.compileWhitelistIssuerContract(
      whitelistIssuerContractString,
      txHelper.senderAddress
    )

    val whitelistIssuerBox =
      outBoxObj.whiteListIssuerBox(
        whitelistContract,
        whitelistTokenAmount,
        0.002
      )

    val whitelistIssuerTxn = txHelper.signTransaction(
      txHelper.buildUnsignedTransaction(inputBoxList, List(whitelistIssuerBox))
    )

    inputBoxList.remove(0)
    inputBoxList.add(whitelistIssuerTxn.getOutputsToSpend.get(2))

    val whitelistTokenTx: SignedTransaction = txHelper.createToken(
      artistAddress,
      List(0.001),
      List(whitelistIssuerTxn.getOutputsToSpend.get(0)).asJava,
      name = collectionName + " Whitelist Token",
      description = "Allows free mint for " + collectionName,
      tokenAmount = whitelistTokenAmount,
      tokenDecimals = 0
    )

    whitelistToken = whitelistTokenTx.getOutputsToSpend.get(0).getTokens.get(0)

    println("Whitelist Issuer Tx: " + txHelper.sendTx(whitelistIssuerTxn))
    println("Whitelist Token Mint Tx: " + txHelper.sendTx(whitelistTokenTx))

  }

  private val singletonIssuerBox =
    outBoxObj.genericContractBox(singletonIssuerContract, 0.002)

  private val collectionIssuerBox = outBoxObj
    .initNFTCollection(
      Array(r4, r5, r6, r7, r8, r9),
      collectionIssuerContract,
      0.002 + 0.001 + convertERGLongToDouble(minerFee)
    )

  println(
    "collectionIssuerContract: " + collectionIssuerContract.toAddress.toString
  )

  private val liliumFeeBox = outBoxObj.payoutBox(
    txHelper.senderAddress,
    convertERGLongToDouble(calulateLiliumFee(collectionTokensAmount))
  )

  private val initTransactionP1 = txHelper.signTransaction(
    txHelper.buildUnsignedTransaction(
      inputBoxList,
      List(
        singletonIssuerBox,
        collectionIssuerBox,
        liliumFeeBox
      )
    )
  )
  println(
    "initTransactionP1: " +
      txHelper.sendTx(initTransactionP1)
  )

  private val singletonIssuanceContract =
    compiler.compileSingletonIssuanceContract(
      singletonIssuanceContractString,
      new ErgoToken(
        initTransactionP1.getOutputsToSpend.get(0).getId.toString,
        1
      ),
      txHelper.senderAddress
    )

  val proxyContract: ErgoContract =
    compiler.compileProxyContract(proxyContractString, minerFee)

  val stateContract: ErgoContract = compiler.compileStateContract(
    stateContractString,
    proxyContract,
    issuerContract,
    artistAddress,
    royaltyBlakeHash,
    new ErgoToken(
      initTransactionP1.getOutputsToSpend.get(1).getId.toString,
      1L
    ),
    new ErgoToken(
      initTransactionP1.getOutputsToSpend.get(0).getId.toString,
      1L
    ),
    priceOfNFTNanoErg,
    liliumFeeAddress,
    liliumFeePercent,
    minTxOperatorFeeNanoErg,
    minerFee,
    minBoxValue
  )

  private val singletonTokenTx: SignedTransaction = txHelper.createToken(
    singletonIssuanceContract.toAddress,
    List(0.001),
    List(
      initTransactionP1.getOutputsToSpend
        .get(0)
        .withContextVars(
          ContextVar.of(
            0.toByte,
            singletonIssuanceContract.toAddress.asP2S().scriptBytes
          )
        )
    ).asJava,
    singletonIssuanceContract.toAddress,
    name = "State Box Singleton",
    description = "State Box Singleton",
    tokenAmount = 1L,
    tokenDecimals = 0
  )

  println("Singleton Creation Tx: " + txHelper.sendTx(singletonTokenTx))

  private val collectionIssuanceContract =
    compiler.compileCollectionIssuanceContract(
      collectionIssuanceContractString,
      txHelper.senderAddress
    )

  private val collectionTokenTx: SignedTransaction = txHelper.createToken(
    collectionIssuanceContract.toAddress,
    List(0.002 + convertERGLongToDouble(minerFee)),
    List(
      initTransactionP1.getOutputsToSpend
        .get(1)
        .withContextVars(
          ContextVar.of(
            0.toByte,
            collectionIssuanceContract.toAddress.asP2S().scriptBytes
          )
        )
    ).asJava,
    collectionIssuanceContract.toAddress,
    isCollection = true,
    name = collectionName,
    description = collectionDescription,
    tokenAmount = collectionTokensAmount,
    tokenDecimals = 0
  )

  println("Collection Creation Tx: " + txHelper.sendTx(collectionTokenTx))

  println("State Contract: " + stateContract.toAddress.toString)

  println("startingTimestamp: " + startingTimestamp)
  println("expiryTimestamp: " + expiryTimestamp)

  val stateBox: OutBox = outBoxObj.buildStateBox(
    stateContract,
    issuanceMetaDataMap,
    issuerMetaDataMap,
    singletonTokenTx.getOutputsToSpend.get(0).getTokens.get(0),
    collectionTokenTx.getOutputsToSpend.get(0).getTokens.get(0),
    0,
    startingTimestamp,
    expiryTimestamp,
    Array(
      returnCollectionTokensToArtist,
      whitelistAccepted,
      whitelistBypass,
      premintAccepted
    ),
    preMintToken,
    whitelistToken,
    0.001 + convertERGLongToDouble(minerFee) + convertERGLongToDouble(
      minTxOperatorFeeNanoErg
    )
  )

  val stateBoxInput = new util.ArrayList[InputBox]()

  stateBoxInput.add(
    singletonTokenTx.getOutputsToSpend
      .get(0)
      .withContextVars(
        ContextVar.of(0.toByte, stateContract.toAddress.asP2S().scriptBytes)
      )
  )

  stateBoxInput.add(
    collectionTokenTx.getOutputsToSpend
      .get(0)
      .withContextVars(
        ContextVar.of(0.toByte, stateContract.toAddress.asP2S().scriptBytes),
        ContextVar.of(
          1.toByte,
          exp.getErgoBoxfromID(
            initTransactionP1.getOutputsToSpend.get(1).getId.toString
          )
        )
      )
  )

  val firstStateBoxTx: SignedTransaction = txHelper.signTransaction(
    txHelper.buildUnsignedTransaction(stateBoxInput, List(stateBox))
  )

  val stateBoxTx: String = txHelper.sendTx(firstStateBoxTx)
  println("State Box Tx: " + stateBoxTx)

  new conf(
    stateContract.toAddress.toString,
    singletonTokenTx.getOutputsToSpend.get(0).getTokens.get(0).getId.toString,
    issuerContract.toAddress.toString,
    proxyContract.toAddress.toString,
    initTransactionP1.getOutputsToSpend.get(1).getId.toString
  ).write("contracts.json")

  DatabaseAPI.createArtistEntry(
    singletonTokenTx.getOutputsToSpend.get(0).getTokens.get(0).getId.toString,
    initTransactionP1.getOutputsToSpend.get(1).getId.toString,
    startingTimestamp,
    expiryTimestamp,
    artistAddress.toString,
    exportAVL(issuerMetaDataMap).getJsonString,
    exportAVL(issuanceMetaDataMap).getJsonString,
    encodedRoyalty
  )

  def convertERGLongToDouble(num: Long): Double = {
    val value = num * math.pow(10, -9)
    val x =
      (math floor value * math.pow(10, num.toString.length + 4)) / math.pow(
        10,
        num.toString.length + 4
      )
    val bNum = math.BigDecimal(x)
    val finalNum = bNum.underlying().stripTrailingZeros()
    finalNum.toString.toDouble
  }

}
