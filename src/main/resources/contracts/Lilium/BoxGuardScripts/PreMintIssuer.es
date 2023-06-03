{
    // ===== Contract Description ===== //
    // Name: PreMint Issuer Contract
    // Description: Used to guard the issuer box of minting the pre-mint tokens.
    // Version: 1.0.0
    // Author: lucagdangelo@github.com
    // Auditor: mgpai22@github.com

    // ===== Compile Time Constants ===== //
    // _PreMintTokenAmount: Long
    // _UserPK: SigmaProp
    // _TxOperatorPK: SigmaProp

    // ===== Context Extension Variables ===== //
    // None

    val validPreMintMintingTx: Boolean = {

        val validPreMintIssaunceBox: Boolean = {

            val validTokens: Boolean = (OUTPUTS(0).tokens(0) == (SELF.id, _PreMintTokenAmount))
            val validUser: Boolean = (OUTPUTS(0).propositionBytes == _UserPK.propBytes)

            allOf(Coll(
                validTokens,
                validUser
            ))

        }

        validPreMintIssuanceBox
    }

    sigmaProp(validPreMintMintingTx) && _TxOperatorPK

}
