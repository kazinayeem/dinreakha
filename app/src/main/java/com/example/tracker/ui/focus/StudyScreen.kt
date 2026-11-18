package com.example.tracker.ui.focus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tracker.data.focus.FocusType
import com.example.tracker.ui.i18n.Text
import com.example.tracker.ui.notes.CoursesScreen

enum class StudyTab(val label: String) { FOCUS("Focus timer"), NOTES("Class notes") }

/** The Study tab: the focus timer and class notes side by side — both are "time spent studying". */
@Composable
fun StudyScreen(
    initialTab: StudyTab,
    noteForSession: Long?,
    preset: Pair<FocusType, Int>?,
    onOpenCourse: (Long) -> Unit,
    onCaptureNote: (Long?) -> Unit,
) {
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    Column(Modifier.fillMaxSize()) {
        // Same pattern as the other tabs (Wallet, Me): a large page title first.
        Text(
            "Study",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            StudyTab.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = tab == t,
                    onClick = { tab = t },
                    shape = SegmentedButtonDefaults.itemShape(i, StudyTab.entries.size),
                ) { Text(t.label) }
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                StudyTab.FOCUS -> FocusScreen(noteForSession, preset)
                StudyTab.NOTES -> CoursesScreen(
                    onOpenCourse = onOpenCourse,
                    onCaptureNote = onCaptureNote,
                    onBack = {},
                    showTitle = false,
                )
            }
        }
    }
}
