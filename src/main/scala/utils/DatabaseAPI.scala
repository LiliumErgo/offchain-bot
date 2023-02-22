package utils

import org.apache.http._
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import com.google.gson.{Gson, GsonBuilder}
import configs.{AvlJson, serviceOwnerConf}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.util.EntityUtils
import play.api.http.MediaType.parse

import java.util

case class LiliumRequestEntry(
    var state_box_singleton_id: String,
    collection_id: String,
    start_timestamp: String,
    end_timestamp: String,
    user_pk: String,
    issuer_avl_bytes: String,
    issuance_avl_bytes: String,
    royalty_bytes: String
)

case class LiliumEntry(
    state_box_singleton_id: String,
    collection_id: String,
    start_timestamp: String,
    end_timestamp: String,
    user_pk: String,
    issuer_avl_bytes: AvlJson,
    issuance_avl_bytes: AvlJson,
    royalty_bytes: String
)

object DatabaseAPI {

  private val serviceFilePath = "serviceOwner.json"
  private lazy val serviceConf = serviceOwnerConf.read(serviceFilePath)

  private val readApiKey = serviceConf.dataBaseKey
  private val tableEndpointURI = serviceConf.dataBaseURL

  def getRow(
      stateBoxSingleton: String
  ): LiliumEntry = {

    val get = new HttpGet(
      s"${tableEndpointURI}?select=*&state_box_singleton_id=eq.${stateBoxSingleton}"
    )

    get.setHeader("apikey", readApiKey)
    get.setHeader(HttpHeaders.AUTHORIZATION, s"Bearer ${readApiKey}")
    // send the post request
    val client = HttpClients.custom().build()
    val response = client.execute(get)

    val resp =
      EntityUtils.toString(response.getEntity)

    if (resp == "[]") {
      return null
    }

    val parsedResp = resp.substring(1, resp.length - 1)

    val gson = new GsonBuilder().setPrettyPrinting().create()
    val liliumResponseEntry =
      gson.fromJson(parsedResp, classOf[LiliumRequestEntry])

    val issuerEntry: AvlJson =
      gson.fromJson(liliumResponseEntry.issuer_avl_bytes, classOf[AvlJson])
    val issuanceEntry: AvlJson =
      gson.fromJson(liliumResponseEntry.issuance_avl_bytes, classOf[AvlJson])

    LiliumEntry(
      liliumResponseEntry.state_box_singleton_id,
      liliumResponseEntry.collection_id,
      liliumResponseEntry.start_timestamp,
      liliumResponseEntry.end_timestamp,
      liliumResponseEntry.user_pk,
      issuerEntry,
      issuanceEntry,
      liliumResponseEntry.royalty_bytes
    )
  }
}

object dbTest extends App {
  val res: LiliumEntry = DatabaseAPI.getRow(
    "3f3f356653f705ff002ea8a2d25643e3370819db3b1202a1f1c4ddcd5114521d"
  )
  val json = new GsonBuilder()
    .setPrettyPrinting()
    .create()
    .toJson(res, classOf[LiliumEntry])

  println(json)
}
