//package mock
//
//import AVL.Debug.{AVLDebug, AVLDebugHelpers}
//import AVL.NFT.IndexKey
//import contracts.ErgoSapiensContracts
//import mint.Client
//
//import scala.collection.JavaConversions._
//import scala.collection.mutable.ListBuffer
//import org.ergoplatform.appkit.{
//  Address,
//  BlockchainContext,
//  ConstantsBuilder,
//  ContextVar,
//  ErgoContract,
//  ErgoToken,
//  InputBox
//}
//import utils.{InputBoxes, OutBoxes, TransactionHelper}
//
//import java.util
//import scala.collection.mutable.ListBuffer
//
//class AVLDebugMain(
//    ctx: BlockchainContext,
//    walletMnemonic: String,
//    walletMnemonicPw: String
//) {
//
//  private val outBoxObj = new OutBoxes(ctx)
//  private val txHelper =
//    new TransactionHelper(ctx, walletMnemonic, walletMnemonicPw)
//  private val input =
//    new InputBoxes(ctx).getInputs(List(0.004), txHelper.senderAddress)
//
//  def compileContract(
//      contract: String
//  ): ErgoContract = {
//    this.ctx.compileContract(
//      ConstantsBuilder
//        .create()
//        .build(),
//      contract
//    )
//  }
//
//  private val contract = compileContract(
//    ErgoSapiensContracts.AVLdebug.contractScript
//  )
//
//  private val tree = new AVLDebugHelpers("Debug/data")
//  private val insert =
//    tree.insertMetaData(IndexKey(1L), AVLDebug.createMetadata("debug", "hello"))
//
//  private val out = List(
//    outBoxObj.AVLDebugBox(contract, tree.getMap, 1L, 0.003)
//  )
//
//  private val proofFromInsert = insert.proof
//  private val proofFromLookUp = tree.getProof(tree.lookUp(IndexKey(1L)))
//
//  println("proof from insert: " + proofFromInsert)
//
//  println("proof from look up: " + proofFromLookUp)
//
//  println("Value: " + tree.lookUp(IndexKey(1L)).response.head.get)
//
////  private val signedAVLCreationBox =
////    txHelper.signTransaction(txHelper.buildUnsignedTransaction(input, out))
////  val inputs: ListBuffer[InputBox] = new ListBuffer[InputBox]()
////  inputs.append(
////    signedAVLCreationBox.getOutputsToSpend
////      .get(0)
////      .withContextVars(
////        ContextVar
////          .of(0.toByte, tree.getProof(tree.lookUp(IndexKey(1L))).ergoValue)
////      )
////  )
////
////  println(signedAVLCreationBox)
////
////  private val spendingOutBox =
////    List(outBoxObj.AVLDebugBoxSpending(txHelper.senderAddress, "hello"))
////  private val signedSpendingTx =
////    txHelper.signTransaction(
////      txHelper.buildUnsignedTransaction(inputs, spendingOutBox)
////    )
////
////  println(signedSpendingTx)
//
//}
//
//object AVLDebugMain extends App {
//  val client: Client = new Client()
//  client.setClient
//  val ctx = client.getContext
//  val walletMnemonic =
//    "rebuild mixture present box elevator barely until best sock float kite reject island same flag"
//  val walletMnemonicPw = ""
//
//  new AVLDebugMain(ctx, walletMnemonic, walletMnemonicPw)
//}
