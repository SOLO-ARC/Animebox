package com.lagradost.cloudstream3.ui.animebox.settings

import android.app.Activity
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.lagradost.cloudstream3.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lagradost.cloudstream3.ui.animebox.CustomSearchIcon
import com.lagradost.cloudstream3.ui.animebox.download.AnimeDownloadManager
import com.lagradost.cloudstream3.ui.animebox.download.StorageStats
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ----------------------------------------------------------------------------
// Official Google Material 24x24 Vector Path Data
// ----------------------------------------------------------------------------

enum class SettingsIconType(val pathData: String) {
    LAUNCH_MODE("M12 21C11.5858 21 11.25 20.6642 11.25 20.25V18.75H8.75C8.33579 18.75 8 18.4142 8 18C8 17.5858 8.33579 17.25 8.75 17.25H15.25C15.6642 17.25 16 17.5858 16 18C16 18.4142 15.6642 18.75 15.25 18.75H12.75V20.25C12.75 20.6642 12.4142 21 12 21ZM2 7C2 5.11438 2 4.17157 2.58579 3.58579C3.17157 3 4.11438 3 6 3H18C19.8856 3 20.8284 3 21.4142 3.58579C22 4.17157 22 5.11438 22 7V13C22 14.8856 22 15.8284 21.4142 16.4142C20.8284 17 19.8856 17 18 17H6C4.11438 17 3.17157 17 2.58579 16.4142C2 15.8284 2 14.8856 2 13V7Z"),
    CHEVRON_LEFT("M14.71 6.71a.996.996 0 0 0-1.41 0L8.71 11.3a.996.996 0 0 0 0 1.41l4.59 4.59c.39.39 1.02.39 1.41 0 .39-.39.39-1.02 0-1.41L10.83 12l3.88-3.88c.39-.39.38-1.03 0-1.41z"),
    CHEVRON_RIGHT("M9.29 6.71c-.39.39-.39 1.02 0 1.41L13.17 12l-3.88 3.88c-.39.39-.39 1.02 0 1.41.39.39 1.02.39 1.41 0l4.59-4.59c.39-.39.39-1.02 0-1.41L10.7 6.7c-.38-.38-1.02-.38-1.41.01z"),
    WIFI("M2 16C2 14.1144 2 13.1716 2.58579 12.5858C3.17157 12 4.11438 12 6 12H18C19.8856 12 20.8284 12 21.4142 12.5858C22 13.1716 22 14.1144 22 16C22 17.8856 22 18.8284 21.4142 19.4142C20.8284 20 19.8856 20 18 20H6C4.11438 20 3.17157 20 2.58579 19.4142C2 18.8284 2 17.8856 2 16ZM16.5 12V9M21.5833 7.39702C20.7574 5.40286 18.7924 4 16.4996 4C14.2069 4 12.2419 5.40286 11.416 7.39702M19.3806 8.65811C19.0167 7.41107 17.865 6.5 16.5004 6.5C15.1358 6.5 13.984 7.41107 13.6201 8.65811"),
    SMART_DOWNLOADS("M12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2ZM12.5303 16.5303C12.2374 16.8232 11.7626 16.8232 11.4697 16.5303L8.46967 13.5303C8.17678 13.2374 8.17678 12.7626 8.46967 12.4697C8.76256 12.1768 9.23744 12.1768 9.53033 12.4697L11.25 14.1893V8C11.25 7.58579 11.5858 7.25 12 7.25C12.4142 7.25 12.75 7.58579 12.75 8V14.1893L14.4697 12.4697C14.7626 12.1768 15.2374 12.1768 15.5303 12.4697C15.8232 12.7626 15.8232 13.2374 15.5303 13.5303L12.5303 16.5303Z"),
    DOWNLOAD_QUALITY("M2 12C2 7.28595 2 4.92893 3.46447 3.46447C4.92893 2 7.28595 2 12 2C16.714 2 19.0711 2 20.5355 3.46447C22 4.92893 22 7.28595 22 12C22 16.714 22 19.0711 20.5355 20.5355C19.0711 22 16.714 22 12 22C7.28595 22 4.92893 22 3.46447 20.5355C2 19.0711 2 16.714 2 12ZM12 6.25C12.4142 6.25 12.75 6.58579 12.75 7V12.1893L14.4697 10.4697C14.7626 10.1768 15.2374 10.1768 15.5303 10.4697C15.8232 10.7626 15.8232 11.2374 15.5303 11.5303L12.5303 14.5303C12.3897 14.671 12.1989 14.75 12 14.75C11.8011 14.75 11.6103 14.671 11.4697 14.5303L8.46967 11.5303C8.17678 11.2374 8.17678 10.7626 8.46967 10.4697C8.76256 10.1768 9.23744 10.1768 9.53033 10.4697L11.25 12.1893V7C11.25 6.58579 11.5858 6.25 12 6.25ZM8 16.25C7.58579 16.25 7.25 16.5858 7.25 17C7.25 17.4142 7.58579 17.75 8 17.75H16C16.4142 17.75 16.75 17.4142 16.75 17C16.75 16.5858 16.4142 16.25 16 16.25H8Z"),
    STORAGE_LOCATION("M2 12C2 7.28595 2 4.92893 3.46447 3.46447C4.92893 2 7.28595 2 12 2C16.714 2 19.0711 2 20.5355 3.46447C22 4.92893 22 7.28595 22 12C22 16.714 22 19.0711 20.5355 20.5355C19.0711 22 16.714 22 12 22C7.28595 22 4.92893 22 3.46447 20.5355C2 19.0711 2 16.714 2 12ZM12.5303 16.5303C12.2374 16.8232 11.7626 16.8232 11.4697 16.5303L8.46967 13.5303C8.17678 13.2374 8.17678 12.7626 8.46967 12.4697C8.76256 12.1768 9.23744 12.1768 9.53033 12.4697L11.25 14.1893V8C11.25 7.58579 11.5858 7.25 12 7.25C12.4142 7.25 12.75 7.58579 12.75 8V14.1893L14.4697 12.4697C14.7626 12.1768 15.2374 12.1768 15.5303 12.4697C15.8232 12.7626 15.8232 13.2374 15.5303 13.5303L12.5303 16.5303Z"),
    DELETE_DOWNLOADS("M2 12C2 7.28595 2 4.92893 3.46447 3.46447C4.92893 2 7.28595 2 12 2C16.714 2 19.0711 2 20.5355 3.46447C22 4.92893 22 7.28595 22 12C22 16.714 22 19.0711 20.5355 20.5355C19.0711 22 16.714 22 12 22C7.28595 22 4.92893 22 3.46447 20.5355C2 19.0711 2 16.714 2 12ZM8.46967 8.46967C8.76256 8.17678 9.23744 8.17678 9.53033 8.46967L12 10.9393L14.4697 8.46967C14.7626 8.17678 15.2374 8.17678 15.5303 8.46967C15.8232 8.76256 15.8232 9.23744 15.5303 9.53033L13.0607 12L15.5303 14.4697C15.8232 14.7626 15.8232 15.2374 15.5303 15.5303C15.2374 15.8232 14.7626 15.8232 14.4697 15.5303L12 13.0607L9.53033 15.5303C9.23744 15.8232 8.76256 15.8232 8.46967 15.5303C8.17678 15.2374 8.17678 14.7626 8.46967 14.4697L10.9393 12L8.46967 9.53033C8.17678 9.23744 8.17678 8.76256 8.46967 8.46967Z"),
    APP_THEME("M12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22C13.8214 22 15.3409 20.5225 15.3409 18.7011C15.3409 17.8923 15.6327 17.1353 16.1466 16.5744C16.6605 16.0135 17.3756 15.6705 18.1705 15.6705H19C20.6569 15.6705 22 14.3273 22 12.6705C22 6.77977 17.5228 2 12 2ZM6.5 11.5C5.67157 11.5 5 10.8284 5 10C5 9.17157 5.67157 8.5 6.5 8.5C7.32843 8.5 8 9.17157 8 10C8 10.8284 7.32843 11.5 6.5 11.5ZM9.5 7.5C8.67157 7.5 8 6.82843 8 6C8 5.17157 8.67157 4.5 9.5 4.5C10.3284 4.5 11 5.17157 11 6C11 6.82843 10.3284 7.5 9.5 7.5ZM14.5 7.5C13.6716 7.5 13 6.82843 13 6C13 5.17157 13.6716 4.5 14.5 4.5C15.3284 4.5 16 5.17157 16 6C16 6.82843 15.3284 7.5 14.5 7.5ZM17.5 11.5C16.6716 11.5 16 10.8284 16 10C16 9.17157 16.6716 8.5 17.5 8.5C18.3284 8.5 19 9.17157 19 10C19 10.8284 18.3284 11.5 17.5 11.5Z"),
    EPISODE_VIEW("M2 12C2 7.28595 2 4.92893 3.46447 3.46447C4.92893 2 7.28595 2 12 2C16.714 2 19.0711 2 20.5355 3.46447C22 4.92893 22 7.28595 22 12C22 16.714 22 19.0711 20.5355 20.5355C19.0711 22 16.714 22 12 22C7.28595 22 4.92893 22 3.46447 20.5355C2 19.0711 2 16.714 2 12ZM17.25 8.25C17.6642 8.25 18 7.91421 18 7.5C18 7.08579 17.6642 6.75 17.25 6.75H11.75C11.3358 6.75 11 7.08579 11 7.5C11 7.91421 11.3358 8.25 11.75 8.25H17.25ZM17.25 12.75C17.6642 12.75 18 12.4142 18 12C18 11.5858 17.6642 11.25 17.25 11.25H11.75C11.3358 11.25 11 11.5858 11 12C11 12.4142 11.3358 12.75 11.75 12.75H17.25ZM17.25 17.25C17.6642 17.25 18 16.9142 18 16.5C18 16.0858 17.6642 15.75 17.25 15.75H11.75C11.3358 15.75 11 16.0858 11 16.5C11 16.9142 11.3358 17.25 11.75 17.25H17.25ZM8.53033 7.53033C8.82322 7.23744 8.82322 6.76256 8.53033 6.46967C8.23744 6.17678 7.76256 6.17678 7.46967 6.46967L6.75 7.18934L6.53033 6.96967C6.23744 6.67678 5.76256 6.67678 5.46967 6.96967C5.17678 7.26256 5.17678 7.73744 5.46967 8.03033L6.21967 8.78033C6.51256 9.07322 6.98744 9.07322 7.28033 8.78033L8.53033 7.53033ZM8.53033 12.0303C8.82322 11.7374 8.82322 11.2626 8.53033 10.9697C8.23744 10.6768 7.76256 10.6768 7.46967 10.9697L6.75 11.6893L6.53033 11.4697C6.23744 11.1768 5.76256 11.1768 5.46967 11.4697C5.17678 11.7626 5.17678 12.2374 5.46967 12.5303L6.21967 13.2803C6.51256 13.5732 6.98744 13.5732 7.28033 13.2803L8.53033 12.0303ZM8.53033 16.5303C8.82322 16.2374 8.82322 15.7626 8.53033 15.4697C8.23744 15.1768 7.76256 15.1768 7.46967 15.4697L6.75 16.1893L6.53033 15.9697C6.23744 15.6768 5.76256 15.6768 5.46967 15.9697C5.17678 16.2626 5.17678 16.7374 5.46967 17.0303L6.21967 17.7803C6.51256 18.0732 6.98744 18.0732 7.28033 17.7803L8.53033 16.5303Z"),
    APP_ACTIVITY("M12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2ZM8 16.75C7.58579 16.75 7.25 16.4142 7.25 16V13C7.25 12.5858 7.58579 12.25 8 12.25C8.41421 12.25 8.75 12.5858 8.75 13V16C8.75 16.4142 8.41421 16.75 8 16.75ZM12 16.75C11.5858 16.75 11.25 16.4142 11.25 16V10C11.25 9.58579 11.5858 9.25 12 9.25C12.4142 9.25 12.75 9.58579 12.75 10V16C12.75 16.4142 12.4142 16.75 12 16.75ZM16 16.75C15.5858 16.75 15.25 16.4142 15.25 16V7C15.25 6.58579 15.5858 6.25 16 6.25C16.4142 6.25 16.75 6.58579 16.75 7V16C16.75 16.4142 16.4142 16.75 16 16.75Z"),
    EXTERNAL_PLAYER("M12 22C6.47715 22 2 17.5228 2 12C2 8.87638 3.43217 6.08725 5.67568 4.25345L13.3284 12.5099C13.5381 12.7362 13.865 12.811 14.1523 12.6983C14.4395 12.5857 14.6284 12.3086 14.6284 12.0001V7.50011C14.6284 7.0859 14.2926 6.75011 13.8784 6.75011C13.4642 6.75011 13.1284 7.0859 13.1284 7.50011V10.0876L6.91715 3.38628C8.40658 2.50549 10.1443 2 12 2C17.5228 2 22 6.47715 22 12C22 15.1255 20.5661 17.9162 18.3202 19.7499L10.4207 11.4818C10.209 11.2603 9.88379 11.1898 9.59934 11.3038C9.31488 11.4179 9.12843 11.6935 9.12843 12V16.5C9.12843 16.9142 9.46422 17.25 9.87843 17.25C10.2926 17.25 10.6284 16.9142 10.6284 16.5V13.8706L17.0752 20.6182C15.5875 21.4962 13.8526 22 12 22Z"),
    DETAIL_SPRINT("M12.9062 2.21875C13.284 1.96691 13.7915 2.05943 14.0538 2.42777L18.0538 8.02777C18.2575 8.31301 18.2678 8.69466 18.0801 8.99049C17.8924 9.28632 17.5379 9.45833 17.1875 9.45833H13.625V13.875C13.625 14.2892 13.2892 14.625 12.875 14.625C12.4608 14.625 12.125 14.2892 12.125 13.875V8.25C12.125 7.83579 12.4608 7.5 12.875 7.5H15.6875L12.6875 3.3C12.4252 2.93166 12.5284 2.47059 12.9062 2.21875ZM6.8125 14.5417H10.375V10.125C10.375 9.71079 10.7108 9.375 11.125 9.375C11.5392 9.375 11.875 9.71079 11.875 10.125V15.75C11.875 16.1642 11.5392 16.5 11.125 16.5H8.3125L11.3125 20.7C11.5748 21.0683 11.4716 21.5294 11.0938 21.7812C10.716 22.0331 10.2085 21.9406 9.94623 21.5722L5.94623 15.9722C5.74249 15.687 5.7322 15.3053 5.91992 15.0095C6.10764 14.7137 6.46214 14.5417 6.8125 14.5417Z"),
    AUTOPLAY_NEXT("M17.25 5C17.6642 5 18 5.33579 18 5.75V18.25C18 18.6642 17.6642 19 17.25 19C16.8358 19 16.5 18.6642 16.5 18.25V5.75C16.5 5.33579 16.8358 5 17.25 5ZM6.69354 15.8458C5.93375 16.2944 5 15.7105 5 14.7868V9.21316C5 8.28947 5.93375 7.70561 6.69354 8.15419L11.4137 10.941C12.1954 11.4026 12.1954 12.5974 11.4137 13.059L6.69354 15.8458Z"),
    TRAILERS_TV("M2 12C2 7.28595 2 4.92893 3.46447 3.46447C4.92893 2 7.28595 2 12 2C16.714 2 19.0711 2 20.5355 3.46447C22 4.92893 22 7.28595 22 12C22 16.714 22 19.0711 20.5355 20.5355C19.0711 22 16.714 22 12 22C7.28595 22 4.92893 22 3.46447 20.5355C2 19.0711 2 16.714 2 12ZM9.5 8.75C9.5 8.0125 10.3242 7.5708 10.9419 7.9749L15.9085 11.2249C16.4746 11.5952 16.4746 12.4048 15.9085 12.7751L10.9419 16.0251C10.3242 16.4292 9.5 15.9875 9.5 15.25V8.75Z"),
    LOW_PERF("M12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2ZM8 10.25C7.58579 10.25 7.25 10.5858 7.25 11V16C7.25 16.4142 7.58579 16.75 8 16.75C8.41421 16.75 8.75 16.4142 8.75 16V11C8.75 10.5858 8.41421 10.25 8 10.25ZM12 7.25C11.5858 7.25 11.25 7.58579 11.25 8V16C11.25 16.4142 11.5858 16.75 12 16.75C12.4142 16.75 12.75 16.4142 12.75 16V8C12.75 7.58579 12.4142 7.25 12 7.25ZM16 12.25C15.5858 12.25 15.25 12.5858 15.25 13V16C15.25 16.4142 15.5858 16.75 16 16.75C16.4142 16.75 16.75 16.4142 16.75 16V13C16.75 12.5858 16.4142 12.25 16 12.25Z"),
    TIMELINE_COLOR("M12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2ZM7.25 8C7.25 7.58579 7.58579 7.25 8 7.25H9.25V6C9.25 5.58579 9.58579 5.25 10 5.25C10.4142 5.25 10.75 5.58579 10.75 6V7.25H12C12.4142 7.25 12.75 7.58579 12.75 8C12.75 8.41421 12.4142 8.75 12 8.75H10.75V18C10.75 18.4142 10.4142 18.75 10 18.75C9.58579 18.75 9.25 18.4142 9.25 18V8.75H8C7.58579 8.75 7.25 8.41421 7.25 8ZM12 15.25C11.5858 15.25 11.25 15.5858 11.25 16C11.25 16.4142 11.5858 16.75 12 16.75H13.25V18C13.25 18.4142 13.5858 18.75 14 18.75C14.4142 18.75 14.75 18.4142 14.75 18V16.75H16C16.4142 16.75 16.75 16.4142 16.75 16C16.75 15.5858 16.4142 15.25 16 15.25H14.75V6C14.75 5.58579 14.4142 5.25 14 5.25C13.5858 5.25 13.25 5.58579 13.25 6V15.25H12Z"),
    SKIP_INTRO("M4 7.75C4 7.0125 4.8242 6.5708 5.4419 6.9749L10.4085 10.2249C10.9746 10.5952 10.9746 11.4048 10.4085 11.7751L5.4419 15.0251C4.8242 15.4292 4 14.9875 4 14.25V7.75ZM11.5 7.75C11.5 7.0125 12.3242 6.5708 12.9419 6.9749L17.9085 10.2249C18.4746 10.5952 18.4746 11.4048 17.9085 11.7751L12.9419 15.0251C12.3242 15.4292 11.5 14.9875 11.5 14.25V7.75Z"),
    BRIGHTNESS("M20 8.69V4h-4.69L12 .69 8.69 4H4v4.69L.69 12 4 15.31V20h4.69L12 23.31 15.31 20H20v-4.69L23.31 12 20 8.69zM12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6 6 2.69 6 6-2.69 6-6 6z"),
    VOLUME("M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"),
    DOUBLE_TAP("M12 2C7.58172 2 4 5.58172 4 10V14C4 18.4183 7.58172 22 12 22C16.4183 22 20 18.4183 20 14V10C20 5.58172 16.4183 2 12 2ZM12 6.25C12.4142 6.25 12.75 6.58579 12.75 7V10C12.75 10.4142 12.4142 10.75 12 10.75C11.5858 10.75 11.25 10.4142 11.25 10V7C11.25 6.58579 11.5858 6.25 12 6.25Z"),
    REMEMBER_PREFS("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1.29 14.29a.996.996 0 0 1-1.41 0L6.7 13.7a.996.996 0 1 1 1.41-1.41L10 14.17l5.88-5.88a.996.996 0 1 1 1.41 1.41l-6.58 6.59z"),
    EXPORT("M12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2ZM12.75 7V10.25H16C16.4142 10.25 16.75 10.5858 16.75 11C16.75 11.4142 16.4142 11.75 16 11.75H12.75V15C12.75 15.4142 12.4142 15.75 12 15.75C11.5858 15.75 11.25 15.4142 11.25 15V11.75H8C7.58579 11.75 7.25 11.4142 7.25 11C7.25 10.5858 7.58579 10.25 8 10.25H11.25V7C11.25 6.58579 11.5858 6.25 12 6.25C12.4142 6.25 12.75 6.58579 12.75 7Z"),
    IMPORT("M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM12 17l-5-5h3V8h4v4h3l-5 5z"),
    DNS_SERVER("M2.58579 3.58579C2 4.17157 2 5.11438 2 7C2 8.88562 2 9.82843 2.58579 10.4142C3.17157 11 4.11438 11 6 11H18C19.8856 11 20.8284 11 21.4142 10.4142C22 9.82843 22 8.88562 22 7C22 5.11438 22 4.17157 21.4142 3.58579C20.8284 3 19.8856 3 18 3H6C4.11438 3 3.17157 3 2.58579 3.58579ZM9 8.75C8.58579 8.75 8.25 8.41421 8.25 8V6C8.25 5.58579 8.58579 5.25 9 5.25C9.41421 5.25 9.75 5.58579 9.75 6V8C9.75 8.41421 9.41421 8.75 9 8.75ZM13.5 6.25C13.0858 6.25 12.75 6.58579 12.75 7C12.75 7.41421 13.0858 7.75 13.5 7.75H18C18.4142 7.75 18.75 7.41421 18.75 7C18.75 6.58579 18.4142 6.25 18 6.25H13.5ZM6 8.75C5.58579 8.75 5.25 8.41421 5.25 8L5.25 6C5.25 5.58579 5.58579 5.25 6 5.25C6.41421 5.25 6.75 5.58579 6.75 6V8C6.75 8.41421 6.41421 8.75 6 8.75ZM2.58579 13.5858C2 14.1716 2 15.1144 2 17C2 18.8856 2 19.8284 2.58579 20.4142C3.17157 21 4.11438 21 6 21H18C19.8856 21 20.8284 21 21.4142 20.4142C22 19.8284 22 18.8856 22 17C22 15.1144 22 14.1716 21.4142 13.5858C20.8284 13 19.8856 13 18 13H6C4.11438 13 3.17157 13 2.58579 13.5858ZM12.75 17C12.75 16.5858 13.0858 16.25 13.5 16.25H18C18.4142 16.25 18.75 16.5858 18.75 17C18.75 17.4142 18.4142 17.75 18 17.75H13.5C13.0858 17.75 12.75 17.4142 12.75 17ZM5.25 18C5.25 18.4142 5.58579 18.75 6 18.75C6.41421 18.75 6.75 18.4142 6.75 18V16C6.75 15.5858 6.41421 15.25 6 15.25C5.58579 15.25 5.25 15.5858 5.25 16L5.25 18ZM9 18.75C8.58579 18.75 8.25 18.4142 8.25 18V16C8.25 15.5858 8.58579 15.25 9 15.25C9.41421 15.25 9.75 15.5858 9.75 16V18C9.75 18.4142 9.41421 18.75 9 18.75Z"),
    ABOUT_DEVICE("M16 1H8C6.34 1 5 2.34 5 4v16c0 1.66 1.34 3 3 3h8c1.66 0 3-1.34 3-3V4c0-1.66-1.34-3-3-3zm-2 20h-4c-.55 0-1-.45-1-1s.45-1 1-1h4c.55 0 1 .45 1 1s-.45 1-1 1zm3-3H7c-.55 0-1-.45-1-1V5c0-.55.45-1 1-1h10c.55 0 1 .45 1 1v12c0 .55-.45 1-1 1z"),
    SUPPORT_TICKET("M12 2C6.47715 2 2 6.47715 2 12C2 13.5997 2.37562 15.1116 3.04346 16.4525C3.22094 16.8088 3.28001 17.2161 3.17712 17.6006L2.58151 19.8267C2.32295 20.793 3.20701 21.677 4.17335 21.4185L6.39939 20.8229C6.78393 20.72 7.19121 20.7791 7.54753 20.9565C8.88837 21.6244 10.4003 22 12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2ZM8 12.75C7.58579 12.75 7.25 12.4142 7.25 12C7.25 11.5858 7.58579 11.25 8 11.25C8.41421 11.25 8.75 11.5858 8.75 12C8.75 12.4142 8.41421 12.75 8 12.75ZM12 12.75C11.5858 12.75 11.25 12.4142 11.25 12C11.25 11.5858 11.5858 11.25 12 11.25C12.4142 11.25 12.75 11.5858 12.75 12C12.75 12.4142 12.75 12.75 12 12.75ZM16 12.75C15.5858 12.75 15.25 12.4142 15.25 12C15.25 11.5858 15.5858 11.25 16 11.25C16.4142 11.25 16.75 11.5858 16.75 12C16.75 12.4142 16.4142 12.75 16 12.75Z"),
    CAMERA("M9.77778 21H14.2222C17.3433 21 18.9038 21 20.0248 20.2646C20.51 19.9462 20.9267 19.5371 21.251 19.0607C22 17.9601 22 16.4279 22 13.3636C22 10.2994 22 8.76721 21.251 7.6666C20.9267 7.19014 20.51 6.78104 20.0248 6.46268C19.3044 5.99013 18.4027 5.82123 17.022 5.76086C16.3631 5.76086 15.7959 5.27068 15.6667 4.63636C15.4728 3.68489 14.6219 3 13.6337 3H10.3663C9.37805 3 8.52715 3.68489 8.33333 4.63636C8.20412 5.27068 7.63685 5.76086 6.978 5.76086C5.59733 5.82123 4.69555 5.99013 3.97524 6.46268C3.48995 6.78104 3.07328 7.19014 2.74902 7.6666C2 8.76721 2 10.2994 2 13.3636C2 16.4279 2 17.9601 2.74902 19.0607C3.07328 19.5371 3.48995 19.9462 3.97524 20.2646C5.09624 21 6.65675 21 9.77778 21ZM12 9.27273C9.69881 9.27273 7.83333 11.1043 7.83333 13.3636C7.83333 15.623 9.69881 17.4545 12 17.4545C14.3012 17.4545 16.1667 15.623 16.1667 13.3636C16.1667 11.1043 14.3012 9.27273 12 9.27273ZM12 10.9091C10.6193 10.9091 9.5 12.008 9.5 13.3636C9.5 14.7192 10.6193 15.8182 12 15.8182C13.3807 15.8182 14.5 14.7192 14.5 13.3636C14.5 12.008 13.3807 10.9091 12 10.9091ZM16.7222 10.0909C16.7222 9.63904 17.0953 9.27273 17.5556 9.27273H18.6667C19.1269 9.27273 19.5 9.63904 19.5 10.0909C19.5 10.5428 19.1269 10.9091 18.6667 10.9091H17.5556C17.0953 10.9091 16.7222 10.5428 16.7222 10.0909Z"),
    BOLD_SKIP_NEXT("M17.25 5C17.6642 5 18 5.33579 18 5.75V18.25C18 18.6642 17.6642 19 17.25 19C16.8358 19 16.5 18.6642 16.5 18.25V5.75C16.5 5.33579 16.8358 5 17.25 5ZM6.69354 15.8458C5.93375 16.2944 5 15.7105 5 14.7868V9.21316C5 8.28947 5.93375 7.70561 6.69354 8.15419L11.4137 10.941C12.1954 11.4026 12.1954 12.5974 11.4137 13.059L6.69354 15.8458Z"),
    BOLD_FAST_FORWARD("M4 7.75C4 7.0125 4.8242 6.5708 5.4419 6.9749L10.4085 10.2249C10.9746 10.5952 10.9746 11.4048 10.4085 11.7751L5.4419 15.0251C4.8242 15.4292 4 14.9875 4 14.25V7.75ZM11.5 7.75C11.5 7.0125 12.3242 6.5708 12.9419 6.9749L17.9085 10.2249C18.4746 10.5952 18.4746 11.4048 17.9085 11.7751L12.9419 15.0251C12.3242 15.4292 11.5 14.9875 11.5 14.25V7.75Z"),
    BOLD_WIFI("M2 16C2 14.1144 2 13.1716 2.58579 12.5858C3.17157 12 4.11438 12 6 12H18C19.8856 12 20.8284 12 21.4142 12.5858C22 13.1716 22 14.1144 22 16C22 17.8856 22 18.8284 21.4142 19.4142C20.8284 20 19.8856 20 18 20H6C4.11438 20 3.17157 20 2.58579 19.4142C2 18.8284 2 17.8856 2 16ZM16.5 12V9M21.5833 7.39702C20.7574 5.40286 18.7924 4 16.4996 4C14.2069 4 12.2419 5.40286 11.416 7.39702M19.3806 8.65811C19.0167 7.41107 17.865 6.5 16.5004 6.5C15.1358 6.5 13.984 7.41107 13.6201 8.65811"),
    BOLD_MUSIC("M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"),
    EYE_OFF("M2 12C2 12 5.5 18 12 18C18.5 18 22 12 22 12C22 12 18.5 6 12 6C5.5 6 2 12 2 12ZM12 8.5C13.933 8.5 15.5 10.067 15.5 12C15.5 13.933 13.933 15.5 12 15.5C10.067 15.5 8.5 13.933 8.5 12C8.5 10.067 10.067 8.5 12 8.5Z"),
    NEWSPAPER("M4 20c-.83 0-1.5-.67-1.5-1.5V6c0-.83.67-1.5 1.5-1.5h16c.83 0 1.5.67 1.5 1.5v12c0 .83-.67 1.5-1.5 1.5H4zm0-2h16V6H4v12zm2-2h5v-5H6v5zm0-7h12V7H6v2zm7 7h5v-2h-5v2zm0-3h5v-2h-5v2z"),
    ANIME_GAMES("M17.1593 4C19.7214 4 21.4338 5.75338 21.8491 8.24508L22.9558 14.8856C23.2721 16.7834 21.8388 18.5273 19.9205 18.6749C19.0069 18.7451 18.0694 18.3752 17.4371 17.7429L15.3941 15.7H8.60588L6.56291 17.7429C5.9306 18.3752 4.99307 18.7451 4.07954 18.6749C2.16124 18.5273 0.727915 16.7834 1.04419 14.8856L2.15086 8.24508C2.56616 5.75338 4.27863 4 6.84073 4H17.1593ZM8 7.25C7.58579 7.25 7.25 7.58579 7.25 8V9.25H6C5.58579 9.25 5.25 9.58579 5.25 10C5.25 10.4142 5.58579 10.75 6 10.75H7.25V12C7.25 12.4142 7.58579 12.75 8 12.75C8.41421 12.75 8.75 12.4142 8.75 12V10.75H10C10.4142 10.75 10.75 10.4142 10.75 10C10.75 9.58579 10.4142 9.25 10 9.25H8.75V8C8.75 7.58579 8.41421 7.25 8 7.25ZM17 9.25C16.4477 9.25 16 9.69772 16 10.25C16 10.8023 16.4477 11.25 17 11.25C17.5523 11.25 18 10.8023 18 10.25C18 9.69772 17.5523 9.25 17 9.25ZM14.25 11C13.6977 11 13.25 11.4477 13.25 12C13.25 12.5523 13.6977 13 14.25 13C14.8023 13 15.25 12.5523 15.25 12C15.25 11.4477 14.8023 11 14.25 11Z"),
    AUTO_RESUME("M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22ZM10.6935 15.8458L15.4137 13.059C16.1954 12.5974 16.1954 11.4026 15.4137 10.941L10.6935 8.15419C9.93371 7.70561 9 8.28947 9 9.21316V14.7868C9 15.7105 9.93371 16.2944 10.6935 15.8458Z")
}

@Composable
fun PixelSettingsIcon(
    type: SettingsIconType,
    tint: Color = Color(0xFFE2E2E6),
    modifier: Modifier = Modifier,
    sizeDp: Int = 24
) {
    val path = remember(type) {
        val p = PathParser().parsePathString(type.pathData).toPath()
        p.fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        p
    }

    Canvas(modifier = modifier.size(sizeDp.dp)) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            drawPath(path, color = tint)
        }
    }
}

// ----------------------------------------------------------------------------
// Minimalist Monochrome Skeleton Shimmer
// ----------------------------------------------------------------------------

@Composable
fun Modifier.monochromeShimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return this.background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF0F0F0F),
                Color(0xFF222222),
                Color(0xFF0F0F0F)
            ),
            start = Offset(translateAnim - 400f, translateAnim - 400f),
            end = Offset(translateAnim, translateAnim)
        )
    )
}

// ----------------------------------------------------------------------------
// Main Black & White App Settings Dialog
// ----------------------------------------------------------------------------

@Composable
fun AnimeBoxSettingsDialog(
    onDismiss: () -> Unit,
    onSettingsChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Skeleton Loading State
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(280)
        isLoading = false
    }

    // Search query
    var searchQuery by remember { mutableStateOf("") }

    // Preference States
    var trailerEnabled by remember { mutableStateOf(try { AnimeBoxSettings.isTrailerEnabled(context) } catch (_: Exception) { false }) }
    var lowPerfMode by remember { mutableStateOf(try { AnimeBoxSettings.isLowPerformanceMode(context) } catch (_: Exception) { false }) }
    var fastDetailLoading by remember { mutableStateOf(AnimeBoxSettings.isFastDetailLoadingEnabled(context)) }
    var appTheme by remember { mutableStateOf(try { AnimeBoxSettings.getAppTheme(context) } catch (_: Exception) { "lavender" }) }
    var timelineTheme by remember { mutableStateOf(try { AnimeBoxSettings.getPlayerTimelineTheme(context) } catch (_: Exception) { "lavender" }) }
    var skipIntroTheme by remember { mutableStateOf(try { AnimeBoxSettings.getSkipIntroTheme(context) } catch (_: Exception) { "lavender" }) }
    var skipIntroEnabled by remember { mutableStateOf(try { AnimeBoxSettings.isSkipIntroEnabled(context) } catch (_: Exception) { false }) }
    var brightnessMode by remember { mutableStateOf(try { AnimeBoxSettings.getBrightnessMode(context) } catch (_: Exception) { "gesture" }) }
    var volumeMode by remember { mutableStateOf(try { AnimeBoxSettings.getVolumeMode(context) } catch (_: Exception) { "gesture" }) }
    var defaultEpViewMode by remember { mutableStateOf(try { AnimeBoxSettings.getDefaultEpisodeViewMode(context) } catch (_: Exception) { "image" }) }
    var seekDuration by remember { mutableIntStateOf(try { AnimeBoxSettings.getSeekDuration(context) } catch (_: Exception) { 10 }) }
    var rememberPlaybackPrefs by remember { mutableStateOf(try { AnimeBoxSettings.isRememberPlaybackPrefsEnabled(context) } catch (_: Exception) { true }) }

    // Storage & Download Manager states
    val downloadManager = remember { AnimeDownloadManager.getInstance(context) }
    var storageStats by remember { mutableStateOf(StorageStats(128.0, 45.0, 1.2, 81.8)) }
    val globalPrefs = remember { context.getSharedPreferences("AnimeBoxPrefs", android.content.Context.MODE_PRIVATE) }
    var wifiOnlyDownloads by remember { mutableStateOf(globalPrefs.getBoolean("wifi_only_downloads", false)) }
    var smartDownloads by remember { mutableStateOf(globalPrefs.getBoolean("smart_downloads", true)) }
    var downloadQuality by remember { mutableStateOf(globalPrefs.getString("download_quality", "ask") ?: "ask") }
    var downloadServer by remember { mutableStateOf(com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.getDownloadServer(context)) }
    var downloadLocation by remember { mutableStateOf(globalPrefs.getString("download_location", "Internal Storage") ?: "Internal Storage") }
    var maturityRating by remember { mutableStateOf(AnimeBoxSettings.getMaturityRating(context)) }
    var playerScreenshotEnabled by remember { mutableStateOf(AnimeBoxSettings.isPlayerScreenshotEnabled(context)) }
    var autoplayNextEpisode by remember { mutableStateOf(AnimeBoxSettings.isAutoplayNextEpisodeEnabled(context)) }
    var autoplayPreviews by remember { mutableStateOf(AnimeBoxSettings.isAutoplayPreviewsEnabled(context)) }
    var blurEpisodeSpoilers by remember { mutableStateOf(AnimeBoxSettings.isBlurEpisodeSpoilersEnabled(context)) }

    // Load storage stats asynchronously
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val stats = downloadManager.getStorageStats()
                withContext(Dispatchers.Main) {
                    storageStats = stats
                }
            } catch (_: Exception) {}
        }
    }

    // Dialog Modal states
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showCustomColorPicker by remember { mutableStateOf(false) }
    val customHex = AnimeBoxSettings.getCustomTimelineColor(context)
    var showAppThemePicker by remember { mutableStateOf(false) }
    var showSyncModal by remember { mutableStateOf(false) }
    var targetSyncProvider by remember { mutableStateOf(com.lagradost.cloudstream3.ui.animebox.sync.SyncProvider.ALL) }
    var alertTitle by remember { mutableStateOf("") }
    var alertMessage by remember { mutableStateOf("") }
    var showAlert by remember { mutableStateOf(false) }
    var showDnsModal by remember { mutableStateOf(false) }
    var showTicketsModal by remember { mutableStateOf(false) }
    var showAniListPromptForTickets by remember { mutableStateOf(false) }

    // Export Data SAF Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val jsonStr = AnimeBoxSettings.exportDataToJson(context)
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    OutputStreamWriter(os).use { writer ->
                        writer.write(jsonStr)
                    }
                }
                alertTitle = "Export Successful"
                alertMessage = "Your profile details, watchlist, watch history, timestamps, and settings have been exported successfully."
                showAlert = true
            } catch (e: Exception) {
                alertTitle = "Export Failed"
                alertMessage = "Could not export app data: ${e.localizedMessage}"
                showAlert = true
            }
        }
    }

    // Import Data SAF Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val stringBuilder = StringBuilder()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line: String? = reader.readLine()
                        while (line != null) {
                            stringBuilder.append(line)
                            line = reader.readLine()
                        }
                    }
                }
                val jsonContent = stringBuilder.toString()
                val result = AnimeBoxSettings.importDataFromJson(context, jsonContent)
                if (result.isSuccess) {
                    trailerEnabled = AnimeBoxSettings.isTrailerEnabled(context)
                    lowPerfMode = AnimeBoxSettings.isLowPerformanceMode(context)
                    appTheme = AnimeBoxSettings.getAppTheme(context)
                    timelineTheme = AnimeBoxSettings.getPlayerTimelineTheme(context)
                    skipIntroTheme = AnimeBoxSettings.getSkipIntroTheme(context)
                    skipIntroEnabled = AnimeBoxSettings.isSkipIntroEnabled(context)
                    brightnessMode = AnimeBoxSettings.getBrightnessMode(context)
                    volumeMode = AnimeBoxSettings.getVolumeMode(context)
                    defaultEpViewMode = AnimeBoxSettings.getDefaultEpisodeViewMode(context)
                    playerScreenshotEnabled = AnimeBoxSettings.isPlayerScreenshotEnabled(context)
                    onSettingsChanged()

                    alertTitle = "Import Successful"
                    alertMessage = "All app data, profiles, watchlist, history timestamps, and settings have been imported successfully!"
                    showAlert = true
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unrecognized backup format."
                    alertTitle = "Import Failed"
                    alertMessage = "Failed to import data: $errorMsg\n\nPlease ensure you selected a valid FireFly backup file."
                    showAlert = true
                }
            } catch (e: Exception) {
                alertTitle = "Import Failed"
                alertMessage = "Error reading backup file: ${e.localizedMessage}"
                showAlert = true
            }
        }
    }

    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // -------------------------------------------------------------
            // Google Pixel Search Header with `<` Chevron & Navbar Search Icon
            // -------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF1E1F22))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // `<` Chevron Back Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(38.dp)
                    ) {
                        PixelSettingsIcon(
                            type = SettingsIconType.CHEVRON_LEFT,
                            tint = Color(0xFFE2E2E6),
                            sizeDp = 22
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Navbar Search Icon
                    Icon(
                        imageVector = CustomSearchIcon,
                        contentDescription = "Search",
                        tint = Color(0xFFC4C7C5),
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Text Field / Hint (Centered Vertically with decorationBox)
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        cursorBrush = SolidColor(Color.White),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search settings",
                                        color = Color(0xFF8E918F),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Normal,
                                        style = TextStyle(
                                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFFC4C7C5),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // Main Settings List or Skeleton Loading
            // -------------------------------------------------------------
            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .monochromeShimmer()
                    )
                    repeat(6) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .monochromeShimmer()
                            )
                            Spacer(modifier = Modifier.width(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .monochromeShimmer()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .height(11.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .monochromeShimmer()
                                )
                            }
                        }
                    }
                }
            } else {
                val query = searchQuery.trim().lowercase()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ---------------------------------------------------------
                    // SECTION 1: Network & Downloads
                    // ---------------------------------------------------------
                    if (query.isEmpty() || "download".contains(query) || "wifi".contains(query) || "quality".contains(query) || "storage".contains(query) || "server".contains(query) || "subtitle".contains(query)) {
                        PixelSectionHeader(title = "Network & Downloads")

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.WIFI,
                            title = "Wi-Fi Only",
                            subtitle = "Download episodes exclusively over Wi-Fi networks\nto conserve cellular mobile data",
                            checked = wifiOnlyDownloads,
                            onCheckedChange = {
                                wifiOnlyDownloads = it
                                globalPrefs.edit().putBoolean("wifi_only_downloads", it).apply()
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.SMART_DOWNLOADS,
                            title = "Smart Downloads",
                            subtitle = "Automatically deletes completed episodes after watching\nand downloads the next episode in queue",
                            checked = smartDownloads,
                            onCheckedChange = {
                                smartDownloads = it
                                globalPrefs.edit().putBoolean("smart_downloads", it).apply()
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSelectorRow(
                            iconType = SettingsIconType.DOWNLOAD_QUALITY,
                            title = "Video Quality",
                            subtitle = "Choose preferred stream resolution and encoding\nquality for saved offline anime episodes",
                            currentValue = downloadQuality,
                            options = listOf(
                                "Always Ask" to "ask",
                                "1080p (Standard)" to "1080p (Standard)",
                                "720p (High)" to "720p (High)",
                                "480p (Data Saver)" to "480p (Data Saver)"
                            ),
                            onOptionSelected = {
                                downloadQuality = it
                                globalPrefs.edit().putString("download_quality", it).apply()
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        val selectedDownloadServerName = com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.DOWNLOAD_SERVERS
                            .firstOrNull { it.key == downloadServer }?.displayName ?: "Auto (Default)"
                        PixelSettingsSelectorRow(
                            iconType = SettingsIconType.DNS_SERVER,
                            title = "Download Server",
                            subtitle = "Choose a default provider for episode downloads. Auto follows the server priorities above.",
                            currentValue = selectedDownloadServerName,
                            options = com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.DOWNLOAD_SERVERS
                                .filter { it.key == com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.SERVER_AUTO || com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isServerEnabled(it.key) }
                                .map { it.displayName to it.key },
                            onOptionSelected = { selected ->
                                downloadServer = selected
                                com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.setDownloadServer(context, selected)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSelectorRow(
                            iconType = SettingsIconType.STORAGE_LOCATION,
                            title = "Download Location",
                            subtitle = "Specify the internal storage or SD card directory\nfor saving offline anime videos and covers",
                            currentValue = downloadLocation,
                            options = listOf(
                                "Internal Storage" to "Internal Storage",
                                "SD Card" to "SD Card"
                            ),
                            onOptionSelected = {
                                downloadLocation = it
                                globalPrefs.edit().putString("download_location", it).apply()
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsActionRow(
                            iconType = SettingsIconType.DELETE_DOWNLOADS,
                            title = "Delete All Downloads",
                            subtitle = "Permanently remove all offline video files\nand cached thumbnails to free up device space",
                            onClick = { showDeleteAllConfirm = true }
                        )

                        // Minimalist Monochrome Storage Visualizer
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF141414))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Device Storage",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Internal Storage",
                                    color = Color(0xFF8E918F),
                                    fontSize = 12.5.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val total = if (storageStats.totalGb.isNaN() || storageStats.totalGb <= 0.0) 128.0 else storageStats.totalGb
                            val used = if (storageStats.usedGb.isNaN() || storageStats.usedGb < 0.0) 45.0 else storageStats.usedGb
                            val app = if (storageStats.appGb.isNaN() || storageStats.appGb < 0.0) 1.2 else storageStats.appGb
                            val rawUsedFrac = (used / total).toFloat()
                            val rawAppFrac = (app / total).toFloat()
                            val usedFrac = if (rawUsedFrac.isNaN() || rawUsedFrac <= 0f) 0.35f else rawUsedFrac.coerceIn(0.05f, 0.75f)
                            val appFrac = if (rawAppFrac.isNaN() || rawAppFrac <= 0f) 0.05f else rawAppFrac.coerceIn(0.02f, 0.20f)
                            val rawFreeFrac = 1f - usedFrac - appFrac
                            val freeFrac = if (rawFreeFrac.isNaN() || rawFreeFrac <= 0f) 0.60f else rawFreeFrac.coerceIn(0.05f, 0.90f)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF262626))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(appFrac)
                                        .background(Color.White)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(usedFrac)
                                        .background(Color(0xFF6B6B6B))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(freeFrac)
                                        .background(Color(0xFF262626))
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(Color.White, CircleShape))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    val appMb = app * 1024.0
                                    val appStr = if (appMb >= 1024.0) String.format(Locale.US, "%.1f GB", app) else String.format(Locale.US, "%.0f MB", appMb)
                                    Text("FireFly • $appStr", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF6B6B6B), CircleShape))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Used • ${String.format(Locale.US, "%.0f GB", used)}", color = Color(0xFF8E918F), fontSize = 12.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF333333), CircleShape))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    val freeGb = (total - used - app).coerceAtLeast(0.0)
                                    Text("Free • ${String.format(Locale.US, "%.0f GB", freeGb)}", color = Color(0xFF8E918F), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // SECTION 2: Appearance & Display
                    // ---------------------------------------------------------
                    if (query.isEmpty() || "theme".contains(query) || "color".contains(query) || "appearance".contains(query) || "view".contains(query)) {
                        PixelSectionHeader(title = "Appearance & Display")

                        val currentThemeName = when (appTheme) {
                            "light_red", "red" -> "Light Red"
                            "cyan" -> "Cyan Blue"
                            "emerald" -> "Emerald Green"
                            "amber" -> "Golden Amber"
                            else -> "Lavender"
                        }
                        val currentThemeColor = when (appTheme) {
                            "light_red", "red" -> Color(0xFFFF5252)
                            "cyan" -> Color(0xFF00E5FF)
                            "emerald" -> Color(0xFF00E676)
                            "amber" -> Color(0xFFFFAB00)
                            else -> Color(0xFFD0BCFF)
                        }

                        // App Theme Row with Matching Square Selector Box
                        PixelSettingsCustomActionRow(
                            iconType = SettingsIconType.APP_THEME,
                            title = "App Theme",
                            subtitle = "Customize primary navigation accents, selection\nhighlights, and interactive UI component tints",
                            badgeTrailing = {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1F22))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(currentThemeColor)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = currentThemeName,
                                            color = Color(0xFFE2E2E6),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = Color(0xFF8E918F),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            },
                            onClick = { showAppThemePicker = true }
                        )
                        PixelDivider()

                        PixelSettingsSelectorRow(
                            iconType = SettingsIconType.EPISODE_VIEW,
                            title = "Episode View Mode",
                            subtitle = "Switch between rich poster thumbnail cards\nor compact numbered grid tiles on detail pages",
                            currentValue = if (defaultEpViewMode == "number") "Numbering View" else "Image View (Default)",
                            options = listOf(
                                "Image View (Default)" to "image",
                                "Numbering View" to "number"
                            ),
                            onOptionSelected = { selectedKey ->
                                defaultEpViewMode = selectedKey
                                AnimeBoxSettings.setDefaultEpisodeViewMode(context, selectedKey)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.EPISODE_VIEW,
                            title = "Blur Episode Spoilers",
                            subtitle = "Blurs episode preview thumbnails in details and player to prevent spoiler scenes",
                            checked = blurEpisodeSpoilers,
                            onCheckedChange = { checked ->
                                blurEpisodeSpoilers = checked
                                AnimeBoxSettings.setBlurEpisodeSpoilersEnabled(context, checked)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSelectorRow(
                            iconType = SettingsIconType.LOW_PERF,
                            title = "Maturity Rating",
                            subtitle = "Filter 18+ adult anime across all sections of the app",
                            currentValue = maturityRating,
                            options = listOf(
                                "With restrictions (Default)" to "With restrictions",
                                "No restrictions (Show 18+)" to "No restrictions"
                            ),
                            onOptionSelected = { selectedRating ->
                                maturityRating = selectedRating
                                AnimeBoxSettings.setMaturityRating(context, selectedRating)
                                com.lagradost.cloudstream3.ui.animebox.api.AniListClient.refreshClient()
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                    }

                    // ---------------------------------------------------------
                    // SECTION 3: Performance & Battery
                    // ---------------------------------------------------------
                    if (query.isEmpty() || "trailer".contains(query) || "perf".contains(query) || "battery".contains(query) || "optimize".contains(query) || "fast loading".contains(query) || "anime details".contains(query)) {
                        PixelSectionHeader(title = "Performance & Battery")

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.WIFI,
                            title = "Fast Anime Detail Loading",
                            subtitle = "Show anime details as soon as the main data arrives. Turn off to wait for the logo and episode list before showing the page.",
                            checked = fastDetailLoading,
                            onCheckedChange = { enabled ->
                                fastDetailLoading = enabled
                                AnimeBoxSettings.setFastDetailLoadingEnabled(context, enabled)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.TRAILERS_TV,
                            title = "Trailers on Detail Page",
                            subtitle = "Stream official YouTube anime trailers directly\non detail screens when previewing titles",
                            checked = trailerEnabled,
                            onCheckedChange = { checked ->
                                trailerEnabled = checked
                                AnimeBoxSettings.setTrailerEnabled(context, checked)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.CAMERA,
                            title = "Episode Screenshots",
                            subtitle = "Show snapshot camera button in video player controls to capture episode frames",
                            checked = playerScreenshotEnabled,
                            onCheckedChange = { checked ->
                                playerScreenshotEnabled = checked
                                AnimeBoxSettings.setPlayerScreenshotEnabled(context, checked)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.EPISODE_VIEW,
                            title = "Autoplay Next Episode",
                            subtitle = "Automatically load next episode when current video ends",
                            checked = autoplayNextEpisode,
                            onCheckedChange = { checked ->
                                autoplayNextEpisode = checked
                                AnimeBoxSettings.setAutoplayNextEpisodeEnabled(context, checked)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.TRAILERS_TV,
                            title = "Autoplay Previews",
                            subtitle = "Automatically play video previews on focus in spotlight",
                            checked = autoplayPreviews,
                            onCheckedChange = { checked ->
                                autoplayPreviews = checked
                                AnimeBoxSettings.setAutoplayPreviewsEnabled(context, checked)
                                if (checked) lowPerfMode = false
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.LOW_PERF,
                            title = "Low Performance Mode",
                            subtitle = "Disables background blur filters and heavy UI\nanimations to maximize device battery and fps",
                            checked = lowPerfMode,
                            onCheckedChange = { checked ->
                                lowPerfMode = checked
                                AnimeBoxSettings.setLowPerformanceMode(context, checked)
                                onSettingsChanged()
                            }
                        )
                    }

                    // ---------------------------------------------------------
                    // SECTION 4: Video Player Controls
                    // ---------------------------------------------------------
                    if (query.isEmpty() || "player".contains(query) || "timeline".contains(query) || "intro".contains(query) || "brightness".contains(query) || "volume".contains(query) || "skip".contains(query)) {
                        PixelSectionHeader(title = "Video Player Controls")

                        val isYtActive = AnimeBoxSettings.isYouTubeStylePlayer(context)
                        val currentTimelineColor = if (isYtActive) AnimeBoxSettings.getYouTubePlayerTimelineColor(context) else AnimeBoxSettings.getDefaultPlayerTimelineColor(context)
                        val currentTimelineName = if (isYtActive) {
                            val ytTh = AnimeBoxSettings.getYouTubePlayerTimelineTheme(context)
                            val ytHx = AnimeBoxSettings.getYouTubePlayerCustomTimelineColor(context)
                            when (ytTh) {
                                "cyan" -> "Neon Cyan"
                                "gold" -> "Sunset Gold"
                                "green" -> "Emerald Green"
                                "white" -> "Pure White"
                                "lavender" -> "Lavender"
                                "custom" -> "Custom ($ytHx)"
                                else -> "Crimson Red"
                            }
                        } else {
                            when (timelineTheme) {
                                "red" -> "Netflix Red"
                                "cyan" -> "Neon Cyan"
                                "gold" -> "Sunset Gold"
                                "green" -> "Emerald Green"
                                "white" -> "Pure White"
                                "custom" -> "Custom ($customHex)"
                                else -> "Lavender"
                            }
                        }

                        // Timeline Color Row with Matching Square Selector Box
                        PixelSettingsCustomActionRow(
                            iconType = SettingsIconType.TIMELINE_COLOR,
                            title = "Timeline Color",
                            subtitle = "Customize progress seekbar colors and scrub highlights for Default and YouTube Style players",
                            badgeTrailing = {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1F22))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(currentTimelineColor)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = currentTimelineName.substringBefore(" ("),
                                            color = Color(0xFFE2E2E6),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = Color(0xFF8E918F),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            },
                            onClick = { showCustomColorPicker = true }
                        )
                        PixelDivider()

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.SKIP_INTRO,
                            title = "Skip Intro & Outro",
                            subtitle = "Shows dedicated one-tap buttons to effortlessly\nskip opening and ending anime theme timestamps",
                            checked = skipIntroEnabled,
                            onCheckedChange = { checked ->
                                skipIntroEnabled = checked
                                AnimeBoxSettings.setSkipIntroEnabled(context, checked)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSelectorRow(
                            iconType = SettingsIconType.BRIGHTNESS,
                            title = "Brightness Control",
                            subtitle = "Swipe vertically on the left side of the player screen\nto rapidly raise or lower display brightness",
                            currentValue = if (brightnessMode == "gesture") "Gesture Slider (Default)" else "Hidden",
                            options = listOf(
                                "Gesture Slider (Default)" to "gesture",
                                "Hidden" to "hidden"
                            ),
                            onOptionSelected = { selectedKey ->
                                brightnessMode = selectedKey
                                AnimeBoxSettings.setBrightnessMode(context, selectedKey)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSelectorRow(
                            iconType = SettingsIconType.VOLUME,
                            title = "Volume Control",
                            subtitle = "Swipe vertically on the right side of the player screen\nto adjust audio volume without hardware keys",
                            currentValue = if (volumeMode == "gesture") "Gesture Slider (Default)" else "Hidden",
                            options = listOf(
                                "Gesture Slider (Default)" to "gesture",
                                "Hidden" to "hidden"
                            ),
                            onOptionSelected = { selectedKey ->
                                volumeMode = selectedKey
                                AnimeBoxSettings.setVolumeMode(context, selectedKey)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSelectorRow(
                            iconType = SettingsIconType.DOUBLE_TAP,
                            title = "Double Tap Skip",
                            subtitle = "Set the exact amount of seconds jumped forward\nor backward when double-tapping player sides",
                            currentValue = "${seekDuration}s",
                            options = listOf(
                                "5 seconds" to "5",
                                "10 seconds (Default)" to "10",
                                "15 seconds" to "15",
                                "30 seconds" to "30",
                                "60 seconds" to "60"
                            ),
                            onOptionSelected = { selectedKey ->
                                val secs = selectedKey.toIntOrNull() ?: 10
                                seekDuration = secs
                                AnimeBoxSettings.setSeekDuration(context, secs)
                                onSettingsChanged()
                            }
                        )
                        PixelDivider()

                        PixelSettingsSwitchRow(
                            iconType = SettingsIconType.REMEMBER_PREFS,
                            title = "Remember Preferences",
                            subtitle = "Automatically remembers your chosen audio language,\nsubtitle tracks, and custom playback speeds",
                            checked = rememberPlaybackPrefs,
                            onCheckedChange = { checked ->
                                rememberPlaybackPrefs = checked
                                AnimeBoxSettings.setRememberPlaybackPrefsEnabled(context, checked)
                                onSettingsChanged()
                            }
                        )
                    }

                    // ---------------------------------------------------------
                    // SECTION 5: Data & Connectivity
                    // ---------------------------------------------------------
                    if (query.isEmpty() || "export".contains(query) || "import".contains(query) || "backup".contains(query) || "dns".contains(query) || "doh".contains(query) || "sync".contains(query) || "anilist".contains(query) || "mal".contains(query)) {
                        PixelSectionHeader(title = "Account & Cloud Sync")

                        val actProfId = com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager.getActiveProfile(context)
                        val anilistUser = com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxAccountSyncManager.getAniListUser(context, actProfId)

                        PixelSettingsActionRow(
                            iconRes = R.drawable.ic_anilist_official,
                            title = "AniList Sync",
                            subtitle = if (anilistUser != null) "Connected as @${anilistUser.username} • Watchlist synced" else "Connect AniList account for bidirectional watchlist & progress sync",
                            onClick = {
                                targetSyncProvider = com.lagradost.cloudstream3.ui.animebox.sync.SyncProvider.ANILIST
                                showSyncModal = true
                            }
                        )
                        PixelDivider()

                        PixelSettingsActionRow(
                            iconType = SettingsIconType.SUPPORT_TICKET,
                            title = "Support & Feedback Tickets",
                            subtitle = if (anilistUser != null) "Create tickets, submit bugs/suggestions & live chat" else "Log in with AniList to create tickets & chat with support",
                            onClick = {
                                if (anilistUser != null) {
                                    showTicketsModal = true
                                } else {
                                    showAniListPromptForTickets = true
                                }
                            }
                        )
                        PixelDivider()

                        PixelSettingsActionRow(
                            iconType = SettingsIconType.EXPORT,
                            title = "Export App Data",
                            subtitle = "Backup data, history & settings to JSON",
                            onClick = {
                                try {
                                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                    exportLauncher.launch("firefly_backup_$timeStamp.json")
                                } catch (_: Exception) {}
                            }
                        )
                        PixelDivider()

                        PixelSettingsActionRow(
                            iconType = SettingsIconType.IMPORT,
                            title = "Import App Data",
                            subtitle = "Restore data & settings from backup file",
                            onClick = {
                                try {
                                    importLauncher.launch("application/json")
                                } catch (_: Exception) {}
                            }
                        )
                        PixelDivider()

                        val currentDnsMode = AnimeBoxSettings.getDnsMode(context)
                        val dnsDisplayLabel = when (currentDnsMode) {
                            "cloudflare" -> "Cloudflare DoH (1.1.1.1)"
                            "google" -> "Google Public DNS (8.8.8.8)"
                            "adguard" -> "AdGuard DNS (Ad-block)"
                            "quad9" -> "Quad9 DNS (9.9.9.9)"
                            "controld" -> "ControlD (High Speed)"
                            "dnssb" -> "DNS.SB (Privacy-first)"
                            "custom" -> "Custom DNS-over-HTTPS"
                            else -> "System Default (Auto)"
                        }
                        PixelSettingsActionRow(
                            iconType = SettingsIconType.DNS_SERVER,
                            title = "DNS over HTTPS (DoH)",
                            subtitle = "Active: $dnsDisplayLabel",
                            onClick = { showDnsModal = true }
                        )
                    }

                    // ---------------------------------------------------------
                    // SECTION 6: About / Device Info
                    // ---------------------------------------------------------
                    if (query.isEmpty() || "about".contains(query) || "device".contains(query) || "version".contains(query)) {
                        PixelSectionHeader(title = "About")

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PixelSettingsIcon(type = SettingsIconType.ABOUT_DEVICE)
                            Spacer(modifier = Modifier.width(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "FireFly Anime",
                                    color = Color(0xFFE2E2E6),
                                    fontSize = 17.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Version ${com.lagradost.cloudstream3.BuildConfig.VERSION_NAME} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                                    color = Color(0xFF8E918F),
                                    fontSize = 13.5.sp,
                                    lineHeight = 18.sp
                                )
                                Text(
                                    text = "Device: ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                                    color = Color(0xFF8E918F),
                                    fontSize = 13.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        PixelDivider()

                        // Check for App Updates Row (In-App Direct Updater)
                        val activity = context as? Activity
                        val currentVersionName = com.lagradost.cloudstream3.BuildConfig.VERSION_NAME

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF222228))
                                .border(1.dp, Color(0xFF383842), RoundedCornerShape(12.dp))
                                .clickable {
                                    if (activity != null) {
                                        Toast.makeText(context, "Checking for app updates...", Toast.LENGTH_SHORT).show()
                                        coroutineScope.launch {
                                            try {
                                                val hasUpdate = with(com.lagradost.cloudstream3.utils.InAppUpdater) {
                                                    activity.runAutoUpdate(checkAutoUpdate = false)
                                                }
                                                if (!hasUpdate) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "You're on the latest version (v$currentVersionName)", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Update check: ${e.message ?: "Up to date"}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Cannot check for updates in current context", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF2E2E36)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.firefly_logo),
                                    contentDescription = "FireFly",
                                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "FireFly Updater",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF35353E))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "v$currentVersionName",
                                            color = Color(0xFFE0E0E0),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Check GitHub releases for latest updates",
                                    color = Color(0xFFA0A0AB),
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "CHECK",
                                    color = Color.Black,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // -------------------------------------------------------------
        // Dialog Modals
        // -------------------------------------------------------------

        if (showSyncModal) {
            val actProfId = com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager.getActiveProfile(context)
            com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxSyncDialog(
                profileId = actProfId,
                initialProvider = targetSyncProvider,
                onDismiss = { showSyncModal = false }
            )
        }

        // App Theme Picker Modal
        if (showAppThemePicker) {
            AppThemePickerDialog(
                currentTheme = appTheme,
                onDismiss = { showAppThemePicker = false },
                onThemeSelected = { selectedKey ->
                    appTheme = selectedKey
                    AnimeBoxSettings.setAppTheme(context, selectedKey)
                    showAppThemePicker = false
                    onSettingsChanged()
                }
            )
        }

        // Custom Timeline Color Picker Modal
        if (showCustomColorPicker) {
            CustomColorPickerDialog(
                context = context,
                initialColorHex = customHex,
                currentTheme = timelineTheme,
                onDismiss = { showCustomColorPicker = false },
                onSettingsChanged = {
                    timelineTheme = AnimeBoxSettings.getPlayerTimelineTheme(context)
                    onSettingsChanged()
                }
            )
        }

        // Delete All Downloads Confirmation Dialog
        if (showDeleteAllConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteAllConfirm = false },
                title = {
                    Text(
                        text = "Delete All Downloads",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete all downloaded anime episodes? This will permanently remove all offline files from your device storage.",
                        color = Color(0xFFCCCCCC),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    downloadManager.deleteAllDownloads()
                                    storageStats = downloadManager.getStorageStats()
                                } catch (_: Exception) {}
                                showDeleteAllConfirm = false
                                Toast.makeText(context, "All downloads deleted", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Delete All", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllConfirm = false }) {
                        Text("Cancel", color = Color(0xFF8E918F), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF1E1F22),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFCCCCCC),
                shape = RoundedCornerShape(16.dp)
            )
        }

        // DNS Dialog Modal
        if (showDnsModal) {
            AnimeBoxDnsDialog(
                onDismiss = { showDnsModal = false },
                onDnsChanged = { onSettingsChanged() }
            )
        }

        // Alert Dialog for Import/Export outcomes
        if (showAlert) {
            AlertDialog(
                onDismissRequest = { showAlert = false },
                title = {
                    Text(
                        text = alertTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = alertMessage,
                        color = Color(0xFFCCCCCC),
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showAlert = false }) {
                        Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF1E1F22),
                shape = RoundedCornerShape(14.dp)
            )
        }

        val actProf = remember { com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager.getActiveProfile(context) }
        val currentAniUser = remember(actProf) { com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxAccountSyncManager.getAniListUser(context, actProf) }

        if (showTicketsModal && currentAniUser != null) {
            com.lagradost.cloudstream3.ui.animebox.tickets.AnimeBoxTicketListDialog(
                anilistUser = currentAniUser,
                profileId = actProf,
                onDismiss = { showTicketsModal = false }
            )
        }

        if (showAniListPromptForTickets) {
            AlertDialog(
                onDismissRequest = { showAniListPromptForTickets = false },
                title = {
                    Text(text = "AniList Account Required", color = Color.White, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = "Support tickets and live chat threads are linked with your AniList identity. Please connect your AniList account first to submit tickets and chat with our team.",
                        color = Color(0xFFCCCCCC),
                        fontSize = 13.5.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showAniListPromptForTickets = false
                            targetSyncProvider = com.lagradost.cloudstream3.ui.animebox.sync.SyncProvider.ANILIST
                            showSyncModal = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF02A9FF), contentColor = Color.White)
                    ) {
                        Text("Connect AniList", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAniListPromptForTickets = false }) {
                        Text("Cancel", color = Color(0xFF8E918F))
                    }
                },
                containerColor = Color(0xFF1E1F22),
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}

// ----------------------------------------------------------------------------
// Pixel Settings Flat Rows (Exact Google Settings Hierarchy & Typography)
// ----------------------------------------------------------------------------

@Composable
fun PixelSectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF8E918F),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
fun PixelDivider() {
    HorizontalDivider(
        color = Color(0xFF1C1D20),
        thickness = 1.dp,
        modifier = Modifier.padding(start = 44.dp)
    )
}

@Composable
fun PixelSettingsSwitchRow(
    iconType: SettingsIconType,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PixelSettingsIcon(type = iconType)
        Spacer(modifier = Modifier.width(20.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = Color(0xFFA0A2A8),
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        val context = LocalContext.current
        val themeRevision by AnimeBoxThemeHelper.themeRevisionFlow.collectAsState()
        val primaryColor = remember(themeRevision) { AnimeBoxSettings.getAppThemeColor(context) }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = primaryColor,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color(0xFF938F99),
                uncheckedTrackColor = Color(0xFF2B2930),
                uncheckedBorderColor = Color(0xFF79747E)
            )
        )
    }
}

@Composable
fun PixelSettingsSelectorRow(
    iconType: SettingsIconType,
    title: String,
    subtitle: String? = null,
    currentValue: String,
    options: List<Pair<String, String>>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PixelSettingsIcon(type = iconType)
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        color = Color(0xFFA0A2A8),
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1F22))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentValue.substringBefore(" ("),
                        color = Color(0xFFE2E2E6),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = Color(0xFF8E918F),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 44.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { (label, key) ->
                    val isSelected = currentValue.startsWith(label.substringBefore(" (")) || currentValue == label

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF24262A) else Color.Transparent)
                            .clickable {
                                onOptionSelected(key)
                                expanded = false
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFF8E918F),
                            fontSize = 13.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PixelSettingsActionRow(
    iconType: SettingsIconType? = null,
    iconRes: Int? = null,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(5.dp))
            )
        } else if (iconType != null) {
            PixelSettingsIcon(type = iconType)
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = Color(0xFFA0A2A8),
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PixelSettingsCustomActionRow(
    iconType: SettingsIconType,
    title: String,
    subtitle: String,
    badgeTrailing: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PixelSettingsIcon(type = iconType)
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = Color(0xFFA0A2A8),
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        badgeTrailing()
    }
}

// ----------------------------------------------------------------------------
// App Theme Picker Dialog Modal
// ----------------------------------------------------------------------------

@Composable
fun AppThemePickerDialog(
    currentTheme: String,
    onDismiss: () -> Unit,
    onThemeSelected: (String) -> Unit
) {
    val themePresets = listOf(
        Triple("Lavender Purple (Default)", "lavender", Color(0xFFD0BCFF)),
        Triple("Light Red", "light_red", Color(0xFFFF5252)),
        Triple("Cyan Blue", "cyan", Color(0xFF00E5FF)),
        Triple("Emerald Green", "emerald", Color(0xFF00E676)),
        Triple("Golden Amber", "amber", Color(0xFFFFAB00))
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1F22))
                .border(1.dp, Color(0xFF2A2B30), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Choose App Theme",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Select the primary accent color for highlights and navigation.",
                    color = Color(0xFF8E918F),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                themePresets.forEach { (name, key, color) ->
                    val isSelected = (currentTheme == key) || (key == "lavender" && (currentTheme.isBlank() || currentTheme == "default"))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF2A2B30) else Color.Transparent)
                            .clickable { onThemeSelected(key) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = name,
                            color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                            fontSize = 14.5.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF8E918F), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Custom Timeline Color Picker Dialog Modal
// ----------------------------------------------------------------------------

@Composable
fun CustomColorPickerDialog(
    context: Context,
    initialColorHex: String,
    currentTheme: String,
    onDismiss: () -> Unit,
    onSettingsChanged: () -> Unit
) {
    val isInitiallyYt = remember { AnimeBoxSettings.isYouTubeStylePlayer(context) }
    var selectedPlayerTab by remember { mutableStateOf(if (isInitiallyYt) "youtube" else "default") }

    var defaultTheme by remember { mutableStateOf(AnimeBoxSettings.getPlayerTimelineTheme(context)) }
    var defaultHexText by remember { mutableStateOf(AnimeBoxSettings.getCustomTimelineColor(context)) }

    var ytTheme by remember { mutableStateOf(AnimeBoxSettings.getYouTubePlayerTimelineTheme(context)) }
    var ytHexText by remember { mutableStateOf(AnimeBoxSettings.getYouTubePlayerCustomTimelineColor(context)) }

    val defaultPresets = listOf(
        Triple("Lavender (Default)", "lavender", Color(0xFFD0BCFF)),
        Triple("Netflix Red", "red", Color(0xFFE50914)),
        Triple("Neon Cyan", "cyan", Color(0xFF00E5FF)),
        Triple("Sunset Gold", "gold", Color(0xFFFFD700)),
        Triple("Emerald Green", "green", Color(0xFF00E676)),
        Triple("Pure White", "white", Color.White)
    )

    val ytPresets = listOf(
        Triple("Crimson Red (Default)", "red", Color(0xFFE50914)),
        Triple("Neon Cyan", "cyan", Color(0xFF00E5FF)),
        Triple("Sunset Gold", "gold", Color(0xFFFFD700)),
        Triple("Emerald Green", "green", Color(0xFF00E676)),
        Triple("Pure White", "white", Color.White),
        Triple("Lavender", "lavender", Color(0xFFD0BCFF))
    )

    val activeTheme = if (selectedPlayerTab == "youtube") ytTheme else defaultTheme
    val activeHex = if (selectedPlayerTab == "youtube") ytHexText else defaultHexText
    val presetThemes = if (selectedPlayerTab == "youtube") ytPresets else defaultPresets

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1F22))
                .border(1.dp, Color(0xFF2A2B30), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Player Timeline Color",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Configure seekbar colors separately for Default and YouTube Style players.",
                    color = Color(0xFF8E918F),
                    fontSize = 12.5.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Player Tab Selector (Default Player vs YouTube Style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF141416))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedPlayerTab == "default") Color(0xFF2A2B30) else Color.Transparent)
                            .clickable { selectedPlayerTab = "default" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AnimeBoxSettings.getDefaultPlayerTimelineColor(context))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Default Player",
                                color = if (selectedPlayerTab == "default") Color.White else Color(0xFF8E918F),
                                fontSize = 12.5.sp,
                                fontWeight = if (selectedPlayerTab == "default") FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedPlayerTab == "youtube") Color(0xFF2A2B30) else Color.Transparent)
                            .clickable { selectedPlayerTab = "youtube" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AnimeBoxSettings.getYouTubePlayerTimelineColor(context))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "YouTube Style",
                                color = if (selectedPlayerTab == "youtube") Color.White else Color(0xFF8E918F),
                                fontSize = 12.5.sp,
                                fontWeight = if (selectedPlayerTab == "youtube") FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Presets Grid
                presetThemes.chunked(2).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPresets.forEach { (name, key, color) ->
                            val isSelected = activeTheme == key
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF2A2B30) else Color(0xFF141414))
                                    .clickable {
                                        if (selectedPlayerTab == "youtube") {
                                            ytTheme = key
                                            AnimeBoxSettings.setYouTubePlayerTimelineTheme(context, key)
                                        } else {
                                            defaultTheme = key
                                            AnimeBoxSettings.setPlayerTimelineTheme(context, key)
                                        }
                                        onSettingsChanged()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = name.substringBefore(" ("),
                                    color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF2A2B30))
                Spacer(modifier = Modifier.height(14.dp))

                // Custom HEX input
                Text(
                    text = "Custom HEX Color (${if (selectedPlayerTab == "youtube") "YouTube Style" else "Default Player"})",
                    color = Color(0xFFCCCCCC),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val previewColor = try {
                        Color(android.graphics.Color.parseColor(if (activeHex.startsWith("#")) activeHex else "#$activeHex"))
                    } catch (_: Exception) { Color.White }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .border(1.dp, Color(0xFF444444), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = activeHex,
                        onValueChange = {
                            if (selectedPlayerTab == "youtube") {
                                ytHexText = it
                            } else {
                                defaultHexText = it
                            }
                        },
                        label = { Text("HEX Code", color = Color(0xFF8E918F), fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color(0xFF333333)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = Color(0xFF8E918F))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val validHex = if (!activeHex.startsWith("#")) "#$activeHex" else activeHex
                            if (selectedPlayerTab == "youtube") {
                                ytHexText = validHex
                                ytTheme = "custom"
                                AnimeBoxSettings.setYouTubePlayerCustomTimelineColor(context, validHex)
                                AnimeBoxSettings.setYouTubePlayerTimelineTheme(context, "custom")
                            } else {
                                defaultHexText = validHex
                                defaultTheme = "custom"
                                AnimeBoxSettings.setCustomTimelineColor(context, validHex)
                                AnimeBoxSettings.setPlayerTimelineTheme(context, "custom")
                            }
                            onSettingsChanged()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Text("Apply HEX", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
