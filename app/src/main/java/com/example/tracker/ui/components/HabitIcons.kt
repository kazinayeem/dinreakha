package com.example.tracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PhonelinkOff
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.tracker.data.schedule.Habits

/** One icon per checklist item, so the list can be read at a glance with short names. */
fun habitIcon(key: String): ImageVector = when {
    key == Habits.WOKE_ON_TIME -> Icons.Rounded.WbSunny
    key == Habits.MORNING_ROUTINE -> Icons.Rounded.SelfImprovement
    key == Habits.COURSE_STUDY -> Icons.AutoMirrored.Rounded.MenuBook
    key == Habits.CODE_PROJECT -> Icons.Rounded.Code
    key == Habits.RESEARCH -> Icons.Rounded.Science
    key == Habits.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
    key == Habits.NAP -> Icons.Rounded.Hotel
    key == Habits.SCREENS_OFF -> Icons.Rounded.PhonelinkOff
    key.startsWith("prayer_") -> Icons.Rounded.NightsStay
    key.startsWith("class_") -> Icons.Rounded.School
    else -> Icons.Rounded.TaskAlt
}
