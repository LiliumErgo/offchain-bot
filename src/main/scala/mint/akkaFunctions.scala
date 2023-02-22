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
  ContractCompile,
  DatabaseAPI,
  LiliumEntry,
  MetadataTranscoder,
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
      latestStateBoxInput: util.ArrayList[String]
  ): Unit = {

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

      println(
        "Processing Collection " + stateBoxInput.getTokens.get(0).getId.toString
      )

      val dataBaseResponse: LiliumEntry =
        DatabaseAPI.getRow(stateBoxInput.getTokens.get(0).getId.toString)

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

      val issuerTxn = this.issuerTxn(
        stateBoxInput,
        proxyInput,
        dataBaseResponse.royalty_bytes,
        issuanceTreeFromDB,
        issuerTreeFromDB
      )

      println("Issuer Tx: " + txHelper.sendTx(issuerTxn))

      val r4 = proxyInput.getRegisters.get(0).toHex
      val prop =
        ErgoValue.fromHex(r4).getValue.asInstanceOf[special.sigma.SigmaProp]
      val proxySender = new org.ergoplatform.appkit.SigmaProp(prop)
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
      Thread.sleep(2000)

      latestStateBoxInput.clear()
      latestStateBoxInput.add(issuerTxn.getOutputsToSpend.get(1).getId.toString)
    }

  }

  def mint(): Unit = { //mints tickets
    val proxyAddress =
      Address.create(contractsConf.Contracts.proxyContract.contract)
//    println("proxy address: " + proxyAddress.toString)
    val validProxyBoxes = new util.ArrayList[InputBox]()
    val latestStateBoxInput = new util.ArrayList[String]()
    val boxes = client.getAllUnspentBox(proxyAddress)
    var counter = 0
//    println(boxes)

    for (box <- boxes) {
      counter += 1
      val validBox = (box.getRegisters.size() == 2) && (box.getRegisters
        .get(0)
        .getType
        .getRType
        .name == "SigmaProp") && (box.getRegisters
        .get(1)
        .getType
        .getRType
        .name == "Coll[Byte]")

      if (validBox) {
        val proxyBox = exp.getUnspentBoxFromMempool(box.getId.toString)
        val tokenIDFromRegister = proxyBox.getRegisters
          .get(1)
          .getValue
          .asInstanceOf[Coll[Byte]]
          .toArray
        val tokenID = new ErgoToken(tokenIDFromRegister, 1).getId.toString
        val stateBox = {
          try {
            exp.getUnspentBoxFromTokenID(tokenID)
          } catch {
            case e: Exception => null
          }
        }

        if (stateBox != null) {
          val lastStateBox: Boolean = {
            stateBox.getAssets.size() == 1 && stateBox.getAdditionalRegisters
              .size() == 3
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

          val dataBaseResp = DatabaseAPI.getRow(
            tokenID
          )

          if (
            !lastStateBox &&
            proxyBox.getValue >= priceOfNFTNanoErg
            && dataBaseResp != null
          ) {
            validProxyBoxes.add(proxyBox)
          }

        }

      }

    }

    if (validProxyBoxes.isEmpty) {
      return
    }

    val tokenIDFromRegister = validProxyBoxes
      .get(0)
      .getRegisters
      .get(1)
      .getValue
      .asInstanceOf[Coll[Byte]]
      .toArray
    val tokenID = new ErgoToken(tokenIDFromRegister, 1).getId.toString
    val stateBox = exp.getUnspentBoxFromTokenID(tokenID)

    latestStateBoxInput.add(stateBox.getBoxId)

    mintNFT(
      validProxyBoxes.asScala
        .filter(box =>
          box.getRegisters
            .get(1)
            .getValue
            .asInstanceOf[Coll[Byte]]
            .toArray sameElements tokenIDFromRegister
        )
        .toArray,
      latestStateBoxInput
    )
  }

  def main(): Unit = {

    println("proceeding with mint")
    mint()

  }

}
