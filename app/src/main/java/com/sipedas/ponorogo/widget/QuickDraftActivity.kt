package com.sipedas.ponorogo.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.sipedas.ponorogo.data.DraftReport
import com.sipedas.ponorogo.data.SipedasDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class QuickDraftActivity : ComponentActivity() {

    private var initialText = ""
    private var isFocusedAndProcessed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = SipedasDatabase.getDatabase(applicationContext)

        lifecycleScope.launch(Dispatchers.IO) {
            val existing = database.sipedasDao().getDraftReportById("ACTIVE_SESSION_DRAFT")
            initialText = existing?.laporan ?: ""

            withContext(Dispatchers.Main) {
                renderUi(database)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val autoPaste = intent.getBooleanExtra("AUTO_PASTE", false)
        if (hasFocus && autoPaste && !isFocusedAndProcessed) {
            isFocusedAndProcessed = true
            val database = SipedasDatabase.getDatabase(applicationContext)
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboard?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val pasted = clipData.getItemAt(0).text?.toString() ?: ""
                if (pasted.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val existing = database.sipedasDao().getDraftReportById("ACTIVE_SESSION_DRAFT")
                        val currentText = existing?.laporan ?: ""
                        val merged = if (currentText.isEmpty()) pasted else "$currentText\n$pasted"
                        saveDraftAndFinish(database, merged)
                    }
                    Toast.makeText(this@QuickDraftActivity, "Draf ditempel ke Widget!", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            Toast.makeText(this@QuickDraftActivity, "Gagal menempel: Clipboard kosong", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveDraftAndFinish(database: SipedasDatabase, newText: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val timeStr = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            val entity = DraftReport(
                id = "ACTIVE_SESSION_DRAFT",
                laporan = newText,
                danru = "—",
                timestamp = timeStr
            )
            database.sipedasDao().insertDraftReport(entity)

            // Trigger Widget Update via Broadcast
            val appWidgetManager = AppWidgetManager.getInstance(this@QuickDraftActivity)
            val reportComponentName = ComponentName(this@QuickDraftActivity, SipedasReportWidget::class.java)
            val reportIds = appWidgetManager.getAppWidgetIds(reportComponentName)
            if (reportIds.isNotEmpty()) {
                val widgetIntent = Intent(this@QuickDraftActivity, SipedasReportWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, reportIds)
                }
                sendBroadcast(widgetIntent)
            }

            withContext(Dispatchers.Main) {
                finish()
            }
        }
    }

    private fun renderUi(database: SipedasDatabase) {
        setContent {
            var textState by remember { mutableStateOf(initialText) }
            val focusRequester = remember { FocusRequester() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { finish() }, // Tap outside to cancel
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E293B) // Slate 800 for gorgeous professional theme
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clickable(enabled = false) {} // Disable clicks on card body from dismissing
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Tulis Draf",
                                tint = Color(0xFF0EA5E9),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Tulis Draf Laporan",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = textState,
                            onValueChange = { textState = it },
                            placeholder = { Text("Ketik isi draf patroli di sini...", color = Color(0xFF94A3B8)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF0EA5E9),
                                unfocusedBorderColor = Color(0xFF475569),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFF0EA5E9)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .focusRequester(focusRequester),
                            trailingIcon = {
                                if (textState.isNotEmpty()) {
                                    IconButton(onClick = { textState = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cancel button
                            TextButton(
                                onClick = { finish() },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF94A3B8))
                            ) {
                                Text("Batal")
                            }

                            Row {
                                // Paste Button inside Dialog
                                Button(
                                    onClick = {
                                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clipData = clipboard?.primaryClip
                                        if (clipData != null && clipData.itemCount > 0) {
                                            val pasted = clipData.getItemAt(0).text?.toString() ?: ""
                                            if (pasted.isNotEmpty()) {
                                                textState = if (textState.isEmpty()) pasted else "$textState\n$pasted"
                                                Toast.makeText(this@QuickDraftActivity, "Draf ditempel!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(this@QuickDraftActivity, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(this@QuickDraftActivity, "Clipboard kosong atau tidak didukung", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tempel", color = Color.White)
                                }

                                // Save button
                                Button(
                                    onClick = {
                                        saveDraftAndFinish(database, textState)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = "Simpan", tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Simpan", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                try {
                    focusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignored
                }
            }
        }
    }
}
