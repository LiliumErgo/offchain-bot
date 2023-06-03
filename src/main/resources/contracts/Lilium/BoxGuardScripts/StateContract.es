{
   // ===== Contract Description ===== //
   // Name: NFT State Contract
   // Description: An NFT requires the bulk of its metadata to come from an issuer box. In order to ensure the proper metadata is placed into the issuer box, this contract is used.
   //              For more information, refer to https://github.com/anon-real/eips/blob/master/eip-0024.md
   // Version: 1.0.0
   // Author: mgpai22@github.com
   // Auditor: lucagdangelo@github.com

   // ===== Box Registers ===== //
   // R4: AvlTree => Issuance Box AVL
   // R5: AvlTree => Issuer Box AVL
   // R6: Long => Index
   // R7: (Long, Long) => Sale Starting and Ending Timestamps
   // R8: Coll[Boolean] => Coll(isReturn, whitelistAccepted, whitelistBypass, premintAccepted)
   // R9: (Coll[Byte], Coll[Byte]) => (WhitelistTokenId, PreMintTokenId)

   // ===== Compile Time Constants ===== //
   // _artistSigmaProp: SigmaProp
   // _proxyContractBytes: Coll[Byte]
   // _issuerContractBytes: Coll[Byte]
   // _royaltyBlakeHash: Coll[Byte]
   // _collectionToken: Coll[Byte]
   // _singletonToken: Coll[Byte]
   // _priceOfNFT: Long
   // _liliumSigmaProp: SigmaProp
   // _liliumFeeNum: Long
   // _liliumFeeDenom: Long
   // _minerFee: Long
   // _txOperatorFee: Long

   // ===== Relevant Variables ===== //
   val hasSaleStarted: Boolean = SELF.R7[(Long, Long)].get._1 <= CONTEXT.headers(0).timestamp
   val hasSaleEnded: Boolean = SELF.R7[(Long, Long)].get._2 <= CONTEXT.headers(0).timestamp
   val isInfiniteSale: Boolean = (SELF.R7[(Long, Long)].get._2 == -1L)
   val isReturn: Boolean = SELF.R8[Coll[Boolean]].get(0)
   val whitelistAccepted: Boolean = SELF.R8[Coll[Boolean]].get(1)
   val whitelistBypass: Boolean = SELF.R8[Coll[Boolean]].get(2)
   val premintAccepted: Boolean = SELF.R8[Coll[Boolean]].get(3)
   val whitelistTokenId: Coll[Byte] = SELF.R9[(Coll[Byte], Coll[Byte])].get._1
   val premintTokenId: Coll[Byte] = SELF.R9[(Coll[Byte], Coll[Byte])].get._2

   if (hasSaleStarted || premintAccepted || whitelistBypass) {

      if (!hasSaleEnded || isInfiniteSale) {

         // ===== Relevant Variables ===== //
         val proof: Coll[Byte] = getVar[Coll[Byte]](0).get
         val collectionIssuerBox: Box = getVar[Box](1).get
         val tree: AvlTree = SELF.R5[AvlTree].get
         val index: Long = SELF.R6[Long].get // metadata index, the value for index in the init tx must be 1L, AVLTREE(0) does not exist, it starts at 1
         val key: Coll[Byte] = longToByteArray(index)
         val keyHash: Coll[Byte] = blake2b256(key)
         val avlData: Coll[Byte] = tree.get(keyHash, proof).get
         val delimiter: Coll[Byte] = fromBase16("7f000fbaf55fab7e")

         val nftSupplyCap: Long = collectionIssuerBox.R9[Long].get
         val isLastSale: Boolean = (index + 1 == nftSupplyCap)

         val validSaleTx: Boolean = {

            // inputs
            val buyerProxyBox: Box = INPUTS(1)

            // outputs
            val issuerBoxOUT: Box = OUTPUTS(0)
            val stateBoxOUT: Box = OUTPUTS(1) // Output which recreates self
            val userBoxOUT: Box = OUTPUTS(2) // Output which goes to artist
            val liliumBoxOUT: Box = OUTPUTS(3)
            val minerBoxOUT: Box = OUTPUTS(4)
            val txOperatorBoxOUT: Box = OUTPUTS(5)

            val validCollection: Boolean = (collectionIssuerBox.id == _collectionToken)

            val validSupplyCap: Boolean = (index + 1 <= nftSupplyCap)

            val validProxyBox: Boolean = (buyerProxyBox.propositionBytes == _proxyContractBytes)

            val validIssuerBox: Boolean = {

               val royalty = issuerBoxOUT.R5[Coll[(Coll[Byte], Int)]].get
               val metadata = issuerBoxOUT.R6[(Coll[(Coll[Byte],Coll[Byte])],(Coll[(Coll[Byte],(Int,Int))],Coll[(Coll[Byte],(Int,Int))]))].get
               val additionalInfo = issuerBoxOUT.R8[Coll[(Coll[Byte], Coll[Byte])]].get
               val explicit: Coll[Byte] = additionalInfo(0)._2
               val traits: Coll[(Coll[Byte],Coll[Byte])] = metadata._1
               val levels: Coll[(Coll[Byte],(Int,Int))] = metadata._2._1
               val stats: Coll[(Coll[Byte],(Int,Int))] = metadata._2._2

               val startingValue = {

                  if (explicit(0) == 0) {
                     longToByteArray(0L)
                  } else {
                     longToByteArray(1L)
                  }

               }

               val traitsBytes = (traits.fold(startingValue, { (a: Coll[Byte], b: (Coll[Byte],Coll[Byte])) => a ++ longToByteArray(b._1.size.toLong) ++ b._1 ++ longToByteArray(b._2.size.toLong) ++ b._2 })) ++ delimiter
               val levelsBytes = (levels.fold(traitsBytes, { (a: Coll[Byte], b: (Coll[Byte],(Int,Int))) => a ++ longToByteArray(b._1.size.toLong) ++ b._1 ++ longToByteArray(b._2._1.toLong) ++ longToByteArray(b._2._2.toLong) })) ++ delimiter
               val statsBytes = stats.fold(levelsBytes, { (a: Coll[Byte], b: (Coll[Byte],(Int,Int))) => a ++ longToByteArray(b._1.size.toLong) ++ b._1 ++ longToByteArray(b._2._1.toLong) ++ longToByteArray(b._2._2.toLong) })
               val royaltyBytes = royalty.fold(longToByteArray(0L), { (a: Coll[Byte], b: (Coll[Byte], Int)) => a ++ b._1 ++ longToByteArray(b._2.toLong) })

               allOf(Coll(
                  // why don't we specify the issuer box value? how are you therefore able to determine all values distributd through the protocol are correct?
                  (issuerBoxOUT.propositionBytes == _issuerContractBytes), // correct contract
                  (issuerBoxOUT.tokens(0) == (_collectionToken, 1L)), // transfer one collection token to the issuer box
                  (issuerBoxOUT.R4[Int].get == 2), // correct standard version
                  (blake2b256(royaltyBytes) == _royaltyBlakeHash), // correct royalty
                  (blake2b256(statsBytes) == blake2b256(avlData)), // correct data from avl tree
                  (issuerBoxOUT.R7[Coll[Byte]].get == _collectionToken), // collection token id stored in register of issuer box
                  (issuerBoxOUT.R9[(SigmaProp, Long)].get == (buyerProxyBox.R4[SigmaProp].get, index)) // correct buyer SigmaProp and NFT index
               ))

            }

            val validStateBox: Boolean = {

               val validTokens: Boolean = {

                  if (!isLastSale) {

                     allOf(Coll(
                        (stateBoxOUT.tokens(0) == (SELF.tokens(0)._1, 1L)), // transfer the state box singleton token
                        (stateBoxOUT.tokens(0)._1 == _singletonToken), // check that the state box singleton token is the correct one
                        (stateBoxOUT.tokens(1) == (SELF.tokens(1)._1, SELF.tokens(1)._2 - 1L)), // amount of collection tokens reduced by 1
                        (stateBoxOUT.tokens(1)._1 == _collectionToken) // check that the collection token is correct the correct one

                     ))


                  } else {

                     allOf(Coll(
                        (stateBoxOUT.tokens(0) == (SELF.tokens(0)._1, 1L)), // transfer the state box singleton token
                        (stateBoxOUT.tokens(0)._1 == _singletonToken), // check that the state box singleton token is the correct one
                        (stateBoxOUT.tokens.size == 1) // check that the singleton token is the only token left in the box
                     ))

                  }

               }

               allOf(Coll(
                  (stateBoxOUT.value == SELF.value), // transfer the box value
                  (stateBoxOUT.propositionBytes == SELF.propositionBytes), // correct contract
                  validTokens, // correct tokens
                  (stateBoxOUT.R4[AvlTree].get.digest == SELF.R4[AvlTree].get.digest), // correct issuer avl tree
                  (stateBoxOUT.R5[AvlTree].get.digest == SELF.R5[AvlTree].get.digest), // correct issuance avl tree
                  (stateBoxOUT.R6[Long].get == index + 1L), // increment nft index
                  (stateBoxOUT.R7[(Long, Long)].get == SELF.R7[(Long, Long)]), // make sure start/end times are preserved
                  (stateBoxOUT.R8[Coll[Boolean]].get == SELF.R8[Coll[Boolean]]), // make sure sale settings are preserved
                  (stateBoxOUT.R9[(Coll[Byte], Coll[Byte])].get == SELF.R9[(Coll[Byte], Coll[Byte])].get) // make sure the whitelist/premint tokens ids are preserved, these should be empty Coll[Byte] if there are no whitelist/premint tokens for the sale.
               ))

            }

            val validUserBox: Boolean = {

               // If the buyer has the accepted whitelist token, then they get the NFT for free and the artists gets the whitelist token back.
               // If premint is accepted, the buyer is able to get the NFT but for the full price.

               if (hasSaleStarted) {

                  val hasWhitelistToken: Boolean = buyerProxyBox.tokens.exists({ (t: (Coll[Byte], Long)) => t._1 == whitelistTokenId })

                  if (whitelistAccepted && hasWhitelistToken) {

                     val validWhitelistSale: Boolean = {

                        // Note: Whitelisting does NOT bypass required Lilium Fee per mint

                        allOf(Coll(
                           (userBoxOUT.tokens(0)._1 == whitelistTokenId),
                           (userBoxOUT.propositionBytes == _artistSigmaProp.propBytes)
                        ))

                     }

                     validWhitelistSale

                  } else {

                     val validNormalSale: Boolean {
                        allOf(Coll(
                           (userBoxOUT.value == _priceOfNFT),
                           (userBoxOUT.propositionBytes == _artistSigmaProp.propBytes)
                        ))
                     }

                     validNormalSale

                  }

               } else if (premintAccepted || (whitelistBypass && whitelistAccepted)) {

                  val hasWhitelistToken: Boolean = buyerProxyBox.tokens.exists({ (t: (Coll[Byte], Long)) => t._1 == whitelistTokenId })

                  val hasPremintToken: Boolean = buyerProxyBox.tokens.exists({ (t: (Coll[Byte], Long)) => t._1 == premintTokenId})

                  if(hasWhitelistToken){

                       val validWhitelistBypass: Boolean = {

                          // Note: Whitelisting does NOT bypass required Lilium Fee per mint

                          allOf(Coll(
                             (userBoxOUT.tokens(0)._1 == whitelistTokenId),
                             (userBoxOUT.propositionBytes == _artistSigmaProp.propBytes)
                          ))

                       }

                       validWhitelistBypass
                  } else if (hasPremintToken){

                        val validPreMintSale: Boolean = {

                           allOf(Coll(
                              (userBoxOUT.value == _priceOfNFT),
                              (userBoxOUT.tokens(0)._1 == premintTokenId),
                              (userBoxOUT.propositionBytes == _artistSigmaProp.propBytes)
                           ))

                        }

                        validPreMintSale
                  } else {
                     false
                  }
               } else {
                  false
               }
            }

            val validLiliumBox: Boolean = {

               allOf(Coll(
                  (liliumBoxOUT.value == (_liliumFeeNum * _priceOfNFT) / _liliumFeeDenom), // user or artist must still pay the Lilium fee.
                  (liliumBoxOUT.propositionBytes == _liliumSigmaProp.propBytes)
               ))

            }

            val validMinerFee: Boolean = (minerBoxOUT.value == _minerFee)

            val validTxOperatorFee: Boolean = (txOperatorBoxOUT.value >= _txOperatorFee)

            allOf(Coll(
               validCollection,
               validSupplyCap,
               validProxyBox,
               validIssuerBox,
               validStateBox,
               validUserBox,
               validLiliumBox,
               validMinerFee,
               validTxOperatorFee,
               (OUTPUTS.size == 6)
            ))

         }

         sigmaProp(validSaleTx)

      } else {

            // create box if sale has expired, do this by setting timestamp to like 5 minutes from now
            val validReturnOrBurnTx: Boolean = {

               // outputs
               val userBoxOUT: Box = OUTPUTS(0)
               val minerBoxOUT: Box = OUTPUTS(1)
               val txOperatorBoxOUT: Box = OUTPUTS(2)

               val validUserBox: Boolean = {

                val validTokenTransfer: Boolean = {

                    if (isReturn) {

                        allOf(Coll(
                            (userBoxOUT.tokens(0) == SELF.tokens(1)),
                            (userBoxOUT.tokens(0)._1 == _collectionToken)
                        ))

                    } else {

                     // maybe add condition here to ensure collection tokens get burned?
                     OUTPUTS.forall({(output: Box) => (output.tokens.size == 0)})

                    }

                }

                allOf(Coll(
                    (userBoxOUT.value == SELF.value - _minerFee - _txOperatorFee), //state box should have more than 0.001, it should have 0.001 + minerFee + operatorFee
                    (userBoxOUT.propositionBytes == _artistSigmaProp.propBytes),
                    validTokenTransfer
                ))

               }

               val validMinerFee: Boolean = (minerBoxOUT.value == _minerFee)

               val validTxOperatorFee: Boolean = (txOperatorBoxOUT.value >= _txOperatorFee)

               allOf(Coll(
                  validUserBox,
                  validMinerFee,
                  validTxOperatorFee,
                  (OUTPUTS.size == 3)
               ))

            }

            sigmaProp(validReturnOrBurnTx)

      }

   } else {
      sigmaProp(false)
   }
}
