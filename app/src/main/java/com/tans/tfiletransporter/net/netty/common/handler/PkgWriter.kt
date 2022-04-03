package com.tans.tfiletransporter.net.netty.common.handler

import android.annotation.SuppressLint
import com.tans.tfiletransporter.net.netty.common.NettyPkg
import io.netty.channel.Channel
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

    fun Channel.writePkgWriter(pkg: NettyPkg): UInt? {
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
    fun Channel.writePkgBlockReplyWriter(pkg: NettyPkg, timeoutMillis: Long = 30 * 1000L): UInt {
        val index = writePkgWriter(pkg) ?: error("$pkg not support block reply")
        indexReplySubject.filter { it == index }.timeout(timeoutMillis, TimeUnit.MILLISECONDS).firstOrError().blockingGet()
        return index
    }
}

fun Channel.writePkg(pkg: NettyPkg): UInt? {
    val writer = pipeline().get(PkgWriter::class.java) ?: error("Didn't find Pkg writer.")
    return with(writer) {
        writePkgWriter(pkg)
    }
}

fun Channel.writePkgBlockReply(pkg: NettyPkg, timeoutMillis: Long = 30 * 1000L): UInt {
    val writer = pipeline().get(PkgWriter::class.java) ?: error("Didn't find Pkg writer.")
    return with(writer) {
        writePkgBlockReplyWriter(pkg, timeoutMillis)
    }
}