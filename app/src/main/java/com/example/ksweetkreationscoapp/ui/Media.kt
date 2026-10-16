package com.example.ksweetkreationscoapp.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ksweetkreationscoapp.AppViewModel
import com.example.ksweetkreationscoapp.data.ApiClient
import com.example.ksweetkreationscoapp.data.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlin.math.min

private fun decode(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}
@Composable fun Photo(api: ApiClient, path: String, description: String, modifier: Modifier = Modifier) {
    var retry by remember(path) { mutableIntStateOf(0) }
    var bitmap by remember(path, retry) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(path, retry) { mutableStateOf(false) }
    LaunchedEffect(path, retry) {
        if (path.isBlank()) { failed = true; return@LaunchedEffect }
        runCatching { val doc = api.document(path); withContext(Dispatchers.IO) { decode(doc.bytes) } }
            .onSuccess { bitmap = it; failed = it == null }.onFailure { failed = true }
    }
    Surface(modifier, color = MaterialTheme.colorScheme.primaryContainer) {
        val image = bitmap
        if (image != null) Image(image.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (failed) IconButton({ retry++ }) { AppIcon("cake", "Retry loading $description") }
            else CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable fun DocumentViewer(vm: AppViewModel, path: String, title: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var document by remember(path) { mutableStateOf<Document?>(null) }
    var renderer by remember(path) { mutableStateOf<PdfRenderer?>(null) }
    var temporary by remember(path) { mutableStateOf<File?>(null) }
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    var error by remember(path) { mutableStateOf("") }
    var page by remember(path) { mutableIntStateOf(0) }
    var retry by remember(path) { mutableIntStateOf(0) }
    LaunchedEffect(path, retry) {
        error = ""
        document = null; bitmap = null; page = 0
        renderer?.let { synchronized(it) { it.close() } }; renderer = null
        temporary?.delete(); temporary = null
        try {
            val downloaded = vm.api.document(path)
            document = downloaded
            if (downloaded.mime == "application/pdf") {
                val file = File.createTempFile("ksweet-proof-", ".pdf", context.cacheDir)
                temporary = file
                withContext(Dispatchers.IO) { file.writeBytes(downloaded.bytes) }
                renderer = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
            } else bitmap = withContext(Dispatchers.IO) { decode(downloaded.bytes) } ?: throw IOException("Unsupported image")
        } catch (e: Exception) { if (e is CancellationException) throw e; error = "This document could not be opened. Check your connection or try again." }
    }
    val pdf = renderer
    DisposableEffect(path) { onDispose { renderer?.let { runCatching { synchronized(it) { it.close() } } }; temporary?.delete() } }
    LaunchedEffect(pdf, page) {
        if (pdf != null) runCatching {
            withContext(Dispatchers.IO) {
                synchronized(pdf) { pdf.openPage(page).use { item ->
                    val scale = min(1200f / item.width, 1600f / item.height)
                    Bitmap.createBitmap((item.width * scale).toInt().coerceAtLeast(1), (item.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(android.graphics.Color.WHITE)
                        item.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                } }
            }
        }.onSuccess { bitmap = it }.onFailure { error = "This PDF page could not be displayed." }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(document?.mime ?: "application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(document?.bytes ?: throw IOException("Document unavailable")) } ?: throw IOException("Cannot save") } }
                .onSuccess { vm.notify("Document saved.") }.onFailure { error = "Could not save the document. Please choose another location." }
        }
    }
    Dialog(onDismissRequest = vm::closeDocument, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(vm::closeDocument) { AppIcon("back", "Close document") }
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    TextButton({ save.launch(if (document?.mime == "application/pdf") "EFT-proof.pdf" else "bakery-image.${if (document?.mime == "image/png") "png" else if (document?.mime == "image/webp") "webp" else "jpg"}") }, enabled = document != null) { Text("Save") }
                }
                if (error.isNotBlank()) { Text(error, color = MaterialTheme.colorScheme.error); SecondaryButton("Retry") { retry++ } }
                val image = bitmap
                if (image != null) Image(image.asImageBitmap(), title, Modifier.fillMaxWidth().weight(1f), contentScale = ContentScale.Fit)
                else if (error.isBlank()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                if (pdf != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ page-- }, enabled = page > 0) { Text("Previous") }
                    Text("${page + 1} / ${pdf.pageCount}")
                    TextButton({ page++ }, enabled = page < pdf.pageCount - 1) { Text("Next") }
                }
            }
        }
    }
}
