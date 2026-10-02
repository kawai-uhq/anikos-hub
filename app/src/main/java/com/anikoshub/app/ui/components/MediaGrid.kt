package com.anikoshub.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anikoshub.app.data.Media

@Composable
fun MediaSection(
    title: String,
    list: List<Media>,
    onOpen: (Media) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        if (list.isEmpty()) {
            Text("Nothing here yet.", color = com.anikoshub.app.ui.theme.TextMuted)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.key }) { media ->
                    Poster(
                        media = media,
                        onClick = onOpen,
                        modifier = Modifier.width(140.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MediaGrid(
    list: List<Media>,
    onOpen: (Media) -> Unit,
    modifier: Modifier = Modifier
) {
    if (list.isEmpty()) {
        Box(
            modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text("No results", color = com.anikoshub.app.ui.theme.TextMuted)
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(list.chunked(2), key = { row -> row.joinToString { it.key } }) { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { media ->
                    Poster(
                        media = media,
                        onClick = onOpen,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
