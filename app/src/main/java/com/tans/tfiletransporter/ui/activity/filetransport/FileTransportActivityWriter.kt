package com.tans.tfiletransporter.ui.activity.filetransport

import android.app.Activity
import com.tans.tfiletransporter.file.CommonFileLeaf
import com.tans.tfiletransporter.file.DirectoryFileLeaf
import com.tans.tfiletransporter.file.FileConstants
import com.tans.tfiletransporter.net.NET_BUFFER_SIZE
import com.tans.tfiletransporter.net.filetransporter.*
import com.tans.tfiletransporter.net.model.File
import com.tans.tfiletransporter.net.model.Folder
import com.tans.tfiletransporter.net.model.ResponseFolderModel
import com.tans.tfiletransporter.ui.activity.commomdialog.showLoadingDialog
import com.tans.tfiletransporter.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.threeten.bp.Instant
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.ZoneId
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlin.streams.toList

suspend fun Activity.newRequestFolderChildrenShareWriterHandle(
    path: String
): RequestFolderChildrenShareWriterHandle {
    val pathData = path.toByteArray(Charsets.UTF_8)
    return RequestFolderChildrenShareWriterHandle(
        pathSize = pathData.size
    ) { outputStream ->
        val dialog = withContext(Dispatchers.Main) { showLoadingDialog() }
        val writer = Channels.newChannel(outputStream)
        val buffer = ByteBuffer.allocate(pathData.size)
        writer.writeSuspendSize(buffer, pathData)
        withContext(Dispatchers.Main) { dialog.cancel() }
    }
}

suspend fun Activity.newFolderChildrenShareWriterHandle(
    parentPath: String
): FolderChildrenShareWriterHandle {
    val json = withContext(Dispatchers.IO) {
        val path = Paths.get(FileConstants.homePathString + parentPath)
        val children = Files.list(path)
            .map { p ->
                val name = p.fileName.toString()
                val lastModify = OffsetDateTime.ofInstant(Instant.ofEpochMilli(Files.getLastModifiedTime(p).toMillis()), ZoneId.systemDefault())
                val pathString = if (parentPath.endsWith(FileConstants.FILE_SEPARATOR)) parentPath + name else parentPath + FileConstants.FILE_SEPARATOR + name
                if (Files.isDirectory(p)) {
                    Folder(
                        name = name,
                        path = pathString,
                        childCount = p.let {
                            val s = Files.list(it)
                            val size = s.count()
                            s.close()
                            size
                        },
                        lastModify = lastModify
                    )
                } else {
                    File(
                        name = name,
                        path = pathString,
                        size = Files.size(p),
                        lastModify = lastModify
                    )
                }
            }.toList()
        val responseFolder = ResponseFolderModel(
            path = parentPath,
            childrenFiles = children.filterIsInstance<File>(),
            childrenFolders = children.filterIsInstance<Folder>()
        )
        FolderChildrenShareWriterHandle.getJsonString(responseFolder)
    }

    val jsonData = json.toByteArray(Charsets.UTF_8)
    println("Send json string: bytes size: ${jsonData.size}, $json")

    return FolderChildrenShareWriterHandle(
        filesJsonSize = jsonData.size
    ) { outputStream ->
        val dialog = withContext(Dispatchers.Main) { showLoadingDialog() }
        val writer = Channels.newChannel(outputStream)
        val byteBuffer = ByteBuffer.allocate(jsonData.size)
        writer.writeSuspendSize(byteBuffer, jsonData)
        withContext(Dispatchers.Main) { dialog.cancel() }
    }
}

suspend fun Activity.newRequestFilesShareWriterHandle(
    files: List<File>
): RequestFilesShareWriterHandle {
    val jsonData = FilesShareWriterHandle.getJsonString(files).toByteArray(Charsets.UTF_8)
    return RequestFilesShareWriterHandle(
        filesJsonDataSize = jsonData.size
    ) { outputStream ->
        val dialog = withContext(Dispatchers.Main) { showLoadingDialog() }
        val writer = Channels.newChannel(outputStream)
        val byteBuffer = ByteBuffer.allocate(jsonData.size)
        writer.writeSuspendSize(byteBuffer, jsonData)
        withContext(Dispatchers.Main) { dialog.cancel() }
    }
}

// TODO: Add loading dialog.
suspend fun Activity.newFilesShareWriterHandle(
    fileLeafs: List<CommonFileLeaf>
): FilesShareWriterHandle {
    return FilesShareWriterHandle(
        fileLeafs.map { it.toFile() }
    ) { files, outputStream ->
        val writer = Channels.newChannel(outputStream)
        val buffer = ByteBuffer.allocate(NET_BUFFER_SIZE)
        val bufferSize = NET_BUFFER_SIZE
        files.map { f ->
            val reader = FileChannel.open(Paths.get(FileConstants.homePathString + f.path), StandardOpenOption.READ)
            reader.use {
                val limit = f.size
                var readSize = 0L
                while (true) {
                    buffer.clear()
                    val thisTimeReadSize = if (readSize + bufferSize >= limit) {
                        (limit - readSize).toInt()
                    } else {
                        bufferSize
                    }

                    reader.readSuspendSize(buffer, thisTimeReadSize)
                    writer.writeSuspendSize(buffer, buffer.copyAvailableBytes())
                    readSize += thisTimeReadSize
                    if (readSize >= limit) {
                        break
                    }

                }
            }
        }
    }
}

suspend fun Activity.newSendMessageShareWriterHandle(
    message: String
): SendMessageShareWriterHandle {
    val messageData = message.toByteArray(Charsets.UTF_8)
    return SendMessageShareWriterHandle(
        messageSize = messageData.size
    ) { outputStream ->
        val dialog = withContext(Dispatchers.Main) { showLoadingDialog() }
        val writer = Channels.newChannel(outputStream)
        writer.writeSuspend(ByteBuffer.wrap(messageData))
        withContext(Dispatchers.Main) { dialog.cancel() }
    }
}

fun CommonFileLeaf.toFile(): File {
    return File(
        name = name,
        path = path,
        size = size,
        lastModify = OffsetDateTime.ofInstant(Instant.ofEpochMilli(lastModified), ZoneId.systemDefault())
    )
}

fun File.toFileLeaf(): CommonFileLeaf {
    return CommonFileLeaf(
        name = name,
        path = path,
        size = size,
        lastModified = lastModify.toInstant().toEpochMilli()
    )
}
