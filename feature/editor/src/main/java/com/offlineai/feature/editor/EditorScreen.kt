package com.offlineai.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    selectedModelPath: String? = null,
    modifier: Modifier = Modifier
) {
    val text by viewModel.text.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()
    val activeFileName by viewModel.activeFileName.collectAsState()
    val activeFilePath by viewModel.activeFilePath.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()

    val lines = remember(text) { text.split("\n") }
    val lineCount = lines.size

    Column(modifier = modifier.fillMaxSize()) {
        // Editor Toolbar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File Tab & Dirty Indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = (activeFileName ?: "Untitled") + if (isDirty) " ●" else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isDirty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { viewModel.undo() }) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { viewModel.redo() }) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo")
                    }
                    Button(
                        onClick = { viewModel.saveFile() },
                        enabled = isDirty
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                    
                    OutlinedButton(
                        onClick = { viewModel.autoComplete(selectedModelPath) },
                        enabled = !isGenerating && selectedModelPath != null
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Auto-Complete", modifier = Modifier.size(16.dp))
                        if (isGenerating) {
                            Spacer(Modifier.width(4.dp))
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }

        // Warning Banner if No Model File Selected
        if (selectedModelPath == null) {
            Surface(
                color = Color(0xFFFFF3CD),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFF856404),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "No GGUF Model loaded. Open 'Models' tab to select/load a model.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF856404)
                    )
                }
            }
        }

        HorizontalDivider()

        // Code Editor View with Line Numbers
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
        ) {
            val scrollState = rememberScrollState()

            // Line Numbers Column
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .background(Color(0xFF252526))
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                for (i in 1..lineCount) {
                    Text(
                        text = "$i",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color(0xFF858585),
                            textAlign = TextAlign.End
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF333333))
            )

            // Text Input Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .padding(8.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { viewModel.onTextChange(it) },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Color(0xFFD4D4D4),
                        lineHeight = 20.sp
                    ),
                    visualTransformation = CodeSyntaxTransformation(),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        HorizontalDivider()

        // Status Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = activeFilePath ?: "No file loaded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Lines: $lineCount | Chars: ${text.length} | UTF-8",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

class CodeSyntaxTransformation : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val original = text.text
        val annotated = buildAnnotatedString {
            // Very simple Regex-based highlighting
            // Matches strings "..." or '...'
            val stringRegex = Regex("(\"[^\"]*\")|('[^']*')")
            // Matches standard keywords
            val keywordRegex = Regex("\\b(function|var|let|const|class|return|if|else|import|export|from|val|fun|class|<!DOCTYPE html>|<html>|<body>|<head>|<title>|<script>|<style>|</div>|<div>|<span>|</span>|</a>|<a>)\\b")
            // Matches comments // or /* ... */
            val commentRegex = Regex("(//.*)|(/\\*[\\s\\S]*?\\*/)")
            
            var lastIndex = 0
            
            // We apply spans greedily. A robust parser would be better, but this works for simple syntax
            val allMatches = (stringRegex.findAll(original) + keywordRegex.findAll(original) + commentRegex.findAll(original))
                .sortedBy { it.range.first }
                .toList()
                
            var currentIndex = 0
            while (currentIndex < original.length) {
                val match = allMatches.firstOrNull { it.range.first >= currentIndex }
                if (match == null) {
                    append(original.substring(currentIndex))
                    break
                }
                
                if (match.range.first > currentIndex) {
                    append(original.substring(currentIndex, match.range.first))
                }
                
                val matchText = match.value
                val color = when {
                    matchText.startsWith("//") || matchText.startsWith("/*") -> Color(0xFF6A9955) // Comments (Green)
                    matchText.startsWith("\"") || matchText.startsWith("'") -> Color(0xFFCE9178) // Strings (Orange)
                    else -> Color(0xFF569CD6) // Keywords (Blue)
                }
                
                withStyle(SpanStyle(color = color)) {
                    append(matchText)
                }
                currentIndex = match.range.last + 1
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}
