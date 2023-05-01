package com.tans.tfiletransporter.netty.extensions

import java.net.InetSocketAddress

interface IClientManager {

    /**
     * For Tcp
     */
    fun <Request, Response> request(
        type: Int,
        request: Request,
        requestClass: Class<Request>,
        responseClass: Class<Response>,
        retryTimes: Int = 2,
        retryTimeout: Long = 1000L,
        callback: RequestCallback<Response>
    )

    /**
     * For Udp
     */
    fun <Request, Response> request(
        type: Int,
        request: Request,
        requestClass: Class<Request>,
        responseClass: Class<Response>,
        targetAddress: InetSocketAddress,
        senderAddress: InetSocketAddress? = null,
        retryTimes: Int = 2,
        retryTimeout: Long = 1000L,
        callback: RequestCallback<Response>
    )

    interface RequestCallback<Response> {

        fun onSuccess(
            type: Int,
            messageId: Long,
            localAddress: InetSocketAddress?,
            remoteAddress: InetSocketAddress?,
            d: Response
        )

        fun onFail(errorMsg: String)

    }
}

inline fun <reified Request, reified Response> IClientManager.requestSimplify(
    type: Int,
    request: Request,
    retryTimes: Int = 2,
    retryTimeout: Long = 1000L,
    callback: IClientManager.RequestCallback<Response>
) {
    request(
        type = type,
        request = request,
        requestClass = Request::class.java,
        responseClass = Response::class.java,
        retryTimes = retryTimes,
        retryTimeout = retryTimeout,
        callback = callback
    )
}

inline fun <reified Request, reified Response> IClientManager.requestSimplify(
    type: Int,
    request: Request,
    targetAddress: InetSocketAddress,
    senderAddress: InetSocketAddress? = null,
    retryTimes: Int = 2,
    retryTimeout: Long = 1000L,
    callback: IClientManager.RequestCallback<Response>
) {
    request(
        type = type,
        request = request,
        requestClass = Request::class.java,
        responseClass = Response::class.java,
        targetAddress = targetAddress,
        senderAddress = senderAddress,
        retryTimes = retryTimes,
        retryTimeout = retryTimeout,
        callback = callback
    )
}