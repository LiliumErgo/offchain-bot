{

    // ===== Contract Description ===== //
    // Name: Singleton Issuance Contract
    // Description: A singleton token is required to track the state box. In order to create the state box, the state box needs the singleton token ID as a constant. Therefore, the
    //                singleton cannot be directly minted to the state box. This contract acts as a proxy to hold the singleton before it is sent to the newly created state box.
    // Version: 1.0.0
    // Author: mgpai22@github.com
    // Auditor: lucagdangelo@github.com

    // ===== Box Registers ===== //
    // Tokens: Singleton Token

    // ===== Compile Time Constants ===== //
    // _SingletonToken: Coll[Byte]
    // _usePool: Boolean
    // _singletonIssuanceContractBytes: Coll[Byte]
    // _saleLPContractBytes: Coll[Byte]
    // _TxOperatorPK: SigmaProp

    // ===== Context Extension Variables ===== //
    // None
   
    val properOutput: Boolean = OUTPUTS(0).propositionBytes == _singletonIssuanceContractBytes
    val properSaleLP: Boolean = if (usePool) OUTPUTS(1).propositionBytes == _saleLPContractBytes
    val properTokenTransfer: Boolean = {

        if (usePool) {

            allOf(Coll(
                (OUTPUTS(0).tokens(0) == (_SingletonToken, 1L)), // state box
                (OUTPUTS(1).tokens(0) == (_SingletonToken, 1L)), // sale lp box
                (SELF.tokens(0)._1  == _SingletonToken)
            ))

        } else {

            allOf(Coll(
                (OUTPUTS(0).tokens(0) == (_SingletonToken, 1L)),
                (SELF.tokens(0)._1  == _SingletonToken)
            ))

        } 

    }

    sigmaProp(properOutput && properTokenTransfer) && _TxOperatorPK

}
