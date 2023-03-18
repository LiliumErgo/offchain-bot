package configs
import com.google.gson.reflect.TypeToken
import com.google.gson.{Gson, GsonBuilder, TypeAdapter}
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

import java.io.{FileWriter, Writer}
import scala.collection.mutable
import scala.collection.JavaConverters._
import scala.io.Source

class ListTypeAdapter extends TypeAdapter[List[Any]] {
  override def read(jsonReader: JsonReader): List[Any] = List.empty
  override def write(jsonWriter: JsonWriter, list: List[Any]): Unit =
    jsonWriter.nullValue()
}

case class MasterMetadata(
    items: Data
)

case class Data(
    name: String,
    description: String,
    image: String,
    imageSHA256: String,
    dna: String,
    edition: Int,
    attributes: Array[Attribute],
    levels: Array[Level],
    stats: Array[Stats]
)
case class Attribute(trait_type: String, value: String)

case class Level(trait_type: String, max_value: Int, value: Int)

case class Stats(trait_type: String, max_value: Int, value: Int)

case class Collection(
    collectionInfo: CollectionInfo,
    socialMedia: java.util.Map[String, String],
    royalty: java.util.Map[String, Double],
    saleStartTimestamp: Long,
    saleEndTimestamp: Long,
    mintingExpiry: Long,
    collectionMaxSize: Long,
    priceOfNFTNanoErg: Long,
    returnCollectionTokensToArtist: Boolean
)

case class CollectionInfo(
    collectionName: String,
    collectionDescription: String,
    collectionLogoURL: String,
    collectionFeaturedImageURL: String,
    collectionBannerImageURL: String,
    collectionCategory: String
)

case class SocialMediaEntry(name: String, url: String)

object masterMeta {
  private val gson = new GsonBuilder()
    .setPrettyPrinting()
    .create()

  def read(filePath: String): Array[Data] = {
    val jsonString: String = Source.fromFile(filePath).mkString
    gson
      .fromJson(jsonString, classOf[Array[Data]])
  }
}

object metadata {
  private val gson = new GsonBuilder()
    .setPrettyPrinting()
    .create()

  def read(filePath: String): Data = {
    val jsonString: String = Source.fromFile(filePath).mkString
    gson.fromJson(jsonString, classOf[Data])
  }
}

object collectionParser {
  private val gson = new GsonBuilder()
    .setPrettyPrinting()
    .create()

  def read(filePath: String): Collection = {
    val jsonString: String = Source.fromFile(filePath).mkString
    gson.fromJson(jsonString, classOf[Collection])
  }

}
