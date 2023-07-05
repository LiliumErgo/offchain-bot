package configs

case class SpendingProof(proofBytes: String, extension: Map[String, String])

case class Input(boxId: String, spendingProof: SpendingProof)

case class DataInput(boxId: String)

case class SignedAsset(tokenId: String, amount: String)

case class Output(
    boxId: String,
    value: String,
    ergoTree: String,
    creationHeight: Int,
    index: Int,
    transactionId: String,
    assets: List[SignedAsset],
    additionalRegisters: Map[String, String]
)

case class SignedTransactionJson(
    id: String,
    inputs: List[Input],
    dataInputs: List[DataInput],
    outputs: List[Output]
)
