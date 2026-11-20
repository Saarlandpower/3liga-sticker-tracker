package de.dritteliga.stickertracker.ui.album

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dritteliga.stickertracker.domain.Sticker
import de.dritteliga.stickertracker.domain.StickerStatus
import de.dritteliga.stickertracker.ui.theme.StatusColors

fun statusColor(status: StickerStatus, dark: Boolean): Color = when (status) {
    StickerStatus.FEHLT -> if (dark) StatusColors.MissingDark else StatusColors.Missing.copy(alpha = 0.35f)
    StickerStatus.VORHANDEN -> StatusColors.Owned
    StickerStatus.DOPPELT -> StatusColors.Duplicate
}

fun statusText(s: Sticker): String = when (s.status) {
    StickerStatus.FEHLT -> "fehlt"
    StickerStatus.VORHANDEN -> "vorhanden"
    StickerStatus.DOPPELT -> "doppelt, ${s.count}x"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StickerTile(sticker: Sticker, dark: Boolean, onTap: () -> Unit, onLongPress: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val bg = statusColor(sticker.status, dark)
    val fg = if (sticker.status == StickerStatus.FEHLT) MaterialTheme.colorScheme.onSurfaceVariant else StatusColors.OnStatus
    Box(
        Modifier
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .combinedClickable(
                onClick = onTap,
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongPress() },
            )
            .semantics { contentDescription = "Sticker ${sticker.number}, ${statusText(sticker)}" },
    ) {
        Column(
            Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                sticker.number,
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = if (sticker.number.length > 3) 15.sp else 19.sp,
                maxLines = 1,
            )
            if (sticker.name.isNotEmpty()) {
                Text(
                    sticker.name,
                    color = fg.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (sticker.status == StickerStatus.DOPPELT) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text("+${sticker.duplicates}", color = StatusColors.Duplicate, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
