{

    // ===== Contract Description ===== //
    // Name: Collection Issuance Contract
    // Description: Collection Tokens are required . In order to create the state box, the state box needs the collection token ID as a constant. Therefore, the
    //              tokens cannot be directly minted to the state box. This contract acts as a proxy to hold the collection tokens before it is sent to the newly created state box.
    // Version: 1.0.0
    // Author: mgpai22@github.com
    // Auditor: lucagdangelo@github.com

    // ===== Box Contents ===== //
    // Tokens
    // 1. (CollectionTokens, CollectionSize)

    // ===== Compile Time Constants ===== //
    // _txOperatorPK: SigmaProp

    // ===== Context Extension Variables ===== //
    val collectionIssuerBox: Box = getVar[Box](0).get
    val stateBoxContractBytes: Coll[Byte] = getVar[Coll[Byte]](1).get

    val properOutput = (OUTPUTS(0).propositionBytes == stateBoxContractBytes)
    val properTokenTransfer = (OUTPUTS(0).tokens(1) == (collectionIssuerBox.id, collectionIssuerBox.R9[Long].get)) && (SELF.tokens(0)._1  == collectionIssuerBox.id)

    sigmaProp(properOutput && properTokenTransfer) && _txOperatorPK

}
