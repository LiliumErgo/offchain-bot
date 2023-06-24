package mint

import AVL.IssuerBox.IssuerValue
import AVL.NFT.{IndexKey, IssuanceValueAVL}
import AVL.utils.avlUtils
import configs.serviceOwnerConf
import org.ergoplatform.appkit.{
  Address,
  ErgoToken,
  ErgoValue,
  InputBox,
  SignedTransaction
}
import org.ergoplatform.explorer.client.model.OutputInfo
import special.collection.Coll
import contracts.LiliumContracts
import io.getblok.getblok_plasma.PlasmaParameters
import io.getblok.getblok_plasma.collections.PlasmaMap
import org.ergoplatform.ErgoBox
import sigmastate.AvlTreeFlags
import utils.{
  BoxAPI,
  BoxJson,
  ContractCompile,
  DatabaseAPI,
  LiliumEntry,
  TransactionHelper,
  explorerApi
}

import java.util
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._

class ErgoScriptConstantDecodeError(message: String) extends Exception(message)
class lpBoxAPIError(message: String) extends Exception(message)

class akkaFunctions {

  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext
  private val compiler = new ContractCompile(ctx)
  private val serviceFilePath = "serviceOwner.json"
  private val contractConfFilePath = "contracts.json"
  private lazy val serviceConf = serviceOwnerConf.read(serviceFilePath)
  private val issuerContract =
    compiler.compileIssuerContract(
      LiliumContracts.IssuerContract.contractScript,
      serviceConf.minerFeeNanoErg
    )

  private val exp = new explorerApi(
    DefaultNodeInfo(ctx.getNetworkType).explorerUrl
  )
  private val walletMnemonic = serviceConf.txOperatorMnemonic
  private val walletMnemonicPw = serviceConf.txOperatorMnemonicPw
  private val txHelper =
    new TransactionHelper(this.ctx, walletMnemonic, walletMnemonicPw)

  println("Service Runner Address: " + txHelper.senderAddress)

  val artistAddressConstant = 7
  val minerFeeConstant = 59
  val collectionTokenConstant = 3
  val liliumFeeAddressConstant = 148
  val liliumFeeValueConstant = 147
  val priceOfNFTNanoErgConstant = 82
  val paymentTokenAmountConstant = 65
  val txOperatorFeeConstant = 64
  val minBoxValueConstant = 51

  private def issuerTxn(
      stateBox: InputBox,
      LPBox: InputBox,
      proxyInput: InputBox,
      encodedRoyaltyHex: String,
      issuanceTreeFromDB: PlasmaMap[IndexKey, IssuanceValueAVL],
      issuerTreeFromDB: PlasmaMap[IndexKey, IssuerValue],
      proxyValue: Long,
      stateBoxBooleans: Array[Boolean],
      tokenArray: Array[Array[Byte]],
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
      txOperatorMnemonic = walletMnemonic,
      txOperatorMnemonicPw = walletMnemonicPw,
      minerFee,
      minerFeeAndMinBoxValueSum,
      liliumFee,
      txOperatorFee,
      paymentTokenAmount,
      priceOfNFTNanoErg,
      artistAddress,
      liliumFeeAddress
    )

