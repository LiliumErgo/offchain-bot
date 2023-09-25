package refund

import AVL.NFT.{IndexKey, IssuanceValueAVL}
import AVL.utils.avlUtils
import configs.serviceOwnerConf
import io.getblok.getblok_plasma.PlasmaParameters
import io.getblok.getblok_plasma.collections.PlasmaMap
import mint.{
  Client,
  DefaultNodeInfo,
  ErgoScriptConstantDecodeError,
  mintUtility
}
import org.ergoplatform.appkit.{Address, InputBox, SignedTransaction}
import org.ergoplatform.explorer.client.model.OutputInfo
import sigmastate.AvlTreeFlags
import utils.{ContractCompile, DatabaseAPI, TransactionHelper, explorerApi}

import scala.collection.JavaConverters._

object StuckNft extends App {

  val issuerTxnHash =
    "246501442be3e697ce7ba71b7cc83e2526756fadf907b220d132557c9768b510" // add tx hash here

  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext
  private val compiler = new ContractCompile(ctx)
  private val serviceFilePath = "serviceOwner.json"
  private lazy val serviceConf = serviceOwnerConf.read(serviceFilePath)

  private val exp = new explorerApi(
    DefaultNodeInfo(ctx.getNetworkType).explorerUrl
  )

  private val txHelper =
    new TransactionHelper(
      this.ctx,
      serviceConf.txOperatorMnemonic,
      serviceConf.txOperatorMnemonicPw
    )

  val artistAddressConstant = 7
  val minerFeeConstant = 59
  val collectionTokenConstant = 3
  val liliumFeeAddressConstant = 148
  val liliumFeeValueConstant = 147
  val priceOfNFTNanoErgConstant = 82
  val paymentTokenAmountConstant = 65
  val txOperatorFeeConstant = 64
  val minBoxValueConstant = 51

  def nftTransaction(
      issuerBox: InputBox,
      newStateBox: InputBox,
      buyerAddress: Address,
      issuanceTree: PlasmaMap[IndexKey, IssuanceValueAVL],
      minerFee: Long,
      minerFeeAndMinBoxValueSum: Long,
      liliumFee: Long,
      txOperatorFee: Long,
      paymentTokenAmount: Long,
      priceOfNFTNanoErg: Long,
      artistAddress: Address,
      liliumFeeAddress: Address
  ): SignedTransaction = {

    val mintUtil = new mintUtility(
      ctx = this.ctx,
      txOperatorMnemonic = serviceConf.txOperatorMnemonic,
      txOperatorMnemonicPw = serviceConf.txOperatorMnemonicPw,
      minerFee,
      minerFeeAndMinBoxValueSum,
      liliumFee,
      txOperatorFee,
      paymentTokenAmount,
      priceOfNFTNanoErg,
      artistAddress,
      liliumFeeAddress
    )
    mintUtil.buildNFTBox(
      issuerBox,
      newStateBox,
      buyerAddress,
      issuanceTree
    )

  }

  val issuerTxn = exp.getBoxesfromTransaction(issuerTxnHash)
  val issuerTxnOutput0 =
    exp.getUnspentBoxFromMempool(issuerTxn.getOutputs.get(0).getBoxId)

  val singletonId = issuerTxn.getOutputs.get(1).getAssets.get(0).getTokenId

  val stateBox: OutputInfo = {
    try {
      val boxes = exp
        .getUnspentBoxesFromTokenID(singletonId)
        .asScala
      val res = boxes.filter(b => b.getAssets.size() == 2).head
      if (res == null) throw new IllegalArgumentException("no state box found")
      else res
    } catch {
      case e: Exception =>
        throw new IllegalArgumentException("error getting state box: " + e)
    }
  }

  val proxyInput =
    exp.getUnspentBoxFromMempool(issuerTxn.getInputs.get(1).getBoxId)

  val r4 = proxyInput.getRegisters
    .get(0)
    .getValue
    .asInstanceOf[special.sigma.SigmaProp]

  val proxySender = new org.ergoplatform.appkit.SigmaProp(r4)
    .toAddress(this.ctx.getNetworkType)

  val issuanceTreeFromDB = PlasmaMap[IndexKey, IssuanceValueAVL](
    AvlTreeFlags.AllOperationsAllowed,
    PlasmaParameters.default
  )

