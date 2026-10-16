package com.example.ksweetkreationscoapp.ui

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ksweetkreationscoapp.domain.BakeryRules
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.provider.OpenableColumns
import android.net.Uri

@Composable fun AppIcon(name: String, description: String? = null, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp).then(if (description == null) Modifier else Modifier.semantics { contentDescription = description })) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            fun line(a: Float, b: Float, c: Float, d: Float) = drawLine(color, Offset(a,b), Offset(c,d), 1.8f, StrokeCap.Round)
            fun path(vararg points: Float) { val p = Path(); p.moveTo(points[0], points[1]); for (i in 2 until points.size step 2) p.lineTo(points[i], points[i+1]); drawPath(p, color, style = Stroke(1.8f, cap = StrokeCap.Round)) }
            when (name) {
                "menu" -> { line(4f,6f,20f,6f); line(4f,12f,20f,12f); line(4f,18f,20f,18f) }
                "close" -> { line(6f,6f,18f,18f); line(6f,18f,18f,6f) }
                "back" -> { path(14f,5f,7f,12f,14f,19f); line(7f,12f,21f,12f) }
                "home" -> { path(3f,10f,12f,3f,21f,10f); path(5f,9f,5f,21f,10f,21f,10f,14f,14f,14f,14f,21f,19f,21f,19f,9f) }
                "account" -> { drawCircle(color,4f,Offset(12f,7f),style=Stroke(1.8f)); val p=Path();p.moveTo(4f,21f);p.cubicTo(4f,12f,20f,12f,20f,21f);drawPath(p,color,style=Stroke(1.8f)) }
                "cart" -> { path(5f,7f,19f,7f,20f,21f,4f,21f,5f,7f); val p=Path();p.moveTo(8f,7f);p.cubicTo(8f,0f,16f,0f,16f,7f);drawPath(p,color,style=Stroke(1.8f)) }
                "orders", "requests" -> { path(5f,3f,19f,3f,19f,21f,5f,21f,5f,3f); line(8f,8f,16f,8f);line(8f,12f,16f,12f);line(8f,16f,13f,16f) }
                "cake" -> { path(3f,12f,21f,12f,21f,21f,3f,21f,3f,12f);path(6f,12f,6f,8f,18f,8f,18f,12f);line(9f,8f,9f,4f);line(15f,8f,15f,4f);line(3f,16f,21f,16f) }
                "eye", "eye-off" -> { val p=Path();p.moveTo(2f,12f);p.cubicTo(7f,3f,17f,3f,22f,12f);p.cubicTo(17f,21f,7f,21f,2f,12f);drawPath(p,color,style=Stroke(1.8f));drawCircle(color,3f,Offset(12f,12f),style=Stroke(1.8f));if(name=="eye-off")line(3f,3f,21f,21f) }
                "plus" -> {line(5f,12f,19f,12f);line(12f,5f,12f,19f)}
                "minus" -> line(5f,12f,19f,12f)
                "refresh" -> { val p=Path();p.moveTo(20f,10f);p.cubicTo(17f,0f,2f,2f,4f,14f);p.cubicTo(7f,24f,20f,22f,21f,14f);drawPath(p,color,style=Stroke(1.8f));path(15f,9f,21f,10f,21f,4f) }
                "delete" -> {line(4f,6f,20f,6f);path(6f,6f,7f,21f,17f,21f,18f,6f);path(9f,6f,9f,3f,15f,3f,15f,6f);line(10f,10f,10f,17f);line(14f,10f,14f,17f)}
                "search" -> {drawCircle(color,7f,Offset(10f,10f),style=Stroke(1.8f));line(15f,15f,21f,21f)}
                else -> path(5f,12f,10f,17f,20f,6f)
            }
        }
    }
}
@Composable fun Page(title: String, subtitle: String = "", content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content(); Spacer(Modifier.height(12.dp))
    }
    }
}
@Composable fun Panel(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
@Composable fun EmptyCard(title: String, detail: String = "") = Panel(title) { if (detail.isNotBlank()) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable fun StatusPill(text: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
@Composable fun ActionButton(text: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, modifier = Modifier.defaultMinSize(minHeight = 48.dp), colors = if (danger) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()) { Text(text) }
}
@Composable fun SecondaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick, enabled = enabled, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(text) }
}
@Composable fun SearchField(value: String, onChange: (String) -> Unit, label: String = "Search") {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, leadingIcon = { AppIcon("search") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
}
@Composable fun Choices(label: String, options: List<String>, value: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton({ open = true }, enabled = enabled && options.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp)) {
                Text(value.ifBlank { "Choose ${label.lowercase()}" }, Modifier.weight(1f)); Text("⌄")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                options.forEach { choice -> DropdownMenuItem(text = { Text(choice) }, onClick = { onChange(choice); open = false }) }
            }
        }
    }
}
@Composable fun ChoiceChips(options: List<String>, value: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { FilterChip(selected = value == it, onClick = { onChange(it) }, label = { Text(it) }) }
    }
}
@Composable fun CollectionPicker(date: String, time: String, custom: Boolean, busy: Boolean, setDate: (String) -> Unit, setTime: (String) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Preferred collection", style = MaterialTheme.typography.titleMedium)
        SecondaryButton(if (date.isBlank()) "Choose collection date" else date, !busy) {
            val selected = runCatching { LocalDate.parse(date) }.getOrElse { BakeryRules.today().plusDays(if (custom) 7 else 1) }
            DatePickerDialog(context, { _, y, m, d -> setDate(LocalDate.of(y, m+1, d).toString()); setTime("") }, selected.year, selected.monthValue-1, selected.dayOfMonth).apply {
                datePicker.minDate = BakeryRules.today().plusDays(if (custom) 7 else 1).atStartOfDay(BakeryRules.zone).toInstant().toEpochMilli()
            }.show()
        }
        Choices("Collection time", BakeryRules.slots(date), time, !busy, setTime)
        Text("Collection only · Lenasia South. Mon–Fri 09:00–17:00; Sat 09:00–15:00. Closed Sundays.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun AttachmentPicker(value: String, onChange: (String) -> Unit, proof: Boolean = false, enabled: Boolean = true) {
    val context = LocalContext.current
    var fileName by remember(value) { mutableStateOf("") }
    LaunchedEffect(value) {
        if (value.isNotBlank()) fileName = withContext(Dispatchers.IO) { runCatching {
            context.contentResolver.query(Uri.parse(value), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: "File selected" }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { runCatching { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }; onChange(it.toString()) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SecondaryButton(if (value.isBlank()) if (proof) "Choose payment proof" else "Choose photograph" else "Replace selected file", enabled) {
            picker.launch(if (proof) arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp") else arrayOf("image/jpeg", "image/png", "image/webp"))
        }
        Text(if (proof) "PDF / JPG / PNG / WebP · up to 5 MB" else "JPG / PNG / WebP · up to 2 MB", style = MaterialTheme.typography.bodySmall)
        if (value.isNotBlank()) { Text(fileName.ifBlank { "File selected" }, style = MaterialTheme.typography.labelMedium); TextButton({ onChange("") }, enabled = enabled) { Text("Remove file") } }
    }
}
data class InputSpec(val key: String, val label: String, val initial: String = "", val required: Boolean = true,
                     val max: Int = 100, val min: Int = 1, val password: Boolean = false, val multiline: Boolean = false,
                     val keyboard: KeyboardType = KeyboardType.Text)
@Composable fun FormCard(formKey: String, title: String, fields: List<InputSpec>, busy: Boolean, error: String = "",
                         submit: String, extra: @Composable ColumnScope.() -> Unit = {}, validate: (Map<String, String>) -> String? = { null },
                         onSubmit: (Map<String, String>, () -> Unit) -> Unit) {
    var values by rememberSaveable(formKey) { mutableStateOf(fields.filterNot { it.password }.associate { it.key to it.initial }) }
    var secrets by remember(formKey) { mutableStateOf(mapOf<String,String>()) }
    var problem by remember(formKey) { mutableStateOf("") }
    Panel(title) {
        fields.forEach { field ->
            val value = (if (field.password) secrets else values)[field.key].orEmpty()
            var revealed by remember(formKey, field.key) { mutableStateOf(false) }
            OutlinedTextField(value, { next -> if (next.length <= field.max) {
                if (field.password) secrets = secrets + (field.key to next) else values = values + (field.key to next); problem = ""
            } }, label = { Text(field.label) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("field-${field.key}"),
                singleLine = !field.multiline, minLines = if (field.multiline) 3 else 1,
                visualTransformation = if (field.password && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (field.password) KeyboardType.Password else field.keyboard, autoCorrectEnabled = false),
                trailingIcon = if (field.password) ({ IconButton({ revealed = !revealed }) { AppIcon(if (revealed) "eye-off" else "eye", (if (revealed) "Hide " else "Show ") + field.label.lowercase()) } }) else null,
                shape = RoundedCornerShape(12.dp))
        }
        extra()
        if (problem.isNotBlank()) Text(problem, color = MaterialTheme.colorScheme.error)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        ActionButton(if (busy) "Saving…" else submit, !busy) {
            val merged = values + secrets
            problem = fields.firstOrNull { field -> val value = merged[field.key].orEmpty()
                (field.required && (if (field.password) value else value.trim()).length < field.min) || (value.isNotEmpty() && value.length < field.min)
            }?.let { "Please enter ${it.label.lowercase()}${if (it.min > 1) " (${it.min} characters minimum)" else ""}." } ?: validate(merged).orEmpty()
            if (problem.isBlank()) onSubmit(merged) { values = fields.filterNot { it.password }.associate { it.key to it.initial }; secrets = emptyMap(); problem = "" }
        }
    }
}
