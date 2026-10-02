package com.anikoshub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anikoshub.app.data.Episode
import com.anikoshub.app.data.TmdbClient
import com.anikoshub.app.ui.theme.CardBg
import com.anikoshub.app.ui.theme.TextMuted
import com.anikoshub.app.ui.theme.TextSecondary

@Composable
fun EpisodeCard(
    episode: Episode,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (episode.still != null) {
                AsyncImage(
                    model = TmdbClient.IMAGE_BASE + episode.still,
                    contentDescription = episode.name,
                    modifier = Modifier
                        .width(120.dp)
                        .height(70.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    Modifier
                        .width(120.dp)
                        .height(70.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF222228)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("EP ${episode.number}", color = TextMuted, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "E${episode.number} • ${episode.name}",
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp
                )
                if (episode.runtime > 0) {
                    Text(
                        "${episode.runtime} min • ★ ${"%.1f".format(episode.rating)}",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
                if (episode.overview.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        episode.overview,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = onPlay,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("▶")
            }
        }
    }
}
