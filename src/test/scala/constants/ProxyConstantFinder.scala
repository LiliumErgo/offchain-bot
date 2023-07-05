package constants

import configs.serviceOwnerConf
import contracts.LiliumContracts
import mint.Client
import utils.ContractCompile

import scala.collection.mutable

object ProxyConstantFinder extends App {

  val client: Client = new Client()
  client.setClient
  val ctx = client.getContext
  val serviceConf = serviceOwnerConf.read("serviceOwner.json")
  val compiler = new ContractCompile(ctx)
  val minerFeeNanoErg = 4382052

  val proxyContract = compiler.compileProxyContract(
    LiliumContracts.ProxyContract.contractScript,
    minerFeeNanoErg
  )

  val potentialMinerFeeConstantList = mutable.ListBuffer[Int]()
  proxyContract.getErgoTree.constants.zipWithIndex.foreach { case (c, i) =>
    c.value match {
      case value: Long =>
        if (value == minerFeeNanoErg) {
          potentialMinerFeeConstantList += i
        }
      case _ => // Do nothing
    }
  }

  println(
    "Potential Miner Fee Constant: " + potentialMinerFeeConstantList.mkString(
      ", "
    )
  )

}
