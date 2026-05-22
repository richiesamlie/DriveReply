package com.example.drivereply

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data object Templates : NavKey
@Serializable data object ReplyLog : NavKey
@Serializable data object Settings : NavKey
@Serializable data class EditTemplate(val templateId: String? = null) : NavKey
