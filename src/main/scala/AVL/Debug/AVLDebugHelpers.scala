package AVL.Debug

import AVL.NFT.{IndexKey}
import com.google.common.primitives.Longs
import io.getblok.getblok_plasma.PlasmaParameters
import io.getblok.getblok_plasma.collections.{
  LocalPlasmaMap,
  Proof,
  ProvenResult
}
import org.ergoplatform.appkit.ErgoValue
import scorex.crypto.authds.avltree.batch.VersionedLDBAVLStorage
import scorex.crypto.hash.{Blake2b256, Digest32}
import scorex.db.LDBVersionedStore
import sigmastate.AvlTreeFlags
import special.collection.Coll
import special.sigma.AvlTree

import java.io.File

class AVLDebugHelpers(dbFilePath: String) {

  val ldbStore = new LDBVersionedStore(new File(dbFilePath), 10)
  val avlStorage = new VersionedLDBAVLStorage[Digest32](
    ldbStore,
    PlasmaParameters.default.toNodeParams
  )(Blake2b256)
  private val metaDataMap = new LocalPlasmaMap[IndexKey, AVLDebug](
    avlStorage,
    AvlTreeFlags.AllOperationsAllowed,
    PlasmaParameters.default
  )

  def getMap: LocalPlasmaMap[IndexKey, AVLDebug] = {
    return metaDataMap
  }

  def insertMetaData(
      key: IndexKey,
      data: AVLDebug
  ): ProvenResult[AVLDebug] = {
    val dataSet: Seq[(IndexKey, AVLDebug)] = Seq(key -> data)
    return getMap.insert(dataSet: _*)
  }

  def getDigest: String = {
    return getMap.toString
  }

  def lookUpString(key: IndexKey): String = {
    return getMap.lookUp(key).toString
  }

  def lookUp(key: IndexKey): ProvenResult[AVLDebug] = {
    return getMap.lookUp(key)
  }

  def getErgoValueOfMap: ErgoValue[AvlTree] = {
    return getMap.ergoValue
  }
  def getErgoValueOfMapHex: String = {
    return getMap.ergoValue.toHex
  }

  def getProof(response: ProvenResult[AVLDebug]): Proof = {
    return response.proof
  }

  def getErgoValueOfProof(proof: Proof): ErgoValue[Coll[java.lang.Byte]] = {
    return proof.ergoValue
  }

  def getErgoValueOfProofHex(proof: Proof): String = {
    return proof.ergoValue.toHex
  }

  def convertLongtoByteArrayErgoValue(
      value: java.lang.Long
  ): ErgoValue[Coll[java.lang.Byte]] = {
    return ErgoValue.of(Longs.toByteArray(value))
  }

  def convertLongtoByteArray(value: java.lang.Long): Array[Byte] = {
    return Longs.toByteArray(value)
  }

  def convertLongtoByteArrayHex(value: java.lang.Long): String = {
    return ErgoValue.of(Longs.toByteArray(value)).toHex
  }

}
