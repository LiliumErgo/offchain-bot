package testMint

import AVL.IssuerBox.IssuerHelpersAVL
import AVL.NFT.IssuanceAVLHelpers
import configs.{collectionParser, conf, masterMeta, serviceOwnerConf}
import mint.{Client, DefaultNodeInfo}
import org.ergoplatform.appkit._
import special.collection.Coll
import utils.{
  ContractCompile,
  MetadataTranscoder,
  TransactionHelper,
  explorerApi
}

import java.util
import scala.collection.JavaConverters._
import scala.collection.mutable

class akkaFunctions(
    issuanceTree: IssuanceAVLHelpers,
    issuerTree: IssuerHelpersAVL
) {

  private val client: Client = new Client()
  client.setClient
  private val ctx = client.getContext
  private val compiler = new ContractCompile(ctx)
  private val serviceFilePath = "serviceOwner.json"
  private val metadataJsonFilePath = "metadata.json"
  private val issuanceAVLPath = "avlData/issuanceMetaData"
  private val issuerAVLPath = "avlData/issuerMetaData"
  private val collectionJsonFilePath = "collection.json"
  private val contractConfFilePath = "contracts.json"
  private lazy val serviceConf = serviceOwnerConf.read(serviceFilePath)
  private lazy val contractsConf = conf.read(contractConfFilePath)
  private val metadataFromJson = masterMeta.read(metadataJsonFilePath)
  private val collectionFromJson = collectionParser.read(collectionJsonFilePath)

  private val exp = new explorerApi(
    DefaultNodeInfo(ctx.getNetworkType).explorerUrl
  )
  private val walletMnemonic = serviceConf.txOperatorMnemonic
  private val walletMnemonicPw = serviceConf.txOperatorMnemonicPw
  private val txHelper =
    new TransactionHelper(this.ctx, walletMnemonic, walletMnemonicPw)

  println(txHelper.senderAddress)

  private val metadataTranscoder = new MetadataTranscoder
  private val encoder = new metadataTranscoder.Encoder
  private val decoder = new metadataTranscoder.Decoder

  private val royaltyMap: mutable.LinkedHashMap[Address, Int] =
    mutable.LinkedHashMap()

  collectionFromJson.royalty.foreach { item =>
    royaltyMap += (Address.create(item.address) -> item.amount.round.toInt)
  }

  private val encodedRoyalty =
    encoder.encodeRoyalty(royaltyMap)

  private def issuerTxn(
      stateBox: InputBox,
      proxyInput: InputBox
  ): SignedTransaction = {

    val mintUtil = new testMint.mintUtility(
      ctx = this.ctx,
      txOperatorMnemonic = walletMnemonic,
      txOperatorMnemonicPw = walletMnemonicPw
    )

    mintUtil.buildIssuerTx(
      stateBox,
      proxyInput,
      contractsConf.Contracts.issuerContract.contract,
      encodedRoyalty.asInstanceOf[ErgoValue[Coll[(Coll[Byte], Integer)]]],
      contractsConf.Contracts.collectionToken,
      issuanceTree.getMap,
      issuerTree.getMap
    )

  }

  private def nftTransaction(
      issuerBox: InputBox,
      newStateBox: InputBox,
      buyerAddress: Address
  ): SignedTransaction = {

    val mintUtil = new testMint.mintUtility(
      ctx = this.ctx,
      txOperatorMnemonic = walletMnemonic,
      txOperatorMnemonicPw = walletMnemonicPw
    )

    mintUtil.buildNFTBox(
      issuerBox,
      newStateBox,
      buyerAddress,
      issuanceTree.getMap
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
        println("Last StateBox Detected as input exiting...")
        return
      }

      val issuerTxn = this.issuerTxn(stateBoxInput, proxyInput)

      println(txHelper.sendTx(issuerTxn))

      val r4 = proxyInput.getRegisters.get(0).toHex
      val prop =
        ErgoValue.fromHex(r4).getValue.asInstanceOf[special.sigma.SigmaProp]
      val proxySender = new org.ergoplatform.appkit.SigmaProp(prop)
        .toAddress(this.ctx.getNetworkType)

      if (issuerTxn.getOutputsToSpend.size() == 3) {
        println("Sale has ended exiting function...")
        return
      }

      val nftTxn = this.nftTransaction(
        issuerTxn.getOutputsToSpend.get(0),
        issuerTxn.getOutputsToSpend.get(1),
        proxySender //should be extracted from r4 of input
      )

      println(txHelper.sendTx(nftTxn))
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
    println(boxes)

    for (box <- boxes) {
      counter += 1
      val validBox = (box.getRegisters.size() == 2) && (box.getRegisters
        .get(0)
        .getType
        .getRType
        .name == "SigmaProp") && (box.getRegisters
        .get(1)
        .getValue
        .asInstanceOf[Coll[Byte]]
        .toArray sameElements new ErgoToken(
        contractsConf.Contracts.stateContract.singleton,
        1
      ).getId.getBytes)

      if (validBox) {
        val proxyBox = exp.getUnspentBoxFromMempool(box.getId.toString)
        val tokenIDFromRegister = proxyBox.getRegisters
          .get(1)
          .getValue
          .asInstanceOf[Coll[Byte]]
          .toArray
        val tokenID = new ErgoToken(tokenIDFromRegister, 1).getId.toString
        val stateBox = exp.getUnspentBoxFromTokenID(tokenID)

        val lastStateBox: Boolean = {
          stateBox.getAssets.size() == 1 && stateBox.getAdditionalRegisters
            .size() == 3
        }

        if (!lastStateBox) {
          validProxyBoxes.add(proxyBox)
        }

      }

    }

    if (validProxyBoxes.isEmpty) {
      return
    }

    val tokenIDFromRegister = new ErgoToken(
      contractsConf.Contracts.stateContract.singleton,
      1
    ).getId.getBytes
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
