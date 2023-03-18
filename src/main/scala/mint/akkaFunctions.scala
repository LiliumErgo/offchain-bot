package mint

import AVL.IssuerBox.{IssuerHelpersAVL, IssuerValue}
import AVL.NFT.{IndexKey, IssuanceAVLHelpers, IssuanceValueAVL}
import AVL.utils.avlUtils
import configs.{
  AvlJson,
  Data,
  collectionParser,
  conf,
  masterMeta,
  serviceOwnerConf
}

import java.util.{Map => JMap}
import scala.collection.JavaConverters._
import scala.collection.mutable
import initilization.createCollection
import org.ergoplatform.ErgoBox
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.appkit.{
  Address,
  BlockchainContext,
  ErgoToken,
  ErgoValue,
  InputBox,
  SigmaProp,
  SignedTransaction
}
import org.ergoplatform.explorer.client.model.OutputInfo
import special.collection.Coll
import contracts.LiliumContracts
import io.getblok.getblok_plasma.PlasmaParameters
import io.getblok.getblok_plasma.collections.{PlasmaMap, Proof, ProvenResult}
import sigmastate.AvlTreeFlags
import utils.{
  BoxAPI,
  BoxJson,
  ContractCompile,
  DatabaseAPI,
  LiliumEntry,
  MetadataTranscoder,
  NodeBoxJson,
  TransactionHelper,
  explorerApi,
  fileOperations
}

import java.util
import scala.collection.JavaConverters._
import scala.collection.convert.ImplicitConversions.`collection asJava`
import scala.collection.mutable.ListBuffer
import scala.collection.mutable

class akkaFunctions {

  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext
  private val serviceFilePath = "serviceOwner.json"
  private val contractConfFilePath = "contracts.json"
  private lazy val serviceConf = serviceOwnerConf.read(serviceFilePath)
  private lazy val contractsConf = conf.read(contractConfFilePath)

  private val exp = new explorerApi(
    DefaultNodeInfo(ctx.getNetworkType).explorerUrl
  )
  private val walletMnemonic = serviceConf.txOperatorMnemonic
  private val walletMnemonicPw = serviceConf.txOperatorMnemonicPw
  private val txHelper =
    new TransactionHelper(this.ctx, walletMnemonic, walletMnemonicPw)

  println("Service Runner Address: " + txHelper.senderAddress)

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
      contractsConf.Contracts.issuerContract.contract,
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
        if (res == null) return
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

    val priceOfNFTNanoErg: Long = stateContract.getErgoTree
      .constants(29)
      .value
      .asInstanceOf[Long]

//    val priceOfNFTNanoErg: Long = 2000000

    val boxAPIObj = new BoxAPI(serviceConf.apiUrl, serviceConf.nodeUrl)

    val boxes: Array[InputBox] = boxJson
      .filter(box => validateProxyBox(box, singletonTokenId, priceOfNFTNanoErg))
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
      value: Long
  ): Boolean = {
    box.additionalRegisters.R4 != null && box.additionalRegisters.R5.serializedValue == singleton && box.value >= value
  }

}