    mintUtil.buildIssuerTx(
      stateBox,
      LPBox,
      proxyValue,
      stateBoxBooleans,
      tokenArray,
      proxyInput,
      issuerContract,
      ErgoValue
        .fromHex(encodedRoyaltyHex)
        .asInstanceOf[ErgoValue[Coll[(Coll[Byte], Integer)]]],
      issuanceTreeFromDB,
      issuerTreeFromDB
    )

  }

  private def nftTransaction(
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
      txOperatorMnemonic = walletMnemonic,
      txOperatorMnemonicPw = walletMnemonicPw,
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

  private def mintNFT(
      proxyInputArray: Array[InputBox],
      stateBoxBooleans: Array[Boolean],
      tokenArray: Array[Array[Byte]],
      minerFeeAndMinBoxValueSum: Long,
      priceOfNFTNanoErg: Long,
      liliumFee: Long,
      minerFee: Long,
      txOperatorFee: Long,
      paymentTokenAmount: Long,
      artistAddress: Address,
      liliumFeeAddress: Address,
      whitelistToken: ErgoToken,
      paymentToken: ErgoToken,
      latestStateBoxInput: util.ArrayList[String],
      dataBaseResponse: LiliumEntry
  ): Unit = {

    val issuanceTreeFromDB = PlasmaMap[IndexKey, IssuanceValueAVL](
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default
    )
    val issuerTreeFromDB = PlasmaMap[IndexKey, IssuerValue](
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default
    )

    avlUtils.AVLFromExport[IndexKey, IssuanceValueAVL](
      dataBaseResponse.issuance_avl_bytes,
      issuanceTreeFromDB
    )
    avlUtils.AVLFromExport[IndexKey, IssuerValue](
      dataBaseResponse.issuer_avl_bytes,
      issuerTreeFromDB
    )

    val latestLPBoxInput: ListBuffer[String] = new ListBuffer[String]

    for (proxyInput <- proxyInputArray) {

      val stateBoxInput =
        exp.getUnspentBoxFromMempool(latestStateBoxInput.get(0))

      var proxyValue: Long = {
        if ( // payment token amount not checked since box filter in akkaFunctions already checks
          proxyInput.getTokens.asScala
            .exists(t =>
              t.getId.toString == whitelistToken.getId.toString || t.getId.toString == paymentToken.getId.toString
            )
        ) {
          minerFeeAndMinBoxValueSum + liliumFee + txOperatorFee + minerFee
        } else {
          minerFeeAndMinBoxValueSum + priceOfNFTNanoErg + liliumFee + txOperatorFee + minerFee
        }
      }

      val buyerIsPayingFee = {

        if (proxyInput.getValue >= proxyValue) {
          println("Buyer is paying fee")
          println("Proxy Value: " + proxyValue)
          true // buyer is paying fee
        } else {
          if (
            !proxyInput.getTokens.asScala
              .exists(t =>
                t.getId.toString == whitelistToken.getId.toString || t.getId.toString == paymentToken.getId.toString
              )
          ) {
            proxyValue = proxyValue - priceOfNFTNanoErg
          }
          println("Pool is paying fee")
          println("Proxy Value: " + proxyValue)

          false // buyer is not paying fee, LP needed
        }
      }

      val liquidityPoolBox: InputBox = {
        if (!buyerIsPayingFee && stateBoxBooleans(5)) {

          if (latestLPBoxInput.isEmpty) {
            val boxes = exp
              .getUnspentBoxesFromTokenID(
                stateBoxInput.getTokens.get(0).getId.toString
              )
              .asScala
            val res =
              boxes.filter(b => b.getAssets.size() == 1).head
            if (res == null) throw new lpBoxAPIError("LP Box not found")
            else exp.getUnspentBoxFromMempool(res.getBoxId)
          } else {
            exp.getUnspentBoxFromMempool(latestLPBoxInput.head)
          }

        } else {
          null
        }
      }

      if (
        stateBoxInput.getTokens.size() == 1 && stateBoxInput.getRegisters
          .size() == 3
      ) {
        println("Last StateBox Detected")
        return
      }

      if (
        (!buyerIsPayingFee && liquidityPoolBox.getValue >= proxyValue) || buyerIsPayingFee
      ) {

        val issuerTxn = this.issuerTxn(
          stateBoxInput,
          liquidityPoolBox,
          proxyInput,
          dataBaseResponse.royalty_bytes,
          issuanceTreeFromDB,
          issuerTreeFromDB,
          proxyValue,
          stateBoxBooleans,
          tokenArray,
          minerFee,
          minerFeeAndMinBoxValueSum,
          liliumFee,
          txOperatorFee,
          paymentTokenAmount,
          priceOfNFTNanoErg,
          artistAddress,
          liliumFeeAddress
        )

        println("Issuer Tx: " + txHelper.sendTx(issuerTxn))

        val r4 = proxyInput.getRegisters
          .get(0)
          .getValue
          .asInstanceOf[special.sigma.SigmaProp]

        val proxySender = new org.ergoplatform.appkit.SigmaProp(r4)
          .toAddress(this.ctx.getNetworkType)

        if (issuerTxn.getOutputsToSpend.size() == 3) {
          println("Sale has ended for this collection")
          return
        }

        val nftTxn = this.nftTransaction(
          issuerTxn.getOutputsToSpend.get(0),
          issuerTxn.getOutputsToSpend.get(1),
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
        Thread.sleep(500)

        latestStateBoxInput.clear()
        latestStateBoxInput.add(
          issuerTxn.getOutputsToSpend.get(1).getId.toString
        )

        if (!buyerIsPayingFee && stateBoxBooleans(5)) {
          latestLPBoxInput.clear()
          latestLPBoxInput.append(
            issuerTxn.getOutputsToSpend.get(4).getId.toString
          )
        }

      }
    }

  }

  def mint(singletonId: String, boxJson: Array[BoxJson]): Unit = { //mints tickets

    val latestStateBoxInput = new util.ArrayList[String]()

    val singletonTokenId =
      ErgoValue.of(new ErgoToken(singletonId, 1).getId.getBytes).toHex

    println("Searching for: " + singletonId)

    val stateBox: OutputInfo = {
      try {
        val boxes = exp
          .getUnspentBoxesFromTokenID(singletonId)
          .asScala
        val res = boxes.filter(b => b.getAssets.size() == 2).head
        if (res == null) return
        else res
      } catch {
        case e: Exception => println("error getting state box: " + e); return
      }
    }

    val stateContract = Address
      .fromPropositionBytes(
        ctx.getNetworkType,
        exp
          .getUnspentBoxFromMempool(stateBox.getBoxId)
          .toErgoValue
          .getValue
          .propositionBytes
          .toArray
      )
      .toErgoContract

    val priceOfNFTNanoErg: Long = {
      try {
        stateContract.getErgoTree
          .constants(priceOfNFTNanoErgConstant)
          .value
          .asInstanceOf[Long]
      } catch {
        case e: Exception => println("error with decoding NFT price"); return
      }
    }
//    val priceOfNFTNanoErg: Long = 100000000

    val paymentTokenAmount: Long = {
      try {
        stateContract.getErgoTree
          .constants(paymentTokenAmountConstant)
          .value
          .asInstanceOf[Long]
      } catch {
        case e: Exception =>
          println("error with decoding paymentTokenAmount"); return
      }
    }

    //    val paymentTokenAmount = 10000000

    val minerFee = {
      try {
        stateContract.getErgoTree
          .constants(minerFeeConstant)
          .value
          .asInstanceOf[Long]
      } catch {
        case e: Exception => println("error with decoding miner fee"); return
      }
    }

//    val minerFee = 1600000

    val liliumFee = {
      try {
        stateContract.getErgoTree
          .constants(liliumFeeValueConstant)
          .value
          .asInstanceOf[Long]
      } catch {
        case e: Exception => println("error with decoding lilium fee"); return
      }
    }

//    val liliumFee = 5000000

    val boxCreationFee = {
      try {
        stateContract.getErgoTree
          .constants(minBoxValueConstant)
          .value
          .asInstanceOf[Long]
      } catch {
        case _: Exception =>
          throw new ErgoScriptConstantDecodeError(
            "Error decoding min box value"
          )
      }
    }

//    val boxCreationFee = 1000000

    val minerFeeAndMinBoxValueSum = minerFee + boxCreationFee

//    val minerFeeAndMinBoxValueSum = 2600000

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
          )
      }
    }

