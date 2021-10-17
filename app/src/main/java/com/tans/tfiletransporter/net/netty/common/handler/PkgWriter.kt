package com.tans.tfiletransporter.net.netty.common.handler

import android.annotation.SuppressLint
import com.tans.tfiletransporter.net.netty.common.HANDLER_PKG_WRITER
import com.tans.tfiletransporter.net.netty.common.NettyPkg
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class PkgWriter : ChannelInboundHandlerAdapter() {

    private val writePackageIndex: AtomicLong = AtomicLong(0)
    private val indexReplySubject = PublishSubject.create<UInt>().toSerialized()

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        if (msg is NettyPkg.ResponsePkg) {
            indexReplySubject.onNext(msg.pkgIndex)
        }
        super.channelRead(ctx, msg)
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        super.channelInactive(ctx)
        indexReplySubject.onComplete()
    }

    fun ChannelHandlerContext.writePkg(pkg: NettyPkg): UInt? {
        val fixedPkg = when (pkg) {
            is NettyPkg.BytesPkg -> pkg.copy(pkgIndex = writePackageIndex.getAndIncrement().toUInt())
            is NettyPkg.JsonPkg -> pkg.copy(pkgIndex = writePackageIndex.getAndIncrement().toUInt())
            is NettyPkg.TextPkg -> pkg.copy(pkgIndex = writePackageIndex.getAndIncrement().toUInt())
            else -> null
        }
        writeAndFlush(fixedPkg ?: pkg)
        return (fixedPkg as? NettyPkg.PkgIndex)?.pkgIndex ?: (pkg as? NettyPkg.PkgIndex)?.pkgIndex
    }

    @SuppressLint("CheckResult")
    fun ChannelHandlerContext.writePkgBlockReply(pkg: NettyPkg, timeoutMillis: Long = 30 * 1000L): UInt {
        val index = writePkg(pkg) ?: error("$pkg not support block reply")
        indexReplySubject.first(index).timeout(timeoutMillis, TimeUnit.MILLISECONDS).blockingGet()
        return index
    }
}

fun ChannelHandlerContext.writePkg(pkg: NettyPkg): UInt? {
    val writer = (pipeline()[HANDLER_PKG_WRITER] as? PkgWriter) ?: error("Didn't find Pkg writer.")
    return with(writer) {
        writePkg(pkg)
    }
}

fun ChannelHandlerContext.writePkgBlockReply(pkg: NettyPkg, timeoutMillis: Long = 30 * 1000L): UInt {
    val writer = (pipeline()[HANDLER_PKG_WRITER] as? PkgWriter) ?: error("Didn't find Pkg writer.")
    return with(writer) {
        writePkgBlockReply(pkg, timeoutMillis)
    }
}