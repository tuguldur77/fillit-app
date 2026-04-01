package com.fillit.app.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fillit.app.data.EventRepository
import com.fillit.app.model.Event
import com.fillit.app.model.EventLocation
import com.fillit.app.model.Recurrence
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

private val KOREA_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")
private val HH_MM_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA)

private enum class TimeField { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScheduleScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
    selectedDate: LocalDate = LocalDate.now(),
    initialEvent: com.fillit.app.model.Event? = null,
    onDelete: ((String) -> Unit)? = null,
    onUpdate: ((String, Map<String, Any>) -> Unit)? = null,
    onCreate: ((title: String, startTime: Timestamp, endTime: Timestamp, location: EventLocation?, memo: String, recurrence: Recurrence) -> Unit)? = null
) {
    var title by remember { mutableStateOf(initialEvent?.title.orEmpty()) }
    var start by remember { mutableStateOf(initialEvent?.startTime.toFormattedTime()) }
    var end by remember { mutableStateOf(initialEvent?.endTime.toFormattedTime()) }
    var memo by remember { mutableStateOf(initialEvent?.memo.orEmpty()) }
    var recurrence by remember { mutableStateOf(initialEvent?.recurrence ?: Recurrence.NONE) }
    var isLoading by remember { mutableStateOf(false) }
    var showPickerFor by remember { mutableStateOf<TimeField?>(null) }
    var selectedLocation by remember { mutableStateOf(initialEvent?.location) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    if (showPickerFor != null) {
        val timePickerState = rememberTimePickerState(is24Hour = true)
        TimePickerDialog(
            onDismissRequest = { showPickerFor = null },
            onConfirm = {
                val time = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                when (showPickerFor) {
                    TimeField.START -> start = time
                    TimeField.END -> end = time
                    null -> {}
                }
                showPickerFor = null
            },
            content = { TimePicker(state = timePickerState) }
        )
    }

    val placesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val place = result.data?.let { Autocomplete.getPlaceFromIntent(it) }
                val latLng: LatLng? = place?.latLng
                val id = place?.id
                val name = place?.name
                if (latLng == null || id.isNullOrBlank() || name.isNullOrBlank()) {
                    Toast.makeText(context, "장소 정보를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
                    return@rememberLauncherForActivityResult
                }
                selectedLocation = EventLocation(
                    name = name,
                    lat = latLng.latitude,
                    lng = latLng.longitude,
                    placeId = id
                )
            }
            Activity.RESULT_CANCELED -> Unit
            else -> {
                val status = result.data?.let { Autocomplete.getStatusFromIntent(it) }
                Toast.makeText(context, "장소 선택 오류: ${status?.statusMessage ?: "Unknown"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialEvent == null) "일정 추가" else "일정 수정") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기") } }
            )
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .padding(16.dp)
        ) {
            Text("날짜: ${selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}")
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("제목") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))

            TimeSelector(label = "시작 시간", time = start) { showPickerFor = TimeField.START }
            Spacer(Modifier.height(8.dp))
            TimeSelector(label = "종료 시간", time = end) { showPickerFor = TimeField.END }
            Spacer(Modifier.height(16.dp))

            RecurrenceSelector(selected = recurrence, onSelected = { recurrence = it })
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
                    val intent = Autocomplete.IntentBuilder(
                        AutocompleteActivityMode.FULLSCREEN,
                        fields
                    ).build(context)
                    placesLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("장소 선택")
            }
            selectedLocation?.let {
                Spacer(Modifier.height(8.dp))
                Text("선택된 장소: ${it.name}")
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(value = memo, onValueChange = { memo = it }, label = { Text("메모") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))

            Button(
                enabled = !isLoading,
                onClick = {
                    if (title.isBlank() || start.isBlank() || end.isBlank()) {
                        Toast.makeText(context, "제목, 시작 시간, 종료 시간을 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val startTs = start.toTimestamp(selectedDate)
                    val endTs = end.toTimestamp(selectedDate)
                    if (startTs == null || endTs == null) {
                        Toast.makeText(context, "시간 형식이 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (endTs.seconds <= startTs.seconds) {
                        Toast.makeText(context, "종료 시간은 시작 시간보다 늦어야 합니다.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLoading = true
                    scope.launch {
                        runCatching {
                            if (initialEvent != null && onUpdate != null) {
                                val updates = mutableMapOf<String, Any>(
                                    "title" to title,
                                    "startTime" to startTs,
                                    "endTime" to endTs,
                                    "memo" to memo,
                                    "recurrence" to recurrence.name
                                )

                                selectedLocation?.let {
                                    updates["location"] = mapOf(
                                        "name" to it.name,
                                        "lat" to it.lat,
                                        "lng" to it.lng,
                                        "placeId" to it.placeId
                                    )
                                }

                                onUpdate(initialEvent.id, updates)
                            } else if (onCreate != null) {
                                onCreate(title, startTs, endTs, selectedLocation, memo, recurrence)
                            } else {
                                val eventToSave = Event(
                                    title = title,
                                    location = selectedLocation,
                                    memo = memo.ifBlank { null },
                                    startTime = startTs,
                                    endTime = endTs,
                                    recurrence = recurrence
                                )
                                EventRepository.addEvent(eventToSave).getOrThrow()
                            }
                        }.onSuccess {
                            Toast.makeText(context, "저장되었습니다.", Toast.LENGTH_SHORT).show()
                            onSave()
                        }.onFailure {
                            Toast.makeText(context, "저장 실패: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("저장")
                }
            }

            if (initialEvent != null && onDelete != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        onDelete(initialEvent.id)
                        onSave()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("삭제") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurrenceSelector(selected: Recurrence, onSelected: (Recurrence) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = isExpanded, onExpandedChange = { isExpanded = it }) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            label = { Text("반복") },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            // "매일", "매월"은 비활성화 처리
            Recurrence.values().forEach { recurrence ->
                DropdownMenuItem(
                    text = { Text(recurrence.displayName) },
                    onClick = {
                        onSelected(recurrence)
                        isExpanded = false
                    },
                    enabled = recurrence == Recurrence.NONE || recurrence == Recurrence.WEEKLY
                )
            }
        }
    }
}

@Composable
private fun TimeSelector(label: String, time: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (time.isNotBlank()) "$label: $time" else label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (time.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = Icons.Default.AccessTime,
            contentDescription = "시간 선택",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)) {
                    content()
                }
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) {
                        Text("취소")
                    }
                    TextButton(onClick = onConfirm) {
                        Text("확인")
                    }
                }
            }
        }
    }
}

private fun String.toTimestamp(date: LocalDate): Timestamp? {
    if (this.isBlank()) return null
    return try {
        val localTime = LocalTime.parse(this, HH_MM_FORMATTER)
        val instant = date.atTime(localTime).atZone(KOREA_ZONE_ID).toInstant()
        Timestamp(instant.epochSecond, instant.nano)
    } catch (_: Exception) {
        null
    }
}

private fun Timestamp?.toFormattedTime(): String {
    if (this == null) return ""
    val local = this.toDate().toInstant().atZone(KOREA_ZONE_ID).toLocalTime()
    return "%02d:%02d".format(local.hour, local.minute)
}

@Preview(showBackground = true)
@Composable
private fun PreviewAddScheduleScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        AddScheduleScreen(onBack = {}, onSave = {})
    }
}
