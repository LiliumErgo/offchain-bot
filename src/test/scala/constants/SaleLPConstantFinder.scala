package constants

import configs.{collectionParser, serviceOwnerConf}
import contracts.LiliumContracts
import mint.Client
import org.ergoplatform.appkit.{Address, ErgoToken}
import special.collection.Coll
import utils.{ContractCompile, MetadataTranscoder}

import scala.collection.mutable

object SaleLPConstantFinder extends App {

  val client: Client = new Client()
  client.setClient
  val ctx = client.getContext
  val serviceConf = serviceOwnerConf.read("serviceOwner.json")
  val compiler = new ContractCompile(ctx)
  val proxyContract = compiler.compileProxyContract(
    LiliumContracts.ProxyContract.contractScript,
    serviceConf.minerFeeNanoErg
  )
  val minerFeeNanoErg = 4382052

  val minBoxValue = 423944325
  val minTxOperatorFeeNanoErg = 4239084

  val saleLPContract = compiler.compileSaleLP(
    LiliumContracts.SaleLP.contractScript,
    minBoxValue,
    minerFeeNanoErg,
    minTxOperatorFeeNanoErg
  )

  val potentialMinerFeeConstantList = mutable.ListBuffer[Int]()
  val potentialTXOperatorFeeList = mutable.ListBuffer[Int]()
  val potentialMinBoxValueList = mutable.ListBuffer[Int]()

  saleLPContract.getErgoTree.constants.zipWithIndex.foreach { case (c, i) =>
    c.value match {
      case value: Long =>
        if (value == minerFeeNanoErg) {
          potentialMinerFeeConstantList += i
        } else if (value == minTxOperatorFeeNanoErg) {
          potentialTXOperatorFeeList += i
        } else if (value == minBoxValue) {
          potentialMinBoxValueList += i
        }
      case _ => // Do nothing
    }
  }

  println(
    "Potential Miner Fee Constant: " + potentialMinerFeeConstantList.mkString(
      ", "
    )
  )

  println(
    "Potential TX Operator Fee Constant: " + potentialTXOperatorFeeList
      .mkString(", ")
  )
  println(
    "Potential Min Box Value Constant: " + potentialMinBoxValueList
      .mkString(", ")
  )

}
