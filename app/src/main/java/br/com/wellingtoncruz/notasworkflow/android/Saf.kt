// NotasWorkflow Android - Saf.kt
// The Android storage: the data file as a document chosen by the person (for example the
// NotasWorkflowNVDA-data.json of the NVDA add-on in Google Drive or OneDrive), folders chosen to
// export one file per note, and reading the documents to import.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import br.com.wellingtoncruz.notasworkflow.core.DataFile
import br.com.wellingtoncruz.notasworkflow.core.ExportFolder
import br.com.wellingtoncruz.notasworkflow.core.NotesStore
import br.com.wellingtoncruz.notasworkflow.core.StoreException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Saf {
	/** Name shown for a document (its file name), or the last part of the address. */
	fun displayName(context: Context, uri: Uri): String {
		try {
			context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
				if (c.moveToFirst() && !c.isNull(0)) return c.getString(0)
			}
		} catch (e: Exception) {
			// Some providers do not answer queries; the address is used.
		}
		return uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
	}

	/** Bytes of a document, refusing it when it is bigger than limit. Raises IOException. */
	fun readBytes(resolver: ContentResolver, uri: Uri, limit: Long): ByteArray {
		val input: InputStream = resolver.openInputStream(uri) ?: throw IOException("no stream")
		return input.use { readLimited(it, limit) }
	}

	fun readLimited(input: InputStream, limit: Long): ByteArray {
		val out = ByteArrayOutputStream()
		val buf = ByteArray(64 * 1024)
		var total = 0L
		while (true) {
			val n = input.read(buf)
			if (n < 0) break
			total += n
			if (total > limit) throw TooBigException()
			out.write(buf, 0, n)
		}
		return out.toByteArray()
	}

	class TooBigException : IOException("too big")

	fun openOutput(resolver: ContentResolver, uri: Uri): OutputStream =
		try {
			resolver.openOutputStream(uri, "wt")
		} catch (e: Exception) {
			// Some providers do not know "wt" (write and truncate).
			null
		} ?: resolver.openOutputStream(uri, "w") ?: throw IOException("no stream")

	/** Keeps the right to read and write a document after the app restarts. */
	fun keepPermission(context: Context, uri: Uri, write: Boolean = true) {
		val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
			(if (write) android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
		try {
			context.contentResolver.takePersistableUriPermission(uri, flags)
		} catch (e: SecurityException) {
			// The provider does not offer persistable permissions: it works while the app is open.
		}
	}
}

/** The data file as a document (Google Drive, OneDrive, a folder of the phone...). Its backup copy
 * and damaged files are kept in the app's own folder, as a document cannot be renamed safely. */
class SafDataFile(private val context: Context, val uri: Uri) : DataFile {
	private val resolver get() = context.contentResolver
	private val backup: File get() = File(context.filesDir, "documento-folhas.json.bak")

	override fun read(): ByteArray? {
		return try {
			Saf.readBytes(resolver, uri, NotesStore.MAX_FILE_SIZE)
		} catch (e: Saf.TooBigException) {
			throw StoreException("tooLarge")
		} catch (e: java.io.FileNotFoundException) {
			null
		}
	}

	override fun write(bytes: ByteArray) {
		// The current document becomes the backup copy before it is replaced.
		try {
			val current = Saf.readBytes(resolver, uri, NotesStore.MAX_FILE_SIZE)
			if (current.isNotEmpty()) backup.writeBytes(current)
		} catch (e: Exception) {
			// No current document (or unreadable): nothing to keep.
		}
		Saf.openOutput(resolver, uri).use { out ->
			out.write(bytes)
			out.flush()
		}
	}

	override fun readBackup(): ByteArray? = if (backup.exists()) backup.readBytes() else null

	override fun keepDamaged(): String? {
		return try {
			val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
			val target = File(context.filesDir, "NotasWorkflow-data.corrupt-$stamp.json")
			target.writeBytes(Saf.readBytes(resolver, uri, NotesStore.MAX_FILE_SIZE))
			target.name
		} catch (e: Exception) {
			null
		}
	}

	override fun stamp(): Any? {
		return try {
			resolver.query(
				uri,
				arrayOf(OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
				null, null, null,
			)?.use { c ->
				if (!c.moveToFirst()) return null
				Pair(if (c.isNull(0)) -1L else c.getLong(0), if (c.isNull(1)) -1L else c.getLong(1))
			}
		} catch (e: Exception) {
			null
		}
	}
}

/** A folder chosen by the person (OpenDocumentTree), where exported files are created. */
class SafExportFolder(
	private val context: Context,
	private val treeUri: Uri,
	private val documentUri: Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)),
	override val displayName: String = Saf.displayName(context, documentUri),
) : ExportFolder {
	private val resolver get() = context.contentResolver

	override fun existingNames(): Set<String> {
		val out = HashSet<String>()
		val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getDocumentId(documentUri))
		try {
			resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
				while (c.moveToNext()) if (!c.isNull(0)) out.add(c.getString(0).lowercase())
			}
		} catch (e: Exception) {
			// An empty set: the provider itself renames on a clash.
		}
		return out
	}

	override fun createFolder(name: String): ExportFolder {
		val uri = DocumentsContract.createDocument(resolver, documentUri, DocumentsContract.Document.MIME_TYPE_DIR, name)
			?: throw IOException("folder not created")
		return SafExportFolder(context, treeUri, uri, name)
	}

	override fun createFile(name: String, mimeType: String): OutputStream {
		val uri = DocumentsContract.createDocument(resolver, documentUri, mimeType, name) ?: throw IOException("file not created")
		return Saf.openOutput(resolver, uri)
	}
}
