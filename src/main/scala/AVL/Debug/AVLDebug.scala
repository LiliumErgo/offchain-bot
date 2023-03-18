package AVL.Debug

import com.google.common.primitives.Longs
import io.getblok.getblok_plasma.ByteConversion
import org.bouncycastle.util.Strings
import org.bouncycastle.util.encoders.Hex

import scorex.crypto.hash

import java.nio.charset.StandardCharsets

case class AVLDebug(name: String, description: String) {
  def toBytes: Array[Byte] = {
    val delimiter: Array[Byte] =
      Array[Byte](
        0x7f.toByte,
        0x00.toByte,
        0x0f.toByte,
        0xba.toByte,
        0xf5.toByte,
        0x5f.toByte,
        0xab.toByte,
        0x7e.toByte
      )

    name.getBytes(StandardCharsets.UTF_8) ++ delimiter ++ description
      .getBytes(StandardCharsets.UTF_8)

  }
}

case class AVLDebugBytes(bytes: Array[Byte]) {
  def toBytes: Array[Byte] = {
    bytes
  }
}

object AVLDebug {
  implicit val ErgoValueMetaConversionV2: ByteConversion[AVLDebug] =
    new ByteConversion[AVLDebug] {
      override def convertToBytes(t: AVLDebug): Array[Byte] = t.toBytes
      override def convertFromBytes(bytes: Array[Byte]): AVLDebug = {
        val delimiter: Array[Byte] =
          Array[Byte](
            0x7f.toByte,
            0x00.toByte,
            0x0f.toByte,
            0xba.toByte,
            0xf5.toByte,
            0x5f.toByte,
            0xab.toByte,
            0x7e.toByte
          )
        val delimiterIndex = bytes.indexOfSlice(delimiter)
        val nameBytes = bytes.slice(0, delimiterIndex)
        val descriptionBytes =
          bytes.slice(delimiterIndex + delimiter.length, bytes.length)
        AVLDebug(
          new String(nameBytes, StandardCharsets.UTF_8),
          new String(descriptionBytes, StandardCharsets.UTF_8)
        )
      }
    }

  def createMetadata(
      name: String,
      description: String
  ): AVLDebug = {
    val metaData: AVLDebug = AVLDebug(
      name,
      description
    )
    metaData
  }

}
