package com.anikoshub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.anikoshub.app.data.Media
import com.anikoshub.app.data.TmdbClient
import com.anikoshub.app.ui.theme.TextMuted

@Composable
fun Poster(
    media: Media,
    onClick: (Media) -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 220
) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick(media) }
    ) {
        val image = media.poster ?: media.backdrop
        if (image != null) {
            AsyncImage(
                model = TmdbClient.IMAGE_BASE + image,
                contentDescription = media.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(height.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF222228)),
                contentAlignment = Alignment.Center
            ) {
                Text("No image", color = TextMuted, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            media.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Text(
            "${media.year} • ★ ${"%.1f".format(media.rating)}",
            fontSize = 12.sp,
            color = TextMuted
        )
    }
}
