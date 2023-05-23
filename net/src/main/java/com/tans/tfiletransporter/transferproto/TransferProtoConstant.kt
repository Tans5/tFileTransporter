package com.tans.tfiletransporter.transferproto

object TransferProtoConstant {
    const val VERSION: Int = 20230523

    /**
     * P2P Connect
     */
    const val P2P_GROUP_OWNER_PORT = 1996

    /**
     * Broadcast Connection
     */
    const val BROADCAST_SCANNER_PORT = 1997
    const val BROADCAST_TRANSFER_SERVER_PORT = 1998
    const val BROADCAST_TRANSFER_CLIENT_PORT = 1999

    /**
     * File Explore
     */
    const val FILE_EXPLORE_PORT = 2000

    /**
     * File Transfer
     */
    const val FILE_TRANSFER_PORT = 2001

    /**
     * QR code scan connection
     */
    const val QR_CODE_SCAN_SERVER_PORT = 2002
}