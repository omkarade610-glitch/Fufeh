package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.UiState
import com.example.services.MediaExportHelper
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.GlassPanel
import com.example.ui.components.SetupRequiredBanner
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.StudioSelectorStrip
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.NeonViolet
import com.example.viewmodel.GenerationState
import com.example.viewmodel.ImageResultState
import com.example.viewmodel.JarvisViewModel
import kotlinx.coroutines.launch
import java.io.File

private val ANIME_GENRES = listOf(
    "Action", "Adventure", "Fantasy", "Mystery",
    "School Life", "Comedy", "Science Fiction", "Drama", "Supernatural"
)

private val IMAGE_CATEGORIES = listOf(
    "Anime Art", "Pencil Sketch", "Fantasy Art", "Portrait", "Wallpaper", "Movie Poster", "Background"
)

private val ASPECT_RATIOS = listOf("1:1", "3:4", "4:3", "16:9", "9:16")

@Composable
fun CreateScreen(
    viewModel: JarvisViewModel,
    selectedStudio: Int,
    onSelectStudio: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val animeSeriesState by viewModel.animeSeriesState.collectAsStateWithLifecycle()
    val mangaScriptState by viewModel.mangaScriptState.collectAsStateWithLifecycle()
    val mangaArtState by viewModel.mangaArtState.collectAsStateWithLifecycle()
    val generalImageState by viewModel.generalImageState.collectAsStateWithLifecycle()
    val storyboardState by viewModel.storyboardState.collectAsStateWithLifecycle()
    val artworksState by viewModel.artworks.collectAsStateWithLifecycle()

    // Anime Series Studio inputs
    var seriesTitle by remember { mutableStateOf("Kurokaze: Echoes of Shibuya") }
    var seriesGenre by remember { mutableStateOf("Action") }
    var seriesPremise by remember { mutableStateOf("A young street artist discovers his graphite sketches awaken guardian spirits in neon Tokyo.") }
    var seriesSeasons by remember { mutableIntStateOf(2) }

    // Manga inputs
    var mangaTitle by remember { mutableStateOf("Ink Blade Zero") }
    var mangaGenre by remember { mutableStateOf("Supernatural") }
    var mangaPremise by remember { mutableStateOf("A lone high-school swordsman faces a shadow clan on a rainy Tokyo rooftop.") }
    var renderMangaArt by remember { mutableStateOf(true) }

    // General Image inputs
    var imgPrompt by remember { mutableStateOf("Futuristic cyberpunk Tokyo rain alley with electric cyan neon reflections, ultra detailed anime wallpaper") }
    var imgCategory by remember { mutableStateOf("Anime Art") }
    var imgAspect by remember { mutableStateOf("16:9") }

    // Video Storyboard inputs
    var videoConcept by remember { mutableStateOf("An anime hero ignites cyan lightning across a midnight skyline before leaping into battle") }
    var videoStyle by remember { mutableStateOf("Cinematic Japanese Anime Trailer") }

    // Gallery filter
    var favoritesOnly by remember { mutableStateOf(false) }

    val studioTabs = listOf(
        "Anime Sketch" to Icons.Filled.Brush,
        "Anime Series" to Icons.Filled.Movie,
        "Manga Creator" to Icons.Filled.Book,
        "Image Studio" to Icons.Filled.Image,
        "Video Studio" to Icons.Filled.Videocam,
        "Art Gallery" to Icons.Filled.Collections
    )

    Column(modifier = Modifier.fillMaxSize()) {
        StudioSelectorStrip(
            items = studioTabs,
            selectedIndex = selectedStudio,
            onSelect = onSelectStudio,
            testTagPrefix = "create_studio_tab"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedStudio) {
                0 -> item { AnimeSketchStudioSection(viewModel) }

                1 -> {
                    // JAPANESE ANIME MOVIE & SERIES CREATOR
                    item {
                        GlassPanel(modifier = Modifier.fillMaxWidth()) {
                            Text("Japanese Anime Movie & Series Studio", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Generate original world building, hero/villain cast, multi-season episodes, dialogue, trailer scripts, voice-over & posters.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = seriesTitle,
                                onValueChange = { seriesTitle = it },
                                label = { Text("Anime Series / Movie Title") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ANIME_GENRES) { g ->
                                    FilterChip(selected = seriesGenre == g, onClick = { seriesGenre = g }, label = { Text(g) })
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = seriesPremise,
                                onValueChange = { seriesPremise = it },
                                label = { Text("Story Premise & World Concept") },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 2, 3, 4).forEach { s ->
                                    FilterChip(selected = seriesSeasons == s, onClick = { seriesSeasons = s }, label = { Text("$s Season${if (s > 1) "s" else ""}") })
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { viewModel.generateAnimeSeries(seriesTitle, seriesGenre, seriesPremise, seriesSeasons) },
                                    enabled = animeSeriesState !is GenerationState.Loading,
                                    modifier = Modifier.weight(1f).height(50.dp).testTag("generate_anime_series_btn")
                                ) {
                                    Text("Generate Series Bible")
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.generateGeneralImage(
                                            context = context,
                                            prompt = "Official-style original Japanese anime key visual movie poster for '$seriesTitle' ($seriesGenre): $seriesPremise. Dramatic theatrical lighting, bold cel-shaded anime art, no text.",
                                            category = "Movie Poster",
                                            aspectRatio = "3:4"
                                        )
                                        onSelectStudio(3)
                                    },
                                    modifier = Modifier.height(50.dp)
                                ) {
                                    Text("Poster")
                                }
                            }
                        }
                    }

                    item {
                        when (val s = animeSeriesState) {
                            GenerationState.Idle -> {}
                            GenerationState.Loading -> LoadingCard("Directing original Japanese anime bible, cast & episode scripts...")
                            is GenerationState.Error -> ErrorBannerCard(s.message)
                            is GenerationState.Success -> {
                                GlassPanel(modifier = Modifier.fillMaxWidth(), accentColor = NeonViolet) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(seriesTitle, style = MaterialTheme.typography.titleMedium)
                                        IconButton(onClick = { MediaExportHelper.shareTextDocument(context, seriesTitle, s.data) }) {
                                            Icon(Icons.Filled.Share, contentDescription = "Export anime bible")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(s.data, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // MANGA & COMIC CREATOR
                    item {
                        GlassPanel(modifier = Modifier.fillMaxWidth()) {
                            Text("Japanese Manga & Comic Creator", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Create structured multi-panel manga scripts with speech bubbles, expressions, Japanese SFX, and optional B&W inked page art.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(value = mangaTitle, onValueChange = { mangaTitle = it }, label = { Text("Manga Title") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ANIME_GENRES) { g ->
                                    FilterChip(selected = mangaGenre == g, onClick = { mangaGenre = g }, label = { Text(g) })
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = mangaPremise, onValueChange = { mangaPremise = it }, label = { Text("Chapter Scene Premise") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Also render B&W Manga Page Art", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = renderMangaArt, onCheckedChange = { renderMangaArt = it })
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.generateMangaPage(context, mangaTitle, mangaGenre, mangaPremise, renderMangaArt) },
                                enabled = mangaScriptState !is GenerationState.Loading,
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("generate_manga_btn")
                            ) {
                                Text("Generate Manga Page & Panels", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    item {
                        when (val ms = mangaScriptState) {
                            GenerationState.Idle -> {}
                            GenerationState.Loading -> LoadingCard("Drafting Japanese manga panel layout & dialogue bubbles...")
                            is GenerationState.Error -> ErrorBannerCard(ms.message)
                            is GenerationState.Success -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ms.data.forEach { panel ->
                                        GlassPanel(modifier = Modifier.fillMaxWidth(), accentColor = ElectricCyan) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("PANEL ${panel.panelNumber} • ${panel.shotType}", style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                                                StatusPillBadge(label = panel.soundEffect.take(18), color = NeonViolet)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(panel.visualDescription, style = MaterialTheme.typography.bodyMedium)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("Speech Bubble: \"${panel.dialogue}\"", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        RenderImageResultBlock(mangaArtState) { bmp, title ->
                            scope.launch {
                                val res = MediaExportHelper.exportBitmapToGallery(context, bmp, title)
                                viewModel.showToast(if (res.isSuccess) "Exported manga page PNG" else "Export failed")
                            }
                        }
                    }
                }

                3 -> {
                    // GENERAL AI IMAGE GENERATOR
                    item {
                        GlassPanel(modifier = Modifier.fillMaxWidth()) {
                            Text("AI Image Generator (Nano Banana 2.1)", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(IMAGE_CATEGORIES) { cat ->
                                    FilterChip(selected = imgCategory == cat, onClick = { imgCategory = cat }, label = { Text(cat) })
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ASPECT_RATIOS) { ar ->
                                    FilterChip(selected = imgAspect == ar, onClick = { imgAspect = ar }, label = { Text(ar) })
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = imgPrompt,
                                onValueChange = { imgPrompt = it },
                                label = { Text("Image Prompt") },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth().testTag("general_image_prompt_input")
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.generateGeneralImage(context, imgPrompt, imgCategory, imgAspect) },
                                enabled = generalImageState !is ImageResultState.Loading && imgPrompt.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("generate_general_image_btn")
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate $imgAspect Artwork", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    item {
                        RenderImageResultBlock(generalImageState) { bmp, title ->
                            scope.launch {
                                val res = MediaExportHelper.exportBitmapToGallery(context, bmp, title)
                                viewModel.showToast(if (res.isSuccess) "Saved PNG to Photos" else "Export failed")
                            }
                        }
                    }
                }

                4 -> {
                    // AI VIDEO STUDIO (REAL STORYBOARD + HONEST EXTERNAL SERVICE NOTICE FOR MP4)
                    item {
                        SetupRequiredBanner(
                            serviceName = "Cloud MP4 Video Rendering Engine (Text-to-Video / Image-to-Video)",
                            reason = "Native MP4 video rendering requires a connected cloud video-generation backend (such as Vertex AI Veo / Runway / Luma). Jarvis AI never fakes video rendering or fake MP4 exports. Use the real Gemini Storyboard, Voice-Over & SRT Subtitle Studio below right now!"
                        )
                    }
                    item {
                        GlassPanel(modifier = Modifier.fillMaxWidth()) {
                            Text("Storyboard, Timeline & SRT Subtitle Planner", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = videoConcept, onValueChange = { videoConcept = it }, label = { Text("Anime Scene / Video Concept") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = videoStyle, onValueChange = { videoStyle = it }, label = { Text("Visual Direction Style") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.generateVideoStoryboard(videoConcept, videoStyle) },
                                enabled = storyboardState !is GenerationState.Loading,
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("generate_storyboard_btn")
                            ) {
                                Text("Generate Scene Timeline & Subtitles")
                            }
                        }
                    }
                    item {
                        when (val sb = storyboardState) {
                            GenerationState.Idle -> {}
                            GenerationState.Loading -> LoadingCard("Planning shot-by-shot timeline, camera moves & SRT subtitles...")
                            is GenerationState.Error -> ErrorBannerCard(sb.message)
                            is GenerationState.Success -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            MediaExportHelper.shareTextDocument(
                                                context,
                                                "jarvis_subtitles.srt",
                                                viewModel.buildSrtFromStoryboard(sb.data)
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Export Real .SRT Subtitle File")
                                    }
                                    sb.data.forEach { sc ->
                                        GlassPanel(modifier = Modifier.fillMaxWidth(), accentColor = NeonViolet) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Scene ${sc.sceneNumber} • ${sc.cameraMovement}", style = MaterialTheme.typography.titleSmall)
                                                StatusPillBadge(label = "${sc.durationSec}s", color = ElectricCyan)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Visual: ${sc.visualPrompt}", style = MaterialTheme.typography.bodySmall)
                                            Text("Voice-Over: ${sc.voiceOver}", style = MaterialTheme.typography.bodySmall, color = EmeraldSignal)
                                            Text("Subtitle: ${sc.subtitle}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    // ANIME ART GALLERY
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Anime Art Gallery", style = MaterialTheme.typography.titleLarge)
                            FilterChip(
                                selected = favoritesOnly,
                                onClick = { favoritesOnly = !favoritesOnly },
                                label = { Text("Favorites") },
                                leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }

                    val list = (artworksState as? UiState.Success)?.data.orEmpty()
                        .let { items -> if (favoritesOnly) items.filter { it.favorite } else items }

                    if (list.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.Collections,
                                title = "No artworks saved yet",
                                subtitle = "Generate an anime pencil sketch, manga page, or image to build your gallery."
                            )
                        }
                    } else {
                        items(list, key = { it.id }) { art ->
                            val localBitmap = remember(art.localFilePath) {
                                val f = File(art.localFilePath)
                                if (art.localFilePath.isNotBlank() && f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                            }
                            GlassPanel(modifier = Modifier.fillMaxWidth(), accentColor = ElectricCyan) {
                                if (localBitmap != null) {
                                    Image(
                                        bitmap = localBitmap.asImageBitmap(),
                                        contentDescription = art.title,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp)
                                            .clip(RoundedCornerShape(14.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(art.title, style = MaterialTheme.typography.titleSmall)
                                        Text("${art.universe} • ${art.style} • ${art.pencilGrade}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row {
                                        IconButton(onClick = { viewModel.toggleArtworkFavorite(art) }) {
                                            Icon(
                                                if (art.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                                contentDescription = "Favorite artwork",
                                                tint = if (art.favorite) CrimsonError else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (localBitmap != null) {
                                            IconButton(onClick = {
                                                scope.launch {
                                                    val res = MediaExportHelper.exportBitmapToGallery(context, localBitmap, art.title)
                                                    viewModel.showToast(if (res.isSuccess) "Exported PNG to Photos" else "Export failed")
                                                }
                                            }) {
                                                Icon(Icons.Filled.Download, contentDescription = "Export PNG", tint = EmeraldSignal)
                                            }
                                        }
                                        IconButton(onClick = { viewModel.deleteArtwork(art.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete artwork", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
