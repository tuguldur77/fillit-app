package com.fillit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fillit.app.model.Event
import com.fillit.app.navigation.Route
import com.fillit.app.ui.components.FillItBottomBar
import com.google.firebase.Timestamp
import java.time.Duration
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleViewScreen(
    current: Route,
    onNavigate: (Route) -> Unit,
    onAddClick: () -> Unit,
    onOpenRecommendationsForSlot: (Long, Long) -> Unit,
    scheduleViewModel: ScheduleViewModel = viewModel(),
    onEditEvent: (Event) -> Unit = {},
    onDeleteEvent: (Event) -> Unit = { scheduleViewModel.deleteEvent(it.id) }
) {
    val uiState by scheduleViewModel.uiState.collectAsState()
    var deletingEvent by remember { mutableStateOf<Event?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("${uiState.selectedDate.year}년 ${uiState.selectedDate.monthValue}월", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                actions = {
                    Button(
                        onClick = onAddClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B4EFF)),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "추가", tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("추가", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            FillItBottomBar(current = current, onNavigate = onNavigate)
        }
    ) { inner ->
        Column(modifier = Modifier.padding(inner)) {
            WeekSelector(
                selectedDate = uiState.selectedDate,
                onDateSelected = { picked ->
                    // 자유 선택: 과거/오늘/미래 모두 허용
                    scheduleViewModel.onDateSelected(picked)
                }
            )
            Spacer(modifier = Modifier.padding(8.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(text = uiState.errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
            } else if (uiState.timeline.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("해당 날짜에는 일정이 없습니다.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(uiState.timeline) { item ->
                        when (item) {
                            is TimelineItem.EventItem -> EventCard(
                                event = item.event,
                                onClick = { onEditEvent(item.event) },
                                onLongClick = { deletingEvent = item.event }
                            )
                            is TimelineItem.EmptySlot -> EmptySlotCard(item.startTime, item.endTime) {
                                val startMillis = item.startTime.toDate().time
                                val endMillis = item.endTime.toDate().time
                                onOpenRecommendationsForSlot(startMillis, endMillis)
                            }
                        }
                    }
                }
            }
        }

        if (deletingEvent != null) {
            AlertDialog(
                onDismissRequest = { deletingEvent = null },
                title = { Text("일정 삭제") },
                text = { Text("이 일정을 삭제할까요?") },
                confirmButton = {
                    TextButton(onClick = {
                        deletingEvent?.let(onDeleteEvent)
                        deletingEvent = null
                    }) { Text("삭제") }
                },
                dismissButton = {
                    TextButton(onClick = { deletingEvent = null }) { Text("취소") }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventCard(
    event: Event,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.width(60.dp), horizontalAlignment = Alignment.Start) {
            Text(event.startTime.toFormattedString("HH:mm"), fontWeight = FontWeight.Bold, color = Color(0xFF6B4EFF))
            Text(event.endTime.toFormattedString("HH:mm"), fontSize = 12.sp, color = Color(0xFFBDBDBD))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF111827))
            if (event.location != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(event.location.name, fontSize = 12.sp, color = Color(0xFF6B7280))
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = event.startTime.getDuration(event.endTime),
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(vertical = 4.dp) // Removed background
        )
    }
}

@Composable
fun EmptySlotCard(startTime: Timestamp, endTime: Timestamp, onExploreClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFFD0D5DD)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.AccessTime,
                contentDescription = null,
                tint = Color(0xFF6B4EFF),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${startTime.toFormattedString("HH:mm")} ~ ${endTime.toFormattedString("HH:mm")} (${startTime.getDuration(endTime)})",
                fontSize = 12.sp, color = Color.Gray
            )
            Text("빈 시간이 있어요!", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onExploreClick,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B4EFF))
            ) {
                Text("이 시간에 갈만한 장소 찾기", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun WeekSelector(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val weekFields = remember { WeekFields.of(Locale.KOREA) }
    val firstDayOfWeek = remember(selectedDate) { selectedDate.with(weekFields.dayOfWeek(), 1) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onDateSelected(selectedDate.minusWeeks(1)) }) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "이전 주")
        }

        (0..6).forEach { i ->
            val date = firstDayOfWeek.plusDays(i.toLong())
            DateItem(date = date, isSelected = (date == selectedDate)) {
                onDateSelected(date)
            }
        }

        IconButton(onClick = { onDateSelected(selectedDate.plusWeeks(1)) }) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "다음 주")
        }
    }
}

@Composable
fun DateItem(date: LocalDate, isSelected: Boolean, onClick: () -> Unit) {
    val dayName = remember { date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN) }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(dayName, fontSize = 12.sp, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Gray)
        Text("${date.dayOfMonth}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Black)
    }
}

private fun Timestamp?.toFormattedString(pattern: String): String {
    if (this == null) return ""
    val date = this.toDate()
    val format = java.text.SimpleDateFormat(pattern, Locale.getDefault())
    return format.format(date)
}

private fun Timestamp?.getDuration(end: Timestamp?): String {
    if (this == null || end == null) return ""
    val duration = Duration.between(this.toDate().toInstant(), end.toDate().toInstant())
    val hours = duration.toHours()
    if (hours > 0) {
        return "${hours}시간"
    }
    val minutes = duration.toMinutes()
    if (minutes > 0) {
        return "${minutes}분"
    }
    return ""
}
