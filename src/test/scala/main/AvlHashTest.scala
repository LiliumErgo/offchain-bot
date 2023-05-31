package main

import AVL.IssuerBox.{IssuerHelpersAVL, IssuerValue}
import AVL.NFT.{IndexKey, IssuanceAVLHelpers}
import AVL.utils.avlUtils.{encoder, prepareAVL}
import configs.{Data, masterMeta}
import org.bouncycastle.util.encoders.Hex
import utils.MetadataTranscoder
import scorex.crypto.hash.Blake2b256

import scala.collection.mutable

object AvlHashTest extends App {
  val issuanceTree = new IssuanceAVLHelpers
  val issuerTree = new IssuerHelpersAVL
  private val metadataTranscoder = new MetadataTranscoder
  private val encoder = new metadataTranscoder.Encoder
  private val decoder = new metadataTranscoder.Decoder

  val res: Data = masterMeta.read("metadata.json").head

  val attributesMap = mutable.LinkedHashMap(
    res.attributes.map(a => a.trait_type -> a.value): _*
  )

  val levelsMap = mutable.LinkedHashMap(
    res.levels.map(a => a.trait_type -> (a.value, a.max_value)): _*
  )

  val statsMap = mutable.LinkedHashMap(
    res.stats.map(a => a.trait_type -> (a.value, a.max_value)): _*
  )

//  val issuerDataToInsert = IssuerValue.createMetadata(
//    encoder
//      .encodeMetaData(
//        false
//        attributesMap,
//        levelsMap,
//        statsMap
//      )
//      .getValue
//  )
//
//  val blakeHash = Hex.toHexString(Blake2b256.hash(issuerDataToInsert.toBytes))
//
//  println(issuerDataToInsert.toBytes.mkString("Array(", ", ", ")"))
//  println(blakeHash)

}
