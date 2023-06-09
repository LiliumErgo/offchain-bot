{
    // ===== Contract Description ===== //
    // Name: Sale LP Contract
    // Description: Used to guard the LP of the sale to pay the lilium fee and tx operator fees if artist chooses to use custom payment token instead of just ERG.
    // Version: 1.0.0
    // Author: lucagdangelo@github.com
    // Auditor: mgpai22@github.com

    // ===== Box Contents ===== //
    // Tokens
    // 1. (StateSingletonTokenId, 1) // Not really a singleton since there will be two tokens in existence, but just keeping the same name.

    // ===== Compile Time Constants ===== //
    // _priceOfNFT: Long
    // _liliumFeeNum: Long
    // _liliumFeeDenom: Long
    // _txOperatorFee: Long
    // _minerFee: Long
    // _artistSigmaProp: SigmaProp
    // _txOperatorPK: SigmaProp
    
    // ===== Context Extension Variables ===== //
    // None

    // ===== Relevant Variables ===== //
    val stateSingletonTokenId: Coll[Byte] = SELF.tokens(0)._1
    val liliumFee: Long = (_liliumFeeNum * _priceOfNFT) / _liliumFeeDenom
    val isSale: Boolean = (INPUTS.size > 1)

    if (isSale) {

        val validSaleTx: Boolean = {

            // inputs
            val stateBoxIN: Box         = INPUTS(0)
            val buyerProxyIN: Box       = INPUTS(1)

            // outputs
            val nftIssuerOUT: Box       = OUTPUTS(0)
            val stateBoxOUT: Box        = OUTPUTS(1)
            val userFeeOUT: Box         = OUTPUTS(2)
            val liliumFeeOUT: Box       = OUTPUTS(3)
            Val saleLPOUT: Box          = OUTPUTS(4)
            val minerFeeOUT: Box        = OUTPUTS(5)
            val txOperatorFeeOUT: Box   = OUTPUTS(6)

            val validStateBox: Boolean = {

                (stateBoxIN.tokens(0)._1 == stateSingletonTokenId) // check that the state box has the right singleton value

            }

            val validLiliumFee: Boolean = {

                (liliumFeeOUT.value == liliumFee)

            }

            val validTxOperatorFee: Boolean = {

                (txOperatorFeeOUT.value == _txOperatorFee)

            }

            val validMinerFee: Boolean = {

                (minerFeeOUT.value == _minerFee)

            }

            val validSelfRecreation: Boolean = {

                allOf(Coll(
                    (saleLPOUT.value == SELF.value - liliumFee - _txOperatorFee - _minerFee),
                    (saleLPOUT.propositionBytes == SELF.propositionBytes),
                    (saleLPOUT.tokens == SELF.tokens)
                ))

            }

            allOf(Coll(
                validStateBox,
                validLiliumFee,
                validTxOperatorFee,
                validMinerFee,
                validSelfRecreation
            ))

        }

        sigmaProp(validSaleTx) && _txOperatorPK

    } else {
        
        val validRefundTx: Boolean = {

            // outputs
            val userBox: Box = OUTPUTS(0)
            val minerBox: Box = OUTPUTS(1)

            val validUserBox: Boolean = {

                allOf(Coll(
                    (userBox.value == SELF.value - _minerFee),
                    (userBox.propositionBytes == _artistSigmaProp.propBytes)
                ))

            }

            val validMinerFee: Boolean = (minerBox.value == _minerFee)

            val validSingletonBurn: Boolean = (OUTPUTS.tokens.exists({ (t: (Coll[Byte], Long)) => t._1 != stateSingletonTokenId }))

            allOf(Coll(
                validUserBox,
                validMinerFee,
                validSingletonBurn
            ))

        }

        sigmaProp(validRefundTx) && _artistSigmaProp

    }

}
