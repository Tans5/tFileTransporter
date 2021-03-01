package com.tans.tfiletransporter.net

import android.os.Build

// UDP Porter
const val UDP_BROADCAST_RECEIVER_PORT = 6666
// TCP Porter
const val UDP_BROADCAST_LISTENER_PORT = 6667
const val UDP_BROADCAST_SERVER_ACCEPT: Byte = 0x00
const val UDP_BROADCAST_SERVER_DENY: Byte = 0x01

// TCP Porter
const val FILE_TRANSPORT_LISTEN_PORT = 6668
const val FILE_TRANSPORTER_VERSION_INT: Byte = 0x01

// TCP Porter
const val MULTI_CONNECTIONS_FILES_TRANSFER_LISTEN_PORT = 6669

// TCP Porter
const val TCP_SCAN_CONNECT_LISTEN_PORTER = 7000

val LOCAL_DEVICE = "${Build.BRAND} ${Build.MODEL}"

// 512 KB
const val NET_BUFFER_SIZE = 1024 * 512

val commonNetBufferPool = NetBufferPool(
    poolSize = 100,
    bufferSize = NET_BUFFER_SIZE
)