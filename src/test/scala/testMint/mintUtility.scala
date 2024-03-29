//package testMint
//
//import AVL.IssuerBox.IssuerValue
//import AVL.NFT.{IndexKey, IssuanceValueAVL}
//import io.getblok.getblok_plasma.collections.{LocalPlasmaMap, PlasmaMap}
//import mint.DefaultNodeInfo
//import org.ergoplatform.ErgoBox.R9
//import org.ergoplatform.ErgoScriptPredef
//import org.ergoplatform.appkit._
//import org.ergoplatform.appkit.impl.Eip4TokenBuilder
//import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
//import scorex.crypto.encode.Base16
//import sigmastate.eval.Colls
//import special.collection.Coll
//import utils.{MetadataTranscoder, OutBoxes, TransactionHelper, explorerApi}
//
//import java.util
//import scala.collection.JavaConverters._
//import scala.collection.mutable
//import scala.collection.mutable.ListBuffer
//
//class mintUtility(
//    val ctx: BlockchainContext,
//    txOperatorMnemonic: String,
//    txOperatorMnemonicPw: String
//) {
//  private val txPropBytes =
//    Base16.decode(ErgoScriptPredef.feeProposition(720).bytesHex).get
//  private val api = new explorerApi(
//    DefaultNodeInfo(ctx.getNetworkType).explorerUrl
//  )
//  private val outBoxObj = new OutBoxes(ctx)
//  private val txHelper = new TransactionHelper(
//    ctx = ctx,
//    walletMnemonic = txOperatorMnemonic,
//    mnemonicPassword = txOperatorMnemonicPw
//  )
//
//  private val metadataTranscoder = new MetadataTranscoder
//  private val encoder = new metadataTranscoder.Encoder
//  private val decoder = new metadataTranscoder.Decoder
//
//  def convertERGLongToDouble(num: Long): Double = {
//    val value = num * math.pow(10, -9)
//    val x =
//      (math floor value * math.pow(10, num.toString.length + 2)) / math.pow(
//        10,
//        num.toString.length + 2
//      )
//    val bNum = math.BigDecimal(x)
//    val finalNum = bNum.underlying().stripTrailingZeros()
//    finalNum.toString.toDouble
//  }
//
//  def buildIssuerTx(
//      boxWithCollectionTokens: InputBox, //use singleton from db to get box
//      proxyInput: InputBox, // get from api
//      issuerContract: String, // constant
//      encodedRoyalty: ErgoValue[ // create from db
//        Coll[(Coll[Byte], Integer)]
//      ],
//      collectionTokenId: String,
//      issuanceTree: PlasmaMap[
//        IndexKey,
//        IssuanceValueAVL
//      ], // create from db
//      issuerTree: PlasmaMap[IndexKey, IssuerValue] // create from db
//  ): SignedTransaction = {
//    val inputs: ListBuffer[InputBox] = new ListBuffer[InputBox]()
//    val inputValue: ListBuffer[Long] = new ListBuffer[Long]()
//    val outValue: ListBuffer[Long] = new ListBuffer[Long]()
//    val inputValueIdeal: ListBuffer[Long] = new ListBuffer[Long]()
//    val input0: InputBox = boxWithCollectionTokens
////    println("proxyInput: " + proxyInput.getId.toString)
//
//    val r4 = proxyInput.getRegisters.get(0).toHex
//    val prop =
//      ErgoValue.fromHex(r4).getValue.asInstanceOf[special.sigma.SigmaProp]
//
//    val proxySender = new org.ergoplatform.appkit.SigmaProp(prop)
//      .toAddress(this.ctx.getNetworkType)
//
//    val r6 =
//      input0.getRegisters.get(2).getValue.asInstanceOf[Long] // metadata index
//    val r7 =
//      input0.getRegisters
//        .get(3)
//        .getValue
//        .asInstanceOf[(Long, Long)] // sale start and end time stamps
//    val returnCollectionTokensToArtist =
//      input0.getRegisters
//        .get(4)
//        .getValue
//        .asInstanceOf[Boolean] // returnCollectionTokensToArtist
//
//    val stateContract = Address
//      .fromPropositionBytes(
//        ctx.getNetworkType,
//        input0.toErgoValue.getValue.propositionBytes.toArray
//      )
//      .toErgoContract
//
//    val hasSaleEnded: Boolean = {
//      if (r7._2 == -1L) {
//        false
//      } else {
//        r7._2 <= System.currentTimeMillis()
//      }
//    }
//
////    val minerFee = stateContract.getErgoTree
////      .constants(38)
////      .value
////      .asInstanceOf[Long]
//
//    val minerFee = 1700000
//
////    val mockCollectionToken: ErgoToken =
////      new ErgoToken( // called mock since value is not accurate, we just want the token methods
////        stateContract.getErgoTree
////          .constants(1)
////          .value
////          .asInstanceOf[Coll[Byte]]
////          .toArray,
////        1
////      )
//
//    val mockCollectionToken: ErgoToken =
//      new ErgoToken( // called mock since value is not accurate, we just want the token methods
//        collectionTokenId,
//        1
//      )
//
//    val collectionIssuerBox =
//      api.getErgoBoxfromID(mockCollectionToken.getId.toString)
//
//    val collectionMaxSize =
//      collectionIssuerBox.additionalRegisters(R9).value.asInstanceOf[Long]
//
//    println("Collection Max Size: " + collectionMaxSize)
//
//    println("State Box Contract: " + stateContract.toAddress.toString)
//
//    inputs.append(
//      input0.withContextVars(
//        ContextVar.of(
//          0.toByte,
//          issuerTree.lookUp(IndexKey(r6)).proof.ergoValue
//        ),
//        ContextVar.of(
//          1.toByte,
//          collectionIssuerBox
//        )
//      )
//    )
//    inputs.append(
//      proxyInput
//    )
//
//    for (input <- inputs) {
//      inputValue.append(input.getValue)
//    }
//
//    println("Input Value: " + inputValue.sum)
////    inputValueIdeal.append(input0.getValue)
////    inputValueIdeal.append(proxyInput.getValue)
////    val buyerAmountPaid = 0.003
////    inputValueIdeal.append((buyerAmountPaid * math.pow(10, 9).toLong).toLong)
//
//    println("r6: " + r6)
//
//    val decodedMetadata = decoder.decodeMetadata(
//      issuerTree.lookUp(IndexKey(r6)).response.head.get.metaData
//    )
//    val encodedMetadata = encoder.encodeMetaData(
//      decodedMetadata(1).asInstanceOf[mutable.LinkedHashMap[String, String]],
//      decodedMetadata(2)
//        .asInstanceOf[mutable.LinkedHashMap[String, (Int, Int)]],
//      decodedMetadata(3).asInstanceOf[mutable.LinkedHashMap[String, (Int, Int)]]
//    )
//
//    println(
//      decodedMetadata(0).asInstanceOf[mutable.LinkedHashMap[String, String]]
//    )
//    println(
//      decodedMetadata(1).asInstanceOf[mutable.LinkedHashMap[String, (Int, Int)]]
//    )
//    println(
//      decodedMetadata(2).asInstanceOf[mutable.LinkedHashMap[String, (Int, Int)]]
//    )
//
//    val emptyArray = new util.ArrayList[Coll[Byte]]()
//
//    val issuerRegisters: Array[ErgoValue[_]] = Array(
//      ErgoValue.of(2),
//      encodedRoyalty,
//      encodedMetadata,
//      ErgoValue.of(mockCollectionToken.getId.getBytes),
//      ErgoValueBuilder.buildFor(
//        Colls.fromArray(emptyArray.asScala.toArray)
//      ),
//      ErgoValueBuilder.buildFor(
//        (ErgoValue.of(proxySender.getPublicKey).getValue, r6)
//      )
//    )
//
//    val issuerOutBox =
//      outBoxObj.buildIssuerBox(
//        Address.create(issuerContract).toErgoContract,
//        issuerRegisters,
//        new ErgoToken(input0.getTokens.get(1).getId.toString, 1),
//        0.001 + convertERGLongToDouble(minerFee)
//      )
//
////    val artistAddress: Address = new org.ergoplatform.appkit.SigmaProp(
////      stateContract.getErgoTree
////        .constants(5)
////        .value
////        .asInstanceOf[special.sigma.SigmaProp]
////    ).toAddress(ctx.getNetworkType)
//
//    val artistAddress =
//      Address.create("3WwnvL9PBXHyR72UyUJqbqAxH1gFT7kbWmivgVXfhUV362i76qRY")
//
//    val newStateBox: OutBox = {
//      if (r6 + 1L == collectionMaxSize) { //last sale outbox
//        println("Last Sale")
//        println("r6 + 1L = " + r6 + 1L)
//        println("collectionMaxSize: " + collectionMaxSize)
//        outBoxObj.lastStateBox(
//          stateContract,
//          issuanceTree,
//          issuerTree,
//          new ErgoToken(input0.getTokens.get(0).getId.toString, 1L),
//          r6 + 1L,
//          convertERGLongToDouble(input0.getValue)
//        )
//      } else if (hasSaleEnded) { //sale expiry outbox
//        println("Sale Has Expired")
//        inputs.remove(1) //remove proxy input
//
//        if (returnCollectionTokensToArtist) {
//          println("Collection Tokens are being returned to artist")
//          outBoxObj.saleExpiryOutbox(
//            artistAddress,
//            new ErgoToken(
//              input0.getTokens.get(1).getId.toString,
//              input0.getTokens.get(1).getValue
//            )
//          )
//        } else {
//          println("Tokens are being burned!")
//          outBoxObj.burnSaleExpiryOutbox(
//            artistAddress
//          )
//        }
//
//      } else {
//        outBoxObj.buildStateBox(
//          stateContract,
//          issuanceTree,
//          issuerTree,
//          new ErgoToken(input0.getTokens.get(0).getId.toString, 1L),
//          new ErgoToken(
//            input0.getTokens.get(1).getId.toString,
//            input0.getTokens.get(1).getValue - 1
//          ),
//          r6 + 1L,
//          r7._1,
//          r7._2,
//          returnCollectionTokensToArtist,
//          convertERGLongToDouble(input0.getValue)
//        )
//      }
//    }
//
////    val priceOfNFTNanoErg: Long = stateContract.getErgoTree
////      .constants(29)
////      .value
////      .asInstanceOf[Long]
//
//    val priceOfNFTNanoErg: Long = 2000000
//
//    val paymentBox: OutBox = outBoxObj.artistPayoutBox(
//      artistAddress,
//      convertERGLongToDouble(priceOfNFTNanoErg)
//    )
//
////    val liliumFee = stateContract.getErgoTree
////      .constants(30)
////      .value
////      .asInstanceOf[Long]
//
//    val liliumFee = 5000000
//
////    val liliumFeeAddress = new org.ergoplatform.appkit.SigmaProp(
////      stateContract.getErgoTree
////        .constants(31)
////        .value
////        .asInstanceOf[special.sigma.SigmaProp]
////    )
////      .toAddress(this.ctx.getNetworkType)
//
//    val liliumFeeAddress =
//      Address.create("3WxR2UxZihv7NTYkUj5U6Pzg1K3UTAip6oDCwDcn1AjDws49WDYg")
//
//    val liliumBox: OutBox = outBoxObj.artistPayoutBox(
//      liliumFeeAddress,
//      convertERGLongToDouble(liliumFee)
//    )
//
//    val OutBox: List[OutBox] = {
//      if (hasSaleEnded) {
//        List(newStateBox)
//      } else {
//        List(issuerOutBox, newStateBox, paymentBox, liliumBox)
//      }
//    }
//
//    OutBox.foreach(o => outValue.append(o.getValue))
//
//    println("Output Value: " + outValue.sum)
//
//    val unsignedTx: UnsignedTransaction = {
//      if (hasSaleEnded) {
//
//        if (returnCollectionTokensToArtist) { // only burn singleton
//          txHelper.buildUnsignedTransactionWithTokensToBurn(
//            inputs.asJava,
//            OutBox,
//            List(
//              new ErgoToken(
//                input0.getTokens.get(0).getId.toString,
//                1L
//              )
//            ),
//            convertERGLongToDouble(minerFee)
//          )
//        } else { // burn singleton and collection token
//          txHelper.buildUnsignedTransactionWithTokensToBurn(
//            inputs.asJava,
//            OutBox,
//            List(
//              new ErgoToken(
//                input0.getTokens.get(1).getId.toString,
//                input0.getTokens.get(1).getValue
//              ),
//              new ErgoToken(
//                input0.getTokens.get(0).getId.toString,
//                1L
//              )
//            ),
//            convertERGLongToDouble(minerFee)
//          )
//        }
//      } else { //normal transaction
//        txHelper.buildUnsignedTransaction(
//          inputs.asJava,
//          OutBox,
//          convertERGLongToDouble(minerFee)
//        )
//      }
//    }
//    txHelper.signTransaction(unsignedTx)
//  }
//
//  def buildNFTBox(
//      issuerBox: InputBox,
//      newStateBox: InputBox,
//      buyerAddress: Address,
//      issuanceTree: PlasmaMap[IndexKey, IssuanceValueAVL] //grab from db
//  ): SignedTransaction = {
//    val r6: Long = issuerBox.getRegisters
//      .get(5)
//      .getValue
//      .asInstanceOf[(SigmaProp, Long)]
//      ._2
//    val nftDataFromAVLTree = issuanceTree.lookUp(IndexKey(r6))
//    val inputs: ListBuffer[InputBox] = new ListBuffer[InputBox]()
//    val dataInputs: List[InputBox] = List(newStateBox)
//    val tokensToBurn: List[ErgoToken] = List(issuerBox.getTokens.get(0))
//    val inputValue: ListBuffer[Long] = new ListBuffer[Long]()
//    val inputValueIdeal: ListBuffer[Long] = new ListBuffer[Long]()
//    inputs.append(
//      issuerBox.withContextVars(
//        ContextVar.of(
//          0.toByte,
//          nftDataFromAVLTree.proof.ergoValue
//        )
//      )
//    )
//
//    val nft = Eip4TokenBuilder.buildNftPictureToken(
//      issuerBox.getId.toString,
//      1,
//      nftDataFromAVLTree.response.head.get.name,
//      nftDataFromAVLTree.response.head.get.description,
//      0,
//      nftDataFromAVLTree.response.head.get.sha256,
//      nftDataFromAVLTree.response.head.get.link
//    )
//
//    val stateContract = Address
//      .fromPropositionBytes(
//        ctx.getNetworkType,
//        newStateBox.toErgoValue.getValue.propositionBytes.toArray
//      )
//      .toErgoContract
//
////    val minerFee = stateContract.getErgoTree
////      .constants(38)
////      .value
////      .asInstanceOf[Long]
//
//    val minerFee = 1700000
//
//    val outputs = List(outBoxObj.nftOutBox(buyerAddress, nft))
//
//    val unsignedTx =
//      txHelper.buildUnsignedTransactionWithDataInputsWithTokensToBurn(
//        inputs.asJava,
//        outputs,
//        dataInputs.asJava,
//        tokensToBurn,
//        convertERGLongToDouble(minerFee)
//      )
//
//    txHelper.signTransaction(unsignedTx)
//  }
//
//}
