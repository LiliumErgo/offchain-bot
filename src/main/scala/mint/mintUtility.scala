package mint

import AVL.IssuerBox.IssuerValue
import AVL.NFT.{IndexKey, IssuanceValueAVL}
import io.getblok.getblok_plasma.collections.PlasmaMap
import org.ergoplatform.ErgoBox.R9
import org.ergoplatform.ErgoScriptPredef
import org.ergoplatform.appkit.{ErgoValue, _}
import org.ergoplatform.appkit.impl.Eip4TokenBuilder
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import scorex.crypto.encode.Base16
import sigmastate.eval.Colls
import special.collection.Coll
import utils.{MetadataTranscoder, OutBoxes, TransactionHelper, explorerApi}

import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class mintUtility(
    val ctx: BlockchainContext,
    txOperatorMnemonic: String,
    txOperatorMnemonicPw: String,
    minerFee: Long,
    minerFeeAndMinBoxValueSum: Long,
    liliumFee: Long,
    txOperatorFee: Long,
    paymentTokenAmount: Long,
    priceOfNFTNanoErg: Long,
    artistAddress: Address,
    liliumFeeAddress: Address
) {
  private val txPropBytes =
    Base16.decode(ErgoScriptPredef.feeProposition(720).bytesHex).get
  private val api = new explorerApi(
    DefaultNodeInfo(ctx.getNetworkType).explorerUrl
  )
  private val outBoxObj = new OutBoxes(ctx)
  private val txHelper = new TransactionHelper(
    ctx = ctx,
    walletMnemonic = txOperatorMnemonic,
    mnemonicPassword = txOperatorMnemonicPw
  )

  private val metadataTranscoder = new MetadataTranscoder
  private val encoder = new metadataTranscoder.Encoder
  private val decoder = new metadataTranscoder.Decoder

  val artistAddressConstant = 7
  val minerFeeConstant = 59
  val collectionTokenConstant = 3
  val liliumFeeAddressConstant = 148
  val liliumFeeValueConstant = 147
  val priceOfNFTNanoErgConstant = 82
  val paymentTokenAmountConstant = 65
  val txOperatorFeeConstant = 64
  val minBoxValueConstant = 51

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

  def buildIssuerTx(
      boxWithCollectionTokens: InputBox, //use singleton from db to get box
      LPBox: InputBox,
      proxyValue: Long,
      stateBoxBooleans: Array[Boolean],
      tokenArray: Array[Array[Byte]],
      proxyInput: InputBox, // get from api
      issuerContract: ErgoContract, // constant
      encodedRoyalty: ErgoValue[ // create from db
        Coll[(Coll[Byte], Integer)]
      ],
      issuanceTree: PlasmaMap[IndexKey, IssuanceValueAVL], // create from db
      issuerTree: PlasmaMap[IndexKey, IssuerValue] // create from db
  ): SignedTransaction = {
    val inputs: ListBuffer[InputBox] = new ListBuffer[InputBox]()
    val inputValue: ListBuffer[Long] = new ListBuffer[Long]()
    val outValue: ListBuffer[Long] = new ListBuffer[Long]()
    val inputValueIdeal: ListBuffer[Long] = new ListBuffer[Long]()
    val input0: InputBox = boxWithCollectionTokens
//    println("proxyInput: " + proxyInput.getId.toString)

    val r4 = proxyInput.getRegisters.get(0).toHex
    val prop =
      ErgoValue.fromHex(r4).getValue.asInstanceOf[special.sigma.SigmaProp]

    val proxySender = new org.ergoplatform.appkit.SigmaProp(prop)
      .toAddress(this.ctx.getNetworkType)

    val r6 =
      input0.getRegisters.get(2).getValue.asInstanceOf[Long] // metadata index
    val r7 =
      input0.getRegisters
        .get(3)
        .getValue
        .asInstanceOf[(Long, Long)] // sale start and end time stamps

    val returnCollectionTokensToArtist = stateBoxBooleans.head

    val stateContract = Address
      .fromPropositionBytes(
        ctx.getNetworkType,
        input0.toErgoValue.getValue.propositionBytes.toArray
      )
      .toErgoContract

    val LPContract: ErgoContract = {
      if (LPBox != null) {

        println("LP Box Value: " + LPBox.getValue)
        Address
          .fromPropositionBytes(
            ctx.getNetworkType,
            LPBox.toErgoValue.getValue.propositionBytes.toArray
          )
          .toErgoContract
      } else {
        null
      }
    }

    val hasSaleStarted: Boolean = r7._1 < System.currentTimeMillis()

    val hasSaleEnded: Boolean = {
      if (r7._2 == -1L) {
        false
      } else {
        r7._2 <= System.currentTimeMillis()
      }
    }

    val mockCollectionToken =
      try {
        new ErgoToken( // called mock since value is not accurate, we just want the token methods
          stateContract.getErgoTree
            .constants(collectionTokenConstant)
            .value
            .asInstanceOf[Coll[Byte]]
            .toArray,
          1
        )
      } catch {
        case _: Exception =>
          throw new ErgoScriptConstantDecodeError(
            "Error decoding collection token"
          )
      }

//    val mockCollectionToken: ErgoToken =
//      new ErgoToken( // called mock since value is not accurate, we just want the token methods
//        "16ad60a42cec3230e1d4c82ea0e6e56f29778452e48c0245888d8b88da068f06",
//        1
//      )

    val collectionIssuerBox =
      api.getErgoBoxfromID(mockCollectionToken.getId.toString)

    val collectionMaxSize =
      collectionIssuerBox.additionalRegisters(R9).value.asInstanceOf[Long]

//    println("Collection Max Size: " + collectionMaxSize)
//
//    println("State Box Contract: " + stateContract.toAddress.toString)

    val whitelistToken = {
      if (stateBoxBooleans(1)) {
        new ErgoToken(tokenArray.head, 1)
      } else {
        null
      }
    }

    val preMintToken = {
      if (stateBoxBooleans(3)) {
        new ErgoToken(tokenArray(1), 1)
      } else {
        null
      }
    }

    val paymentToken = {
      if (stateBoxBooleans(4)) {
        new ErgoToken(tokenArray(2), paymentTokenAmount)
      } else {
        null
      }
    }

    val stateBoxSingleton = input0.getTokens.get(0)

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

    inputs.append(
      input0.withContextVars(
        ContextVar.of(
          0.toByte,
          issuerTree.lookUp(IndexKey(r6)).proof.ergoValue
        ),
        ContextVar.of(
          1.toByte,
          collectionIssuerBox
        )
      )
    )
    inputs.append(
      proxyInput
    )

    if (LPBox != null) {
      inputs.append(LPBox)
    }

    for (input <- inputs) {
      inputValue.append(input.getValue)
    }

//    println("Input Value: " + inputValue.sum)
//    inputValueIdeal.append(input0.getValue)
//    inputValueIdeal.append(proxyInput.getValue)
//    val buyerAmountPaid = 0.003
//    inputValueIdeal.append((buyerAmountPaid * math.pow(10, 9).toLong).toLong)

    println(
      "Processing Collection: " + stateBoxSingleton.getId.toString + " " + (r6 + 1) + "/" + collectionMaxSize
    )
    println("Buyer Box ID: " + proxyInput.getId.toString)

//    println("r6: " + r6)

    val response = issuerTree.lookUp(IndexKey(r6)).response.head.get
    val decodedMetadata = decoder.decodeMetadata(response.metaData)
    val explicit = response.explicit

    val encodedMetadata = encoder.encodeMetaData(
      decodedMetadata(0).asInstanceOf[mutable.LinkedHashMap[String, String]],
      decodedMetadata(1)
        .asInstanceOf[mutable.LinkedHashMap[String, (Int, Int)]],
      decodedMetadata(2).asInstanceOf[mutable.LinkedHashMap[String, (Int, Int)]]
    )

//    println(
//      decodedMetadata(0).asInstanceOf[mutable.LinkedHashMap[String, String]]
//    )
//    println(
//      decodedMetadata(1).asInstanceOf[mutable.LinkedHashMap[String, (Int, Int)]]
//    )
//    println(
//      decodedMetadata(2).asInstanceOf[mutable.LinkedHashMap[String, (Int, Int)]]
//    )

    val encodedExplicit = {
      val explicitValue = if (explicit) 1.toByte else 0.toByte
      ErgoValueBuilder.buildFor(
        Colls.fromArray(
          Array(
            (
              Colls.fromArray("explicit".getBytes(StandardCharsets.UTF_8)),
              Colls.fromArray(Array(explicitValue))
            )
          )
        )
      )
    }

    val issuerRegisters: Array[ErgoValue[_]] = Array(
      ErgoValue.of(2),
      encodedRoyalty,
      encodedMetadata,
      ErgoValue.of(mockCollectionToken.getId.getBytes),
      encodedExplicit,
      ErgoValueBuilder.buildFor(
        (ErgoValue.of(proxySender.getPublicKey).getValue, r6)
      )
    )

    val issuerOutBox =
      outBoxObj.buildIssuerBox(
        issuerContract,
        issuerRegisters,
        new ErgoToken(input0.getTokens.get(1).getId.toString, 1),
        convertERGLongToDouble(minerFeeAndMinBoxValueSum)
      )

    val newStateBox: OutBox = {
      if (r6 + 1L == collectionMaxSize) { //last sale outbox
        println("Last Sale")
//        println("r6 + 1L = " + r6 + 1L)
//        println("collectionMaxSize: " + collectionMaxSize)
        outBoxObj.lastStateBox(
          stateContract,
          issuanceTree,
          issuerTree,
          stateBoxSingleton,
          r6 + 1L,
          convertERGLongToDouble(input0.getValue)
        )
      } else if (hasSaleEnded) { //sale expiry outbox
        println("Sale Has Expired")
        if (LPBox != null) {
          inputs.remove(2) //remove LP input
        }
        inputs.remove(1) //remove proxy input

        if (returnCollectionTokensToArtist) {
          println("Collection Tokens are being returned to artist")
          outBoxObj.saleExpiryOutbox(
            artistAddress,
            input0.getTokens.get(1)
          )
        } else {
          println("Tokens are being burned!")
          outBoxObj.burnSaleExpiryOutbox(
            artistAddress
          )
        }

      } else {

        outBoxObj.buildStateBox(
          stateContract,
          issuanceTree,
          issuerTree,
          new ErgoToken(input0.getTokens.get(0).getId.toString, 1L),
          new ErgoToken(
            input0.getTokens.get(1).getId.toString,
            input0.getTokens.get(1).getValue - 1
          ),
          r6 + 1L,
          r7._1,
          r7._2,
          stateBoxBooleans,
          preMintToken,
          whitelistToken,
          paymentToken,
          convertERGLongToDouble(input0.getValue)
        )
      }
    }

    val paymentBox: OutBox = {

      def hasToken(
          inputTokens: mutable.Buffer[ErgoToken],
          token: ErgoToken
      ): Boolean = {
        if (token == null) {
          false
        } else {
          inputTokens.count(t => t.getId.toString == token.getId.toString) > 0
        }
      }

      val proxyTokens = proxyInput.getTokens.asScala
      val premintPhase = !hasSaleStarted

      // Return respective payout boxes based on conditions

      if (whitelistAccepted && hasToken(proxyTokens, whitelistToken)) {
        outBoxObj.artistTokenPayoutBox(
          whitelistToken,
          artistAddress
        )
      } else if (
        premintAccepted && hasToken(
          proxyTokens,
          preMintToken
        ) && paymentTokenAccepted && hasToken(
          proxyTokens,
          paymentToken
        ) && premintPhase
      ) {
        outBoxObj.artistTokensPayoutBox(
          List(preMintToken, paymentToken),
          artistAddress
        )
      } else if (
        premintAccepted && hasToken(proxyTokens, preMintToken) && premintPhase
      ) {
        outBoxObj.artistTokenPayoutBox(
          preMintToken,
          artistAddress,
          convertERGLongToDouble(priceOfNFTNanoErg)
        )
      } else if (paymentTokenAccepted && hasToken(proxyTokens, paymentToken)) {
        val tokenList = { // sends premint token to artist even if its after premint phase
          if (hasToken(proxyTokens, preMintToken)) {
            List(paymentToken, preMintToken)
          } else {
            List(paymentToken)
          }
        }
        outBoxObj.artistTokensPayoutBox(
          tokenList,
          artistAddress
        )
      } else { // assumes erg is accepted
        if (hasToken(proxyTokens, preMintToken)) {
          // sends premint token to artist even if its after premint phase
          outBoxObj.artistTokenPayoutBox(
            preMintToken,
            artistAddress,
            convertERGLongToDouble(priceOfNFTNanoErg)
          )
        } else {
          outBoxObj.artistPayoutBox(
            artistAddress,
            convertERGLongToDouble(priceOfNFTNanoErg)
          )
        }
      }
    }

//    println("Lilium Fee: " + liliumFee)

    val liliumBox: OutBox = outBoxObj.artistPayoutBox(
      liliumFeeAddress,
      convertERGLongToDouble(liliumFee)
    )

    val newLPBox: OutBox = {
      if (LPBox == null) {
        null
      } else {
        println("Proxy Value: " + proxyValue)

        if ((LPBox.getValue - proxyValue) == 0L) {
          null
        } else {
          outBoxObj.newLPBox(
            LPContract,
            stateBoxSingleton,
            artistAddress,
            LPBox.getRegisters.get(1).getValue.asInstanceOf[Long] - 1,
            convertERGLongToDouble(LPBox.getValue - proxyValue)
          )
        }
      }
    }

    if (newLPBox != null) {
      println("new LP Box Value (output): " + newLPBox.getValue)
    }

    val OutBox: List[OutBox] = {
      if (hasSaleEnded) {
        List(newStateBox)
      } else if (newLPBox != null) {
        List(issuerOutBox, newStateBox, paymentBox, liliumBox, newLPBox)
      } else {
        List(issuerOutBox, newStateBox, paymentBox, liliumBox)
      }
    }

//    OutBox.foreach(o => println(o.getValue))

    OutBox.foreach(o => outValue.append(o.getValue))

//    println("Output Value: " + outValue.sum)

    val unsignedTx: UnsignedTransaction = {
      if (hasSaleEnded) {

        if (returnCollectionTokensToArtist) { // only burn singleton
          txHelper.buildUnsignedTransactionWithTokensToBurn(
            inputs.asJava,
            OutBox,
            List(
              input0.getTokens.get(0)
            ),
            convertERGLongToDouble(minerFee)
          )
        } else { // burn singleton and collection token
          txHelper.buildUnsignedTransactionWithTokensToBurn(
            inputs.asJava,
            OutBox,
            List(
              input0.getTokens.get(1),
              input0.getTokens.get(0)
            ),
            convertERGLongToDouble(minerFee)
          )
        }
      } else { //normal transaction
        txHelper.buildUnsignedTransaction(
          inputs.asJava,
          OutBox,
          convertERGLongToDouble(minerFee)
        )
      }
    }
    val signedTransaction = txHelper.signTransaction(unsignedTx)
    signedTransaction
  }

  def buildNFTBox(
      issuerBox: InputBox,
      newStateBox: InputBox,
      buyerAddress: Address,
      issuanceTree: PlasmaMap[IndexKey, IssuanceValueAVL] //grab from db
  ): SignedTransaction = {
    val r6: Long = issuerBox.getRegisters
      .get(5)
      .getValue
      .asInstanceOf[(SigmaProp, Long)]
      ._2
    val nftDataFromAVLTree = issuanceTree.lookUp(IndexKey(r6))
    val inputs: ListBuffer[InputBox] = new ListBuffer[InputBox]()
    val dataInputs: List[InputBox] = List(newStateBox)
    val tokensToBurn: List[ErgoToken] = List(issuerBox.getTokens.get(0))
    val inputValue: ListBuffer[Long] = new ListBuffer[Long]()
    val inputValueIdeal: ListBuffer[Long] = new ListBuffer[Long]()
    inputs.append(
      issuerBox.withContextVars(
        ContextVar.of(
          0.toByte,
          nftDataFromAVLTree.proof.ergoValue
        )
      )
    )

    val nft = Eip4TokenBuilder.buildNftPictureToken(
      issuerBox.getId.toString,
      1,
      nftDataFromAVLTree.response.head.get.name,
      nftDataFromAVLTree.response.head.get.description,
      0,
      nftDataFromAVLTree.response.head.get.sha256,
      nftDataFromAVLTree.response.head.get.link
    )

    val stateContract = Address
      .fromPropositionBytes(
        ctx.getNetworkType,
        newStateBox.toErgoValue.getValue.propositionBytes.toArray
      )
      .toErgoContract

    val outputs = List(outBoxObj.nftOutBox(buyerAddress, nft))

    val unsignedTx =
      txHelper.buildUnsignedTransactionWithDataInputsWithTokensToBurn(
        inputs.asJava,
        outputs,
        dataInputs.asJava,
        tokensToBurn,
        convertERGLongToDouble(minerFee)
      )

    txHelper.signTransaction(unsignedTx)
  }

}