  avlUtils.AVLFromExport[IndexKey, IssuanceValueAVL](
    DatabaseAPI.getRow(singletonId).issuance_avl_bytes,
    issuanceTreeFromDB
  )

  val stateContract = Address
    .fromPropositionBytes(
      ctx.getNetworkType,
      exp
        .getUnspentBoxFromMempool(issuerTxn.getInputs.get(0).getBoxId)
        .toErgoValue
        .getValue
        .propositionBytes
        .toArray
    )
    .toErgoContract

  val minerFee = {
    try {
      stateContract.getErgoTree
        .constants(minerFeeConstant)
        .value
        .asInstanceOf[Long]
    } catch {
      case e: Exception =>
        println("error with decoding miner fee"); -1L
    }
  }

  val liliumFee = {
    try {
      stateContract.getErgoTree
        .constants(liliumFeeValueConstant)
        .value
        .asInstanceOf[Long]
    } catch {
      case e: Exception =>
        println("error with decoding lilium fee"); -1L
    }
  }

  val boxCreationFee: Long = {
    try {
      stateContract.getErgoTree
        .constants(minBoxValueConstant)
        .value
        .asInstanceOf[Long]
    } catch {
      case _: Exception =>
        throw new ErgoScriptConstantDecodeError(
          "Error decoding min box value"
        ); -1L
    }
  }

  val txOperatorFee = {
    try {
      stateContract.getErgoTree
        .constants(txOperatorFeeConstant)
        .value
        .asInstanceOf[Long]
    } catch {
      case _: Exception =>
        throw new ErgoScriptConstantDecodeError(
          "Error decoding tx operator fee"
        ); -1L
    }
  }

  val minerFeeAndMinBoxValueSum: Long = minerFee + boxCreationFee

  val paymentTokenAmount: Long = {
    try {
      stateContract.getErgoTree
        .constants(paymentTokenAmountConstant)
        .value
        .asInstanceOf[Long]
    } catch {
      case e: Exception =>
        println("error with decoding paymentTokenAmount"); -1L
    }
  }

  val priceOfNFTNanoErg: Long = {
    try {
      stateContract.getErgoTree
        .constants(priceOfNFTNanoErgConstant)
        .value
        .asInstanceOf[Long]
    } catch {
      case e: Exception =>
        println("error with decoding NFT price"); -1L
    }
  }

  val artistAddress = {
    try {
      new org.ergoplatform.appkit.SigmaProp(
        stateContract.getErgoTree
          .constants(artistAddressConstant)
          .value
          .asInstanceOf[special.sigma.SigmaProp]
      ).toAddress(ctx.getNetworkType)
    } catch {
      case _: Exception =>
        throw new ErgoScriptConstantDecodeError(
          "Error decoding artist address"
        ); Address.create("")
    }
  }

  val liliumFeeAddress = {
    try {
      new org.ergoplatform.appkit.SigmaProp(
        stateContract.getErgoTree
          .constants(liliumFeeAddressConstant)
          .value
          .asInstanceOf[special.sigma.SigmaProp]
      )
        .toAddress(this.ctx.getNetworkType)
    } catch {
      case _: Exception =>
        if (
          singletonId == "f0a491ad4c030dbf7a0caf682b18c054c6306644a547a86878e4acb910f321c6"
        ) {
          // hardcoded since constants cause an issue if liliumFeeAddress == artistAddress
          Address.create("9gWkqeBUdJxgPv9TYUM6mLY1RYkXHmJuHRhHnnM2UZ9qFqySotz")
        } else {
          throw new ErgoScriptConstantDecodeError(
            "Error decoding lilium fee address"
          )
        }
    }
  }

  val nftTxn = this.nftTransaction(
    issuerTxnOutput0,
    exp.getUnspentBoxFromMempool(
      stateBox.getBoxId.toString
    ), // this has to be an unspent statebox as it is used as data input
    proxySender, //should be extracted from r4 of input
    issuanceTreeFromDB,
    minerFee,
    minerFeeAndMinBoxValueSum,
    liliumFee,
    txOperatorFee,
    paymentTokenAmount,
    priceOfNFTNanoErg,
    artistAddress,
    liliumFeeAddress
  )

  println("NFT Tx: " + txHelper.sendTx(nftTxn))

}
