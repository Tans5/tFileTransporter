package com.tans.tfiletransporter.net.filetransporter

/**
 * All text type data is utf-8 encoding.
 */
enum class FileNetAction(
    // The first byte of action, the modifier of action.
    val actionCode: Byte) {

    /**
     * - 0-4 bytes (Int): The length of the directory path.
     *      example: 20
     *
     * - 4-(length + 4) bytes (String): The folder of the request.
     *      example: /home/user/downloads
     */
    RequestFolderChildrenShare(0x00),

    /**
     * - 0-4 bytes (Int): The length of the folder's children information.
     * - 4-(length + 4) (Json): The folder's children information.
     * example:
     * @see com.tans.tfiletransporter.net.model.ResponseFolderModel
     */
    FolderChildrenShare(0x01),

    /**
     * - 0-4 bytes (Int): The length of the files' information.
     * - 4-(length + 4) (Json): The files' information.
     * example:
     * the list of
     * @see com.tans.tfiletransporter.net.model.File
     */
    RequestFilesShare(0x02),

    /**
     * - 0-4 bytes (Int): The length of the files' information.
     * - 4-(length + 4) (Json): The files' information.
     * example:
     * the list of
     * @see com.tans.tfiletransporter.net.model.File
     */
    FilesShare(0x03),

    /**
     * - 0-4 bytes (Int): The length of the message.
     * - 4-(length + 4) (String): The message.
     *
     */
    SendMessage(0x04)
}