{
    // ===== Contract Description ===== //
    // Name: Whitelist Issuer Contract
    // Description: Used to guard the issuer box of minting the whitelist tokens.
    // Version: 1.0.0
    // Author: lucagdangelo@github.com
    // Auditor: mgpai22@github.com

    // ===== Compile Time Constants ===== //
    // _TxOperatorPK: SigmaProp

    // ===== Context Extension Variables ===== //
    // None

    val validWhitelistMintTx: Boolean = {

        val validWhitelistIssuanceBox: Boolean = {

            val whitelistAmount = SELF.R4[Long].get
            val validTokens: Boolean = (OUTPUTS(0).tokens(0) == (SELF.id, whitelistAmount))
            val validUser: Boolean = (OUTPUTS(0).propositionBytes == INPUTS(0).propositionBytes) // who ever sent the funds gets the tokens

            allOf(Coll(
                validTokens,
                validUser
            ))

        }

        validWhitelistIssuanceBox
    }

    sigmaProp(validWhitelistMintTx) && _TxOperatorPK

}
