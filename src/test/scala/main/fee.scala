package main

import AVL.IssuerBox.IssuerValue
import AVL.NFT.IndexKey
import AVL.utils.avlUtils
import com.google.gson.Gson
import io.getblok.getblok_plasma.PlasmaParameters
import io.getblok.getblok_plasma.collections.PlasmaMap
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.ergoplatform.appkit.Parameters
import sigmastate.AvlTreeFlags
import utils.DatabaseAPI

case class Ergo(usd: Double)
case class CoinGekoFormat(
    ergo: Ergo
)

object fee extends App {

  def getERGUSD: Double = {
    val ERGUSD = new HttpGet(
      s"https://api.coingecko.com/api/v3/simple/price?ids=ergo&vs_currencies=USD"
    )
    val client = HttpClients.custom().build()
    val response = client.execute(ERGUSD)
    val resp = EntityUtils.toString(response.getEntity)
    val gson = new Gson()
    gson.fromJson(resp, classOf[CoinGekoFormat]).ergo.usd
  }

  def calulateLiliumFee(amountNFTs: Int): Int = {
    val alpha = 54.10
    val beta = 0.03
    val feeUSD =
      math.floor(alpha * math.log((beta * amountNFTs) + 1)).asInstanceOf[Int]

    val ERGUSD = getERGUSD
    val feeERG = (BigDecimal(feeUSD / ERGUSD)
      .setScale(3, BigDecimal.RoundingMode.HALF_UP)
      .toDouble * Parameters.OneErg).toLong
    println(ERGUSD)
    println(feeERG)
    1
  }
  println(calulateLiliumFee(100))
}

object avlTest extends App {
  val singletonID = "d9ec7731170950b1bdd65b3e0bb9d2fb72a9e7ff4117196587c657424b84827e"
  val dataBaseResponse = DatabaseAPI.getRow(singletonID)
  val issuerTreeFromDB = PlasmaMap[IndexKey, IssuerValue](
    AvlTreeFlags.AllOperationsAllowed,
    PlasmaParameters.default
  )
  avlUtils.AVLFromExport[IndexKey, IssuerValue](
    dataBaseResponse.issuer_avl_bytes,
    issuerTreeFromDB
  )
  println(issuerTreeFromDB.lookUp(IndexKey(1)).response.head.get.metaData)
}
