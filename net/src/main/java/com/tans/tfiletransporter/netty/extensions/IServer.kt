package com.tans.tfiletransporter.netty.extensions

import com.tans.tfiletransporter.ILog
import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.PackageData
import com.tans.tfiletransporter.netty.PackageDataWithAddress
import com.tans.tfiletransporter.netty.udp.NettyUdpConnectionTask
import java.net.InetSocketAddress

interface IServer<Request, Response> {

    val requestClass: Class<Request>

    val responseClass: Class<Response>

    val replyType: Int

    val log: ILog

    fun couldHandle(requestType: Int): Boolean

    fun dispatchRequest(
        localAddress: InetSocketAddress?,
        remoteAddress: InetSocketAddress?,
        msg: PackageData,
        converterFactory: IConverterFactory,
        connectionTask: INettyConnectionTask,
        isNewRequest: Boolean
    ) {
        // 找到 request 的 body 转换器
        val converter = converterFactory.findBodyConverter(msg.type, requestClass)
        if (converter != null) {
            // 转换 request 的数据
            val convertedData = converter.convert(
                type = msg.type,
                dataClass = requestClass,
                packageData = msg
            )
            if (convertedData != null) {
                // 处理 request 的数据并获取 response
                val response = onRequest(localAddress, remoteAddress, convertedData, isNewRequest)
                if (response != null) {
                    // 找到 response 的 pkt 转换器
                    val responseConverter = converterFactory.findPackageDataConverter(replyType, responseClass)
                    if (responseConverter != null) {
                        // 转换 response 到 pkt
                        val pckData = responseConverter.convert(
                            type = replyType,
                            messageId = msg.messageId,
                            data = response,
                            dataClass = responseClass
                        )
                        if (pckData != null) {
                            // 发送 response 数据
                            if (connectionTask is NettyUdpConnectionTask) {
                                if (remoteAddress != null) {
                                    connectionTask.sendData(
                                        data = PackageDataWithAddress(
                                            receiverAddress = remoteAddress,
                                            data = pckData
                                        ),
                                        sendDataCallback = object : INettyConnectionTask.SendDataCallback {
                                            override fun onSuccess() {}
                                            override fun onFail(message: String) {
                                                log.e("Server", "Reply fail: $message")
                                            }
                                        }
                                    )
                                }
                            } else {
                                connectionTask.sendData(
                                    data = pckData,
                                    sendDataCallback = object : INettyConnectionTask.SendDataCallback {
                                        override fun onSuccess() {}
                                        override fun onFail(message: String) {
                                            log.e("Server", "Reply fail: $message")
                                        }
                                    }
                                )
                            }
                        } else {
                            log.e(
                                "Server",
                                "${responseConverter::javaClass} convert $replyType fail."
                            )
                        }
                    } else {
                        log.e("Server", "Didn't find converter $replyType, $responseClass")
                    }
                }
            } else {
                log.e(
                    "Server",
                    "${converter::javaClass} convert $requestClass fail."
                )
            }
        } else {
            log.e("Server", "Didn't find converter ${msg.type}, $requestClass")
        }
    }

    fun onRequest(
        localAddress: InetSocketAddress?,
        remoteAddress: InetSocketAddress?,
        r: Request,
        isNewRequest: Boolean
    ): Response?

}

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
inline fun <reified Request, reified Response> simplifyServer(
    requestType: Int,
    responseType: Int,
    log: ILog,
    crossinline onRequest: (localAddress: InetSocketAddress?, remoteAddress: InetSocketAddress?, r: Request, isNewRequest: Boolean) -> Response?
): IServer<Request, Response> {
    return object : IServer<Request, Response> {
        override val requestClass: Class<Request> = Request::class.java
        override val responseClass: Class<Response> = Response::class.java
        override val replyType: Int = responseType
        override val log: ILog = log
        override fun couldHandle(t: Int): Boolean = t == requestType

        override fun onRequest(
            localAddress: InetSocketAddress?,
            remoteAddress: InetSocketAddress?,
            r: Request,
            isNewRequest: Boolean
        ): Response? {
            return onRequest(localAddress, remoteAddress, r, isNewRequest)
        }

    }
}