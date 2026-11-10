package com.strengthlogz.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strengthlogz.mobile.model.Exercise
import com.strengthlogz.mobile.model.WorkoutSet
import com.strengthlogz.mobile.ui.theme.*
import com.strengthlogz.mobile.util.SetUtils.formatWeightValue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    exercises: List<Exercise>,
    allSets: List<WorkoutSet>,
    onCreateExercise: (Exercise) -> Unit,
    onUpdateExercise: (oldName: String, updated: Exercise) -> Result<Unit>,
    onBackupExercises: suspend (oldName: String?, newName: String?) -> Result<String>,
    onBack: (() -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var exerciseToEdit by remember { mutableStateOf<Exercise?>(null) }
    var syncToast by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val categories = listOf("All", "Chest", "Back", "Legs", "Shoulders", "Arms", "Core", "Other")

    // Filtered exercises list
    val filteredExercises = remember(exercises, searchQuery, selectedCategoryFilter) {
        val q = searchQuery.trim()
        exercises.filter { ex ->
            val cat = ex.category.ifBlank { "Other" }
            val name = ex.name
            val desc = ex.safeDescription
            val matchesCategory = selectedCategoryFilter == "All" ||
                cat.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesSearch = q.isEmpty() ||
                name.contains(q, ignoreCase = true) ||
                cat.contains(q, ignoreCase = true) ||
                desc.contains(q, ignoreCase = true)
            matchesCategory && matchesSearch
        }.sortedBy { it.name.lowercase() }
    }

    // Historical set mapping and pre-computed stats per exercise
    val setsByExercise = remember(allSets) {
        allSets.groupBy { it.exercise.lowercase() }
    }
    val categoryCounts = remember(exercises) {
        val map = HashMap<String, Int>()
        map["All"] = exercises.size
        for (ex in exercises) {
            val cat = ex.category.ifBlank { "Other" }
            val matchedKey = categories.find { it.equals(cat, ignoreCase = true) } ?: "Other"
            map[matchedKey] = (map[matchedKey] ?: 0) + 1
        }
        map
    }

    Scaffold(
        containerColor = BgDark,
        snackbarHost = {
            if (syncToast.isNotBlank()) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = SurfaceDark,
                    contentColor = TextPrimary,
                    action = {
                        TextButton(onClick = { syncToast = "" }) {
                            Text("Dismiss", color = AccentOrange)
                        }
                    }
                ) {
                    Text(syncToast, fontSize = 12.5.sp)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            // HEADER ROW
            ModernScreenHeader(
                title = "Exercises",
                subtitle = "${exercises.size} catalogued movements • Google Sheets Synced",
                badgeText = "${exercises.size} LIFTS",
                action = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Manual Cloud Backup Button
                        IconButton(
                            onClick = {
                                if (!isSyncing) {
                                    scope.launch {
                                        isSyncing = true
                                        syncToast = "Backing up exercises to Google Sheets..."
                                        val res = onBackupExercises(null, null)
                                        syncToast = res.getOrElse { "Backup error: ${it.message}" }
                                        isSyncing = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .background(SurfaceDark, CircleShape)
                                .size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Backup to Google Sheets",
                                tint = if (isSyncing) AccentOrange else AccentPeach,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Create New Exercise Button
                        Button(
                            onClick = { showCreateDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentOrange,
                                contentColor = Color(0xFF140D07)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("+ New", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            // SEARCH BAR
            ModernSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search exercises, category, form cues..."
            )

            Spacer(Modifier.height(10.dp))

            // CATEGORY FILTER CHIPS
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategoryFilter == cat
                    val count = categoryCounts[cat] ?: 0
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) AccentOrange else SurfaceCard,
                        border = BorderStroke(1.dp, if (isSelected) AccentOrange else BorderDark),
                        modifier = Modifier.clickable { selectedCategoryFilter = cat }
                    ) {
                        Text(
                            text = if (count > 0) "$cat ($count)" else cat,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF140D07) else TextSecondary,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // EXERCISE CARDS LIST
            if (filteredExercises.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No exercises match \"$searchQuery\"" else "No exercises found",
                            color = TextSecondary,
                            fontSize = 13.5.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { showCreateDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color(0xFF140D07)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("+ Create \"$searchQuery\"", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(filteredExercises, key = { index, ex -> "${ex.name}_$index" }) { _, exercise ->
                        val exSets = setsByExercise[exercise.name.lowercase()] ?: emptyList()
                        val validSets = exSets.filter { (it.reps ?: 0) > 0 }
                        val peakWeight = validSets.mapNotNull { it.weight }.maxOrNull() ?: 0.0

                        StrengthCard(
                            shape = RoundedCornerShape(14.dp),
                            onClick = { exerciseToEdit = exercise },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = exercise.name,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        CategoryPill(category = exercise.category.ifBlank { "Other" })

                                        if (exercise.bodyweight) {
                                            Surface(
                                                color = GoldPR.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(0.5.dp, GoldPR.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = "BW",
                                                    color = GoldPR,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    val workoutCount = validSets.map { it.workoutId }.distinct().size
                                    val statsLine = buildString {
                                        if (workoutCount > 0) {
                                            append("$workoutCount sessions")
                                            if (peakWeight > 0.0) append(" • Top ${formatWeightValue(peakWeight)}kg")
                                        } else {
                                            append("No sessions logged yet")
                                        }
                                        if (exercise.safeDescription.isNotBlank()) {
                                            append(" • ${exercise.safeDescription}")
                                        }
                                    }
                                    Text(
                                        text = statsLine,
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Exercise",
                                    tint = TextSecondary,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(17.dp)
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }

    // CREATE EXERCISE DIALOG
    if (showCreateDialog) {
        ExerciseFormDialog(
            title = "Create Exercise",
            initialName = if (searchQuery.isNotBlank() && exercises.none { it.name.equals(searchQuery, ignoreCase = true) }) searchQuery.trim() else "",
            initialCategory = if (selectedCategoryFilter != "All") selectedCategoryFilter else "Chest",
            initialBodyweight = false,
            initialDescription = "",
            confirmButtonLabel = "Create & Sync",
            onDismiss = { showCreateDialog = false },
            onSave = { name, category, bodyweight, description ->
                val newEx = Exercise(
                    name = name.trim(),
                    category = category.trim().ifBlank { "Other" },
                    bodyweight = bodyweight,
                    description = description.trim()
                )
                onCreateExercise(newEx)
                showCreateDialog = false

                // Trigger Google Sheets backup
                scope.launch {
                    syncToast = "Backing up \"${newEx.name}\" to Google Sheets..."
                    val res = onBackupExercises(null, null)
                    syncToast = res.getOrElse { "Created locally (cloud backup failed: ${it.message})" }
                }
            }
        )
    }

    // EDIT EXERCISE DIALOG
    exerciseToEdit?.let { ex ->
        ExerciseFormDialog(
            title = "Edit Exercise",
            initialName = ex.name,
            initialCategory = ex.category,
            initialBodyweight = ex.bodyweight,
            initialDescription = ex.safeDescription,
            confirmButtonLabel = "Save Changes",
            nameEditNotice = "Renaming will automatically update all past workout records for this exercise.",
            onDismiss = { exerciseToEdit = null },
            onSave = { newName, category, bodyweight, description ->
                val updatedEx = Exercise(
                    name = newName.trim(),
                    category = category.trim().ifBlank { "Other" },
                    bodyweight = bodyweight,
                    description = description.trim()
                )
                val oldName = ex.name
                val result = onUpdateExercise(oldName, updatedEx)
                if (result.isSuccess) {
                    exerciseToEdit = null
                    scope.launch {
                        syncToast = "Syncing changes to Google Sheets..."
                        val renameOld = if (!oldName.equals(updatedEx.name, ignoreCase = true)) oldName else null
                        val renameNew = if (!oldName.equals(updatedEx.name, ignoreCase = true)) updatedEx.name else null
                        val res = onBackupExercises(renameOld, renameNew)
                        syncToast = res.getOrElse { "Saved locally (cloud sync error: ${it.message})" }
                    }
                } else {
                    syncToast = result.exceptionOrNull()?.message ?: "Update failed"
                }
            }
        )
    }
}

@Composable
fun ExerciseFormDialog(
    title: String,
    initialName: String,
    initialCategory: String,
    initialBodyweight: Boolean,
    initialDescription: String,
    confirmButtonLabel: String,
    nameEditNotice: String? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, bodyweight: Boolean, description: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var category by remember { mutableStateOf(initialCategory) }
    var bodyweight by remember { mutableStateOf(initialBodyweight) }
    var description by remember { mutableStateOf(initialDescription) }
    var errorMessage by remember { mutableStateOf("") }

    val categories = listOf("Chest", "Back", "Legs", "Shoulders", "Arms", "Core", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (nameEditNotice != null) {
                    Surface(
                        color = AccentOrange.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = nameEditNotice,
                            fontSize = 11.5.sp,
                            color = AccentOrange,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = ""
                    },
                    label = { Text("Exercise Name") },
                    placeholder = { Text("e.g. Incline Cable Fly") },
                    singleLine = true,
                    isError = errorMessage.isNotBlank(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = BorderDark,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = DecreaseRed, fontSize = 12.sp)
                }

                // Category Selection Chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Muscle Category", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories) { cat ->
                            val isSel = category.equals(cat, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) AccentOrange else SurfaceDark,
                                border = BorderStroke(1.dp, if (isSel) AccentOrange else BorderDark),
                                modifier = Modifier.clickable { category = cat }
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color(0xFF140D07) else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Bodyweight Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { bodyweight = !bodyweight }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Bodyweight Movement", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Tracks reps with BW baseline", fontSize = 11.5.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = bodyweight,
                        onCheckedChange = { bodyweight = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF140D07),
                            checkedTrackColor = AccentOrange
                        )
                    )
                }

                // Description Input (Multiline textarea)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description & Form Cues") },
                    placeholder = { Text("e.g. Set bench to 30°, pause at bottom stretch, full squeeze...") },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = BorderDark,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = name.trim()
                    if (clean.isBlank()) {
                        errorMessage = "Exercise name cannot be empty"
                    } else {
                        onSave(clean, category, bodyweight, description)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentOrange,
                    contentColor = Color(0xFF140D07)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(confirmButtonLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