//    val txOperatorFee = serviceConf.minTxOperatorFeeNanoErg

    val stateBoxFromApi = exp.getErgoBoxfromID(stateBox.getBoxId)

    val stateBoxTimeStamp = {
      try {
        stateBoxFromApi
          .additionalRegisters(ErgoBox.R7)
          .value
          .asInstanceOf[(Long, Long)]
          ._1
      } catch {
        case e: Exception => println("error with timestamp"); return
      }
    }
    val stateBoxBooleans = {
      try {
        stateBoxFromApi
          .additionalRegisters(ErgoBox.R8)
          .value
          .asInstanceOf[Coll[Boolean]]
          .toArray
      } catch {
        case e: Exception =>
          println("error with getting statebox booleans"); return
      }
    }

    val tokenArray = {
      try {
        stateBoxFromApi
          .additionalRegisters(ErgoBox.R9)
          .value
          .asInstanceOf[Coll[Coll[Byte]]]
          .toArray
          .map(coll => coll.toArray)
      } catch {
        case e: Exception =>
          println("error with getting statebox token tuple"); return
      }
    }

    val boxAPIObj = new BoxAPI(serviceConf.apiUrl, serviceConf.nodeUrl)

    var saleLPBoxValue: Long = {
      try {
        exp
          .getUnspentBoxesFromTokenID(singletonId)
          .asScala
          .filter(b => b.getAssets.size() == 1)
          .head
          .getValue
      } catch {
        case e: Exception => 0
      }
    }

    val fees = boxCreationFee + minerFee + liliumFee + minerFee + txOperatorFee

    val boxes: Array[InputBox] = boxJson
      .filter(box => {
        val isValid = validateProxyBox(
          box,
          singletonTokenId,
          saleLPBoxValue,
          priceOfNFTNanoErg,
          paymentTokenAmount,
          fees,
          stateBoxTimeStamp,
          stateBoxBooleans,
          tokenArray
        )
        isValid

      })
      .map(boxAPIObj.convertJsonBoxToErgoBox)

    if (boxes.length == 0) {
      println("No Boxes Found")
      return
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
          )
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
          throw new ErgoScriptConstantDecodeError(
            "Error decoding lilium fee address"
          )
      }
    }

    latestStateBoxInput.add(stateBox.getBoxId)

    mintNFT(
      boxes,
      stateBoxBooleans,
      tokenArray,
      minerFeeAndMinBoxValueSum,
      priceOfNFTNanoErg,
      liliumFee,
      minerFee,
      txOperatorFee,
      paymentTokenAmount,
      artistAddress,
      liliumFeeAddress,
      new ErgoToken(tokenArray.head, 1), // should be whitelist token
      new ErgoToken(tokenArray(2), 1), // should be payment token
      latestStateBoxInput,
      DatabaseAPI.getRow(singletonId)
    )
  }

  def main(): Unit = {

    println("proceeding with mint")

    val compiler = new ContractCompile(ctx)

    val proxyAddress = compiler
      .compileProxyContract(
        LiliumContracts.ProxyContract.contractScript,
        serviceConf.minerFeeNanoErg
      )
      .toAddress

    val boxAPIObj = new BoxAPI(serviceConf.apiUrl, serviceConf.nodeUrl)

    val boxes =
      boxAPIObj
        .getUnspentBoxesFromApi(proxyAddress.toString, selectAll = true)
        .items

    DatabaseAPI.getAllSingletons.foreach(t => mint(t, boxes))

  }

  def validateProxyBox(
      box: BoxJson,
      singleton: String,
      saleLPBoxValue: Long,
      priceOfNFTNanoErg: Long,
      paymentTokenAmount: Long,
      value: Long,
      timeStamp: Long,
      stateBoxBooleans: Array[Boolean],
      tokenArray: Array[Array[Byte]]
  ): Boolean = {

    def hasToken(box: BoxJson, tokenId: String, amount: Long): Boolean =
      box.assets.exists(a => a.tokenId == tokenId && a.amount >= amount)

    // This method updates the value based on different conditions.
    def updatedValue(
        value: Long,
        whitelistAccepted: Boolean,
        hasWhitelistToken: Boolean,
        preMintTokenAccepted: Boolean,
        hasPreMintToken: Boolean,
        paymentTokenAccepted: Boolean,
        hasPaymentToken: Boolean,
        usePool: Boolean,
        ergAccepted: Boolean
    ): Long = {

      if (usePool && box.value >= priceOfNFTNanoErg) {
        priceOfNFTNanoErg
      } else if (
        usePool && !hasPreMintToken && whitelistAccepted && hasWhitelistToken || (preMintTokenAccepted && hasPreMintToken && paymentTokenAccepted && hasPaymentToken) || usePool && paymentTokenAccepted && hasPaymentToken
      ) {
        1000000L
      } else if (usePool && hasPreMintToken && preMintTokenAccepted) {
        priceOfNFTNanoErg
      } else if (
        (whitelistAccepted && hasWhitelistToken) || (paymentTokenAccepted && hasPaymentToken)
      ) {
        value + 1000000L
      } else if (
        ergAccepted || (timeStamp < System
          .currentTimeMillis()) && (hasPreMintToken && preMintTokenAccepted)
      ) {
        value + priceOfNFTNanoErg
      } else {
        -1L
      }
    }

    try {
      val (
        whitelistAccepted,
        whitelistBypass,
        premintAccepted,
        paymentTokenAccepted,
        usePool,
        ergAccepted
      ) =
        (
          stateBoxBooleans(1),
          stateBoxBooleans(2),
          stateBoxBooleans(3),
          stateBoxBooleans(4),
          stateBoxBooleans(5),
          stateBoxBooleans(6)
        )
      val (whitelistToken, premintToken, paymentToken) = (
        new ErgoToken(tokenArray.head, 1),
        new ErgoToken(tokenArray(1), 1),
        new ErgoToken(tokenArray(2), 1)
      )
      val hasWhitelistToken = hasToken(box, whitelistToken.getId.toString, 1L)
      val hasPremintToken = hasToken(box, premintToken.getId.toString, 1L)
      val hasPaymentToken =
        hasToken(box, paymentToken.getId.toString, paymentTokenAmount)

      if (!hasWhitelistToken && !hasPaymentToken & !ergAccepted) { // Both MUST be false

        return false;
      }

      val bypass =
        (whitelistAccepted && whitelistBypass && hasWhitelistToken) || ((premintAccepted && hasPremintToken) && timeStamp > System
          .currentTimeMillis())

      val allowedMinValue =
        updatedValue(
          value,
          whitelistAccepted,
          hasWhitelistToken,
          premintAccepted,
          hasPremintToken,
          paymentTokenAccepted,
          hasPaymentToken,
          usePool && saleLPBoxValue >= value,
          ergAccepted
        )

      allowedMinValue != -1L && box.additionalRegisters.R4 != null &&
      box.additionalRegisters.R5.serializedValue == singleton &&
      box.value >= allowedMinValue &&
      (timeStamp < System.currentTimeMillis() || bypass)
    } catch {
      case e: Exception => false
    }
  }

}
