package initilization

import AVL.IssuerBox.IssuerValue
import AVL.NFT.{IndexKey, IssuanceValueAVL}
import AVL.utils.avlUtils
import AVL.utils.avlUtils.exportAVL
import configs.conf
import io.getblok.getblok_plasma.collections.{LocalPlasmaMap, PlasmaMap}
import mint.DefaultNodeInfo
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
    socialMediaMap: mutable.Map[String, String],
    issuanceMetaDataMap: PlasmaMap[IndexKey, IssuanceValueAVL],
    issuerMetaDataMap: PlasmaMap[IndexKey, IssuerValue],
    priceOfNFTNanoErg: Long,
    liliumFeeAddress: Address,
    liliumFeeNanoErg: Long,
    minTxOperatorFeeNanoErg: Long,
    minerFee: Long
) {
  private val exp = new explorerApi(
    DefaultNodeInfo(ctx.getNetworkType).explorerUrl
  )
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
    compiler.compileIssuerContract(issuerContractString)

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
    txHelper.simpleSend(List(txHelper.senderAddress), List(0.02))
  val genesisOutBox: InputBox = genesisTX.getOutputsToSpend.get(0)
  val inputBoxList = new util.ArrayList[InputBox]()
  inputBoxList.add(genesisOutBox)

  println("Genesis: " + txHelper.sendTx(genesisTX))

  private val outBoxObj = new OutBoxes(this.ctx)

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
    (liliumFeeNanoErg * math.pow(10, -9))
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
    liliumFeeNanoErg,
    minTxOperatorFeeNanoErg,
    minerFee
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
    returnCollectionTokensToArtist,
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
      (math floor value * math.pow(10, num.toString.length + 2)) / math.pow(
        10,
        num.toString.length + 2
      )
    val bNum = math.BigDecimal(x)
    val finalNum = bNum.underlying().stripTrailingZeros()
    finalNum.toString.toDouble
  }

}
