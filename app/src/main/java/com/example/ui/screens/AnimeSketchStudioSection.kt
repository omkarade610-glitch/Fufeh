package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.services.MediaExportHelper
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.GlassPanel
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.NeonViolet
import com.example.viewmodel.GenerationState
import com.example.viewmodel.ImageResultState
import com.example.viewmodel.JarvisViewModel
import kotlinx.coroutines.launch

private val ANIME_UNIVERSES = listOf(
    "Tokyo Revengers",
    "Jujutsu Kaisen",
    "Naruto",
    "One Piece",
    "Demon Slayer",
    "Dragon Ball",
    "Bleach",
    "Attack on Titan",
    "Original Japanese Anime"
)

private val TOKYO_REVENGERS_PRESETS = listOf(
    "Mikey (Manjiro Sano)-inspired",
    "Draken-inspired",
    "Takemichi-inspired",
    "Chifuyu-inspired",
    "Original Delinquent Captain"
)

private val DRAWING_STYLES = listOf(
    "Graphite Pencil Sketch",
    "Black-and-White Sketch",
    "Manga Portrait",
    "Fine Ink & Screentone",
    "Dark Cross-Hatching Study"
)

private val VIEW_ANGLES = listOf(
    "Front Face",
    "Side Face",
    "Face Portrait",
    "Full-Body Drawing",
    "Dynamic Action Pose"
)

private val PENCIL_GRADES = listOf("HB", "2B", "4B", "6B", "8B")

private val EXPRESSIONS = listOf("Stoic & Intense", "Confident Smirk", "Fierce Battle", "Calm Smile", "Dramatic Shadow")

