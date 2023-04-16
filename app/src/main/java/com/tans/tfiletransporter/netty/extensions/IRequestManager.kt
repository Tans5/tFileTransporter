package com.tans.tfiletransporter.netty.extensions

import java.net.InetSocketAddress

interface IRequestManager {

    /**
     * For Tcp
     */
    fun <Request, Response> request(
        type: Int,
        request: Request,
        requestClass: Class<Request>,
        responseClass: Class<Response>,
        retryTimes: Int = 2,
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
        retryTimes: Int = 2,
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