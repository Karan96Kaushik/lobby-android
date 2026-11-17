package com.bayonetbarontech.lobby.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bayonetbarontech.lobby.legal.LegalDocuments
import com.bayonetbarontech.lobby.legal.LegalKind

@Composable
fun LegalScreen(kind: LegalKind, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val sections = LegalDocuments.sections(kind)
    Column(
        Modifier
            .fillMaxSize()
            .background(LobbyBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose) {
                Text("Back", color = LobbyAccent, fontSize = 18.sp)
            }
            Text(
                LegalDocuments.title(kind),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                "${LegalDocuments.DEVELOPER} · ${LegalDocuments.EFFECTIVE}",
                color = Color.White.copy(alpha = 0.6f)
            )
            sections.forEach { section ->
                Spacer(Modifier.height(20.dp))
                Text(section.heading, color = LobbyAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                section.paragraphs.forEach { paragraph ->
                    Spacer(Modifier.height(8.dp))
                    Text(paragraph, color = Color.White, lineHeight = 22.sp)
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
