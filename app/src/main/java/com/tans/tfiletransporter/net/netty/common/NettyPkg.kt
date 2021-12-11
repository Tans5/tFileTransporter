package com.tans.tfiletransporter.net.netty.common

import androidx.annotation.Keep
import com.tans.tfiletransporter.net.netty.common.handler.HeartbeatChecker
import com.tans.tfiletransporter.net.netty.common.handler.PackageDecoder
import com.tans.tfiletransporter.net.netty.common.handler.PackageEncoder
import com.tans.tfiletransporter.net.netty.common.handler.PkgWriter
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.timeout.IdleStateHandler

const val TAG_NETTY = "NETTY"

const val HEART_BEHAT_PKG: Byte = 0x00
const val CLIENT_FINISH_PKG: Byte = 0x01
const val SERVER_FINISH_PKG: Byte = 0x02
const val BYTES_PKG: Byte = 0x03
const val JSON_PKG: Byte = 0x04
const val TEXT_PKG: Byte = 0x05
const val RESPONSE_PKG: Byte = 0x06

// 30MB
const val NETTY_MAX_PACKAGE_SIZE = 30 * 1024 * 1024

@Keep
sealed class NettyPkg {
    @Keep
    object HeartBeatPkg : NettyPkg()
    @Keep
    data class ClientFinishPkg(val msg: String) : NettyPkg()
    @Keep
    data class ServerFinishPkg(val msg: String) : NettyPkg()
    @Keep
    data class BytesPkg(val bytes: ByteArray, override val pkgIndex: UInt? = null) : NettyPkg(), PkgIndex {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BytesPkg

            if (!bytes.contentEquals(other.bytes)) return false

            return true
        }

        override fun hashCode(): Int {
            return bytes.contentHashCode()
        }
    }
    @Keep
    data class JsonPkg(val json: String, override val pkgIndex: UInt? = null) : NettyPkg(), PkgIndex
    @Keep
    data class TextPkg(val text: String, override val pkgIndex: UInt? = null) : NettyPkg(), PkgIndex
    @Keep
    data class ResponsePkg(override val pkgIndex: UInt) : NettyPkg(), PkgIndex

    interface PkgIndex {
        val pkgIndex: UInt?
    }
}

const val HANDLER_IDLE_STATE = "IDLE_STATE"
const val HANDLER_LENGTH_DECODER = "LENGTH_DECODER"
const val HANDLER_LENGTH_ENCODER = "LENGTH_ENCODER"
const val HANDLER_PACKAGE_DECODER = "PACKAGE_DECODER"
const val HANDLER_PACKAGE_ENCODER = "PACKAGE_ENCODER"
const val HANDLER_HEARTBEAT_CHECKER = "HEARTBEAT_CHECKER"
const val HANDLER_PKG_WRITER = "PKG_WRITER"

fun ChannelPipeline.setDefaultHandler(timeoutSeconds: Int = 30): ChannelPipeline {
    return addLast(HANDLER_IDLE_STATE, IdleStateHandler(0, 0, timeoutSeconds))
        .addLast(HANDLER_LENGTH_ENCODER, LengthFieldPrepender(4))
        .addLast(HANDLER_LENGTH_DECODER, LengthFieldBasedFrameDecoder(NETTY_MAX_PACKAGE_SIZE, 0, 4, 0, 4))
        .addLast(HANDLER_PACKAGE_ENCODER, PackageEncoder())
        .addLast(HANDLER_PACKAGE_DECODER, PackageDecoder())
        .addLast(HANDLER_HEARTBEAT_CHECKER, HeartbeatChecker((timeoutSeconds / 2).let { if (it <= 0) 1 else it }))
        .addLast(HANDLER_PKG_WRITER, PkgWriter())
}

fun ByteBuf.readBytes(): ByteArray {
    val size = writerIndex() - readerIndex()
    return if (size > 0) {
        ByteArray(size).apply {
            readBytes(this)
        }
    } else {
        ByteArray(0)
    }
}

