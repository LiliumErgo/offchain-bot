package mint

import AVL.IssuerBox.{IssuerHelpersAVL, IssuerValue}
import AVL.NFT.{IndexKey, IssuanceAVLHelpers, IssuanceValueAVL}
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

  private val artistAddressConstant = 6
  private val minerFeeConstant = 48
  private val minerFeeAndMinBoxValueSumConstant = 26
  private val collectionTokenConstant = 3
  private val liliumFeeAddressConstant = 46
  private val liliumFeeValueConstant = 45
  private val priceOfNFTNanoErgConstant = 39

  private def issuerTxn(
      stateBox: InputBox,
      proxyInput: InputBox,
      encodedRoyaltyHex: String,
      issuanceTreeFromDB: PlasmaMap[IndexKey, IssuanceValueAVL],
      issuerTreeFromDB: PlasmaMap[IndexKey, IssuerValue]
  ): SignedTransaction = {

    val mintUtil = new mintUtility(
      ctx = this.ctx,
      txOperatorMnemonic = walletMnemonic,
      txOperatorMnemonicPw = walletMnemonicPw
    )

    mintUtil.buildIssuerTx(
      stateBox,
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
      issuanceTree: PlasmaMap[IndexKey, IssuanceValueAVL]
  ): SignedTransaction = {

    val mintUtil = new mintUtility(
      ctx = this.ctx,
      txOperatorMnemonic = walletMnemonic,
      txOperatorMnemonicPw = walletMnemonicPw
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

    for (proxyInput <- proxyInputArray) {

      val stateBoxInput =
        exp.getUnspentBoxFromMempool(latestStateBoxInput.get(0))

      if (
        stateBoxInput.getTokens.size() == 1 && stateBoxInput.getRegisters
          .size() == 3
      ) {
        println("Last StateBox Detected")
        return
      }

      val issuerTxn = this.issuerTxn(
        stateBoxInput,
        proxyInput,
        dataBaseResponse.royalty_bytes,
        issuanceTreeFromDB,
        issuerTreeFromDB
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
        issuanceTreeFromDB
      )

      println("NFT Tx: " + txHelper.sendTx(nftTxn))
      Thread.sleep(500)

      latestStateBoxInput.clear()
      latestStateBoxInput.add(issuerTxn.getOutputsToSpend.get(1).getId.toString)
    }

  }

  def mint(singletonId: String, boxJson: Array[BoxJson]): Unit = { //mints tickets

    val latestStateBoxInput = new util.ArrayList[String]()

    val singletonTokenId =
      ErgoValue.of(new ErgoToken(singletonId, 1).getId.getBytes).toHex

    println("Searching for: " + singletonId)

    val stateBox: OutputInfo = {
      try {
        val res = exp.getUnspentBoxFromTokenID(singletonId)
        if (res == null || res.getAdditionalRegisters.size() < 5) return
        else res
      } catch {
        case e: Exception => return
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

    val priceOfNFTNanoErg: Long =
      try {
        stateContract.getErgoTree
          .constants(priceOfNFTNanoErgConstant)
          .value
          .asInstanceOf[Long]
      } catch {
        case e: Exception => println("error with decoding NFT price"); return
      }

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

//    val priceOfNFTNanoErg: Long = 100000000
//    val minerFee = 1600000
//    val liliumFee = 5000000
    val boxCreationFee = 1000000
    val txOperatorFee = serviceConf.minTxOperatorFeeNanoErg

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

    val tokenTuple = {
      try {
        stateBoxFromApi
          .additionalRegisters(ErgoBox.R9)
          .value
          .asInstanceOf[(Coll[Byte], Coll[Byte])]
      } catch {
        case e: Exception =>
          println("error with getting statebox token tuple"); return
      }
    }

    val boxAPIObj = new BoxAPI(serviceConf.apiUrl, serviceConf.nodeUrl)

    val boxes: Array[InputBox] = boxJson
      .filter(box =>
        validateProxyBox(
          box,
          singletonTokenId,
          priceOfNFTNanoErg,
          boxCreationFee + minerFee + liliumFee + minerFee + txOperatorFee,
          stateBoxTimeStamp,
          stateBoxBooleans,
          tokenTuple
        )
      )
      .map(boxAPIObj.convertJsonBoxToErgoBox)

    if (boxes.length == 0) {
      println("No Boxes Found")
      return
    }

    latestStateBoxInput.add(stateBox.getBoxId)

    mintNFT(
      boxes,
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
    //    println("proxy address: " + proxyAddress.toString)

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
      priceOfNFTNanoErg: Long,
      value: Long,
      timeStamp: Long,
      stateBoxBooleans: Array[Boolean],
      tokenTuple: (Coll[Byte], Coll[Byte])
  ): Boolean = {

    def hasToken(box: BoxJson, tokenId: String): Boolean =
      box.assets.exists(a => a.tokenId == tokenId)

    // This method updates the value based on different conditions.
    def updatedValue(
        value: Long,
        whitelistAccepted: Boolean,
        hasWhitelistToken: Boolean
    ): Long = {
      if (whitelistAccepted && hasWhitelistToken) value + 1000000L
      else value + priceOfNFTNanoErg
    }

    try {
      val (whitelistAccepted, whitelistBypass, premintAccepted) =
        (stateBoxBooleans(1), stateBoxBooleans(2), stateBoxBooleans(3))
      val (whitelistToken, premintToken) = (
        new ErgoToken(tokenTuple._1.toArray, 1),
        new ErgoToken(tokenTuple._2.toArray, 1)
      )
      val hasWhitelistToken = hasToken(box, whitelistToken.getId.toString)
      val hasPremintToken = hasToken(box, premintToken.getId.toString)
      val bypass =
        (whitelistAccepted && whitelistBypass && hasWhitelistToken) || (premintAccepted && hasPremintToken)
      val allowedMinValue =
        updatedValue(value, whitelistAccepted, hasWhitelistToken)

      box.additionalRegisters.R4 != null &&
      box.additionalRegisters.R5.serializedValue == singleton &&
      box.value >= allowedMinValue &&
      (timeStamp < System.currentTimeMillis() || bypass)
    } catch {
      case _: Exception => false
    }
  }

}