@Composable
fun AnimeSketchStudioSection(viewModel: JarvisViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sketchState by viewModel.animeSketchState.collectAsStateWithLifecycle()
    val photoState by viewModel.photoToAnimeState.collectAsStateWithLifecycle()
    val tutorState by viewModel.drawingTutorState.collectAsStateWithLifecycle()
    val characterState by viewModel.characterSheetState.collectAsStateWithLifecycle()

    var subMode by remember { mutableIntStateOf(0) } // 0: Pencil Studio, 1: Photo to Sketch, 2: 8-Step Tutor, 3: Character Creator

    // Pencil studio state
    var universe by remember { mutableStateOf(ANIME_UNIVERSES.first()) }
    var characterPreset by remember { mutableStateOf(TOKYO_REVENGERS_PRESETS.first()) }
    var drawingStyle by remember { mutableStateOf(DRAWING_STYLES.first()) }
    var viewAngle by remember { mutableStateOf(VIEW_ANGLES[2]) }
    var expression by remember { mutableStateOf(EXPRESSIONS.first()) }
    var pencilGrade by remember { mutableStateOf("4B") }
    var crossHatching by remember { mutableStateOf(true) }
    var paperTexture by remember { mutableStateOf(true) }
    var customDetails by remember { mutableStateOf("Flowing wind-swept hair, sharp anime eyes, dramatic shadow under chin") }

    // Photo to anime state
    var pickedPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var photoTargetMode by remember { mutableStateOf("Graphite Pencil Sketch") }
    var sketchIntensity by remember { mutableFloatStateOf(0.65f) }
    var photoBgStyle by remember { mutableStateOf("Clean Textured Sketchbook Paper") }
    var showOriginalCompare by remember { mutableStateOf(false) }

    // Character creator state
    var archetype by remember { mutableStateOf("Original Gang Leader") }
    var charHair by remember { mutableStateOf("Long swept blond undercut") }
    var charOutfit by remember { mutableStateOf("Embroidered dark biker coat") }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                pickedPhoto = MediaExportHelper.loadBitmapFromUri(context, uri)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Studio Hero Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_anime_sketch_studio_1795024203645),
                contentDescription = "Japanese anime sketch artist desk",
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.45f),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                StatusPillBadge(label = "NANO BANANA 2.1 • FAN-ART & ORIGINAL STUDIO", color = ElectricCyan)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Japanese Anime Sketch Artist", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Tokyo Revengers studio, HB–8B pencil engine, Photo-to-Sketch & 8-Step Tutor. Generated art is original/fan-inspired, never official.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 4 Sub-Modes
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val modes = listOf(
                "Pencil Studio" to Icons.Filled.Brush,
                "Photo to Sketch" to Icons.Filled.PhotoLibrary,
                "8-Step Tutor" to Icons.Filled.School,
                "Character Creator" to Icons.Filled.PersonAdd
            )
            items(modes.indices.toList()) { idx ->
                FilterChip(
                    selected = subMode == idx,
                    onClick = { subMode = idx },
                    label = { Text(modes[idx].first) },
                    leadingIcon = { Icon(modes[idx].second, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("anime_submode_$idx")
                )
            }
        }

        when (subMode) {
            0 -> {
                // ANIME SKETCH & TOKYO REVENGERS PENCIL ENGINE
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Text("1. Anime Universe Category", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ANIME_UNIVERSES) { u ->
                            FilterChip(
                                selected = universe == u,
                                onClick = {
                                    universe = u
                                    if (u != "Tokyo Revengers") {
                                        characterPreset = "Original $u-inspired Warrior"
                                    } else {
                                        characterPreset = TOKYO_REVENGERS_PRESETS.first()
                                    }
                                },
                                label = { Text(u) }
                            )
                        }
                    }

                    if (universe == "Tokyo Revengers") {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Tokyo Revengers Sketch Presets (Fan-Inspired)", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(TOKYO_REVENGERS_PRESETS) { preset ->
                                FilterChip(
                                    selected = characterPreset == preset,
                                    onClick = { characterPreset = preset },
                                    label = { Text(preset) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = characterPreset,
                        onValueChange = { characterPreset = it },
                        label = { Text("Character Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("2. Professional Pencil Engine (Graphite Grade)", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PENCIL_GRADES) { grade ->
                            FilterChip(
                                selected = pencilGrade == grade,
                                onClick = { pencilGrade = grade },
                                label = { Text("$grade Pencil") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Framing & Pose", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VIEW_ANGLES) { angle ->
                            FilterChip(selected = viewAngle == angle, onClick = { viewAngle = angle }, label = { Text(angle) })
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Drawing Style & Expression", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DRAWING_STYLES) { st ->
                            FilterChip(selected = drawingStyle == st, onClick = { drawingStyle = st }, label = { Text(st) })
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(EXPRESSIONS) { ex ->
                            FilterChip(selected = expression == ex, onClick = { expression = ex }, label = { Text(ex) })
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dark Cross-Hatching & Blending", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = crossHatching, onCheckedChange = { crossHatching = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Realistic Sketchbook Paper Texture", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = paperTexture, onCheckedChange = { paperTexture = it })
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customDetails,
                        onValueChange = { customDetails = it },
                        label = { Text("Hair, Eye & Shading Details") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            viewModel.generateAnimeSketch(
                                context = context,
                                universe = universe,
                                characterPreset = characterPreset,
                                drawingStyle = drawingStyle,
                                viewAngle = viewAngle,
                                expression = expression,
                                pencilGrade = pencilGrade,
                                crossHatching = crossHatching,
                                paperTexture = paperTexture,
                                customDetails = customDetails
                            )
                        },
                        enabled = sketchState !is ImageResultState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("generate_anime_sketch_btn")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate $pencilGrade Anime Sketch", fontWeight = FontWeight.Bold)
                    }
                }

                RenderImageResultBlock(
                    state = sketchState,
                    onExportPng = { bmp, title ->
                        scope.launch {
                            val res = MediaExportHelper.exportBitmapToGallery(context, bmp, title)
                            viewModel.showToast(
                                if (res.isSuccess) "Exported PNG to Pictures/JarvisAI" else "Export failed: ${res.exceptionOrNull()?.message}"
                            )
                        }
                    }
                )
            }

            1 -> {
                // PHOTO TO ANIME SKETCH CONVERTER
                GlassPanel(modifier = Modifier.fillMaxWidth(), accentColor = NeonViolet) {
                    Text("Photo to Anime / Pencil Sketch", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pick a photo using Android's zero-permission Photo Picker, adjust sketch intensity, and compare Before vs After.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pick_photo_for_sketch_btn")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (pickedPhoto == null) "Select Reference Photo" else "Change Reference Photo")
                    }

                    if (pickedPhoto != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Image(
                            bitmap = pickedPhoto!!.asImageBitmap(),
                            contentDescription = "Selected reference photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Graphite Pencil Sketch", "Japanese Anime Art", "Black-and-White Manga Ink").forEach { mode ->
                            item {
                                FilterChip(
                                    selected = photoTargetMode == mode,
                                    onClick = { photoTargetMode = mode },
                                    label = { Text(mode) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Sketch Intensity: ${(sketchIntensity * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                    Slider(value = sketchIntensity, onValueChange = { sketchIntensity = it })

                    OutlinedTextField(
                        value = photoBgStyle,
                        onValueChange = { photoBgStyle = it },
                        label = { Text("Background Style") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            pickedPhoto?.let { bmp ->
                                viewModel.convertPhotoToAnimeSketch(context, bmp, photoTargetMode, sketchIntensity, photoBgStyle)
                            }
                        },
                        enabled = pickedPhoto != null && photoState !is ImageResultState.Loading,
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("convert_photo_btn")
                    ) {
                        Text("Convert Photo with Nano Banana 2.1", fontWeight = FontWeight.Bold)
                    }
                }

                when (val s = photoState) {
                    is ImageResultState.Success -> {
                        GlassPanel(modifier = Modifier.fillMaxWidth(), accentColor = EmeraldSignal) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (showOriginalCompare) "Original Photo" else s.title, style = MaterialTheme.typography.titleSmall)
                                OutlinedButton(onClick = { showOriginalCompare = !showOriginalCompare }) {
                                    Icon(Icons.Filled.Compare, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (showOriginalCompare) "View Sketch" else "Compare Original")
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            val shownBitmap = if (showOriginalCompare && s.referenceBitmap != null) s.referenceBitmap else s.bitmap
                            Image(
                                bitmap = shownBitmap.asImageBitmap(),
                                contentDescription = s.title,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        val res = MediaExportHelper.exportBitmapToGallery(context, s.bitmap, s.title)
                                        viewModel.showToast(if (res.isSuccess) "Saved PNG to Photos" else "Export failed")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export PNG to Gallery")
                            }
                        }
                    }
                    else -> RenderImageResultBlock(photoState) { _, _ -> }
                }
            }

            2 -> {
                // 8-STEP ANIME DRAWING TUTOR
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Text("8-Step Anime Drawing Tutor", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Step-by-step mangaka curriculum: Face outline -> Eyes -> Nose & Mouth -> Hairstyle -> Body -> Clothing -> Shading -> Final details.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = characterPreset,
                        onValueChange = { characterPreset = it },
                        label = { Text("What do you want to learn to draw?") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.generateDrawingTutor(characterPreset, universe, pencilGrade) },
                        enabled = tutorState !is GenerationState.Loading,
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("generate_tutor_btn")
                    ) {
                        Text("Generate 8-Step Visual Drawing Lesson", fontWeight = FontWeight.Bold)
                    }
                }

                when (val ts = tutorState) {
                    is GenerationState.Loading -> LoadingCard("Building your 8-step mangaka drawing lesson...")
                    is GenerationState.Error -> ErrorBannerCard(ts.message)
                    is GenerationState.Success -> {
                        ts.data.forEach { step ->
                            GlassPanel(modifier = Modifier.fillMaxWidth(), accentColor = ElectricCyan) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Step ${step.stepNumber}: ${step.title}", style = MaterialTheme.typography.titleSmall)
                                    StatusPillBadge(label = step.pencilGrade, color = ElectricCyan)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(step.instruction, style = MaterialTheme.typography.bodyMedium)
                                if (step.proTip.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Mangaka Tip: ${step.proTip}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EmeraldSignal
                                    )
                                }
                            }
                        }
                    }
                    GenerationState.Idle -> {}
                }
            }

            else -> {
                // ANIME CHARACTER CREATOR
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Text("Original Anime Character Creator", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Anime Hero", "Villain", "School Rival", "Fantasy Mage", "Ninja", "Pirate Captain", "Original Gang Leader").forEach { a ->
                            item { FilterChip(selected = archetype == a, onClick = { archetype = a }, label = { Text(a) }) }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = charHair, onValueChange = { charHair = it }, label = { Text("Custom Hairstyle") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = charOutfit, onValueChange = { charOutfit = it }, label = { Text("Custom Outfit") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.generateCharacterProfile(archetype, universe, charHair, charOutfit, expression) },
                        enabled = characterState !is GenerationState.Loading,
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("create_character_sheet_btn")
                    ) {
                        Text("Create Character Profile & Consistency DNA")
                    }
                }
                when (val cs = characterState) {
                    is GenerationState.Loading -> LoadingCard("Forging anime character lore & visual consistency DNA...")
                    is GenerationState.Error -> ErrorBannerCard(cs.message)
                    is GenerationState.Success -> {
                        GlassPanel(modifier = Modifier.fillMaxWidth(), accentColor = NeonViolet) {
                            Text(cs.data, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.addKnowledgeItem(
                                        title = "Character DNA: $archetype",
                                        content = cs.data,
                                        folder = "Anime Characters",
                                        itemType = "memory",
                                        pinned = true
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Save Character Consistency DNA to AI Memory")
                            }
                        }
                    }
                    GenerationState.Idle -> {}
                }
            }
        }
    }
}

@Composable
fun RenderImageResultBlock(
    state: ImageResultState,
    onExportPng: (Bitmap, String) -> Unit
) {
    when (state) {
        ImageResultState.Idle -> {}
        is ImageResultState.Loading -> LoadingCard(state.statusLabel)
        is ImageResultState.Error -> ErrorBannerCard(state.message)
        is ImageResultState.Success -> {
            GlassPanel(modifier = Modifier.fillMaxWidth(), accentColor = EmeraldSignal) {
                Text(state.title, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = state.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.FillWidth
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onExportPng(state.bitmap, state.title) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export PNG to Photos Gallery")
                }
            }
        }
    }
}

@Composable
fun LoadingCard(message: String) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), accentColor = ElectricCyan) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp, color = ElectricCyan)
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
