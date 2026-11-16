package com.example.tesltls.ui.swipe

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.tesltls.R
import com.example.tesltls.domain.model.Dish
import com.example.tesltls.domain.repository.FoodRepository
import com.example.tesltls.ui.FoodCategory
import com.example.tesltls.ui.common.toUserMessage
import com.example.tesltls.ui.components.AppIcon
import com.example.tesltls.ui.components.BackButton
import com.example.tesltls.ui.components.DishImage
import com.example.tesltls.ui.components.LoadingBox
import com.example.tesltls.ui.components.MessageBox
import com.example.tesltls.ui.theme.FoodColors
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

// ───────────────────────── Стан і події ─────────────────────────

sealed interface SwipeUiState {
    data object Loading : SwipeUiState
    data object Empty : SwipeUiState                       // на сьогодні страви закінчились
    data class Error(val message: String) : SwipeUiState

    /** [token] унікальний для кожної картки — за ним скидається позиція свайпу. */
    data class Content(val dish: Dish, val token: Long) : SwipeUiState
}

sealed interface SwipeEvent {
    data class OpenDish(val dishId: String) : SwipeEvent   // свайп вправо -> меню дій
    data class ShowMessage(val text: String) : SwipeEvent
}

// ───────────────────────── ViewModel ─────────────────────────

class SwipeViewModel(
    private val repository: FoodRepository,
    private val userId: suspend () -> String,
    private val category: String
) : ViewModel() {

    private val _state = MutableStateFlow<SwipeUiState>(SwipeUiState.Loading)
    val state: StateFlow<SwipeUiState> = _state.asStateFlow()

    private val _nextDish = MutableStateFlow<Dish?>(null)
    val nextDish: StateFlow<Dish?> = _nextDish.asStateFlow()

    private val _events = Channel<SwipeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var cardCounter = 0L

    // ID страв, які вже були показані або знаходяться в черзі попереднього завантаження
    private val sessionDishIds = mutableSetOf<String>()

    // Черга попередньо завантажених страв для миттєвої подачі без очікування
    private val prefetchQueue = ArrayDeque<Dish>()

    private var isEndReached = false
    private var prefetchJob: Job? = null

    init {
        reload()
    }

    /** Завантажити наступну страву (також для кнопки "Спробувати ще раз"). */
    fun reload() {
        prefetchJob?.cancel()
        prefetchQueue.clear()
        _nextDish.value = null
        isEndReached = false
        _state.value = SwipeUiState.Loading
        fillPrefetchQueue()
    }

    /**
     * Свайп: вліво (like = false) -> страва пропускається;
     * вправо (like = true) -> відкриваємо меню дій. В обох випадках асинхронно
     * зберігаємо свайп і МИТТЄВО показуємо наступну заздалегідь завантажену страву.
     */
    fun onSwiped(dish: Dish, isLike: Boolean) {
        viewModelScope.launch {
            if (isLike) _events.send(SwipeEvent.OpenDish(dish.id))
            try {
                repository.insertSwipe(userId(), dish.id, isLike)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(SwipeEvent.ShowMessage(e.toUserMessage()))
            }
        }

        // Миттєвий перехід до наступної страви без блокування інтерфейсу
        if (prefetchQueue.isNotEmpty()) {
            val next = prefetchQueue.removeFirst()
            _nextDish.value = prefetchQueue.firstOrNull()
            _state.value = SwipeUiState.Content(next, ++cardCounter)
            fillPrefetchQueue()
        } else if (isEndReached) {
            _nextDish.value = null
            _state.value = SwipeUiState.Empty
        } else {
            // Якщо користувач свайпнув швидше, ніж мережа встигла підвантажити страву
            _nextDish.value = null
            _state.value = SwipeUiState.Loading
            fillPrefetchQueue()
        }
    }

    private fun fillPrefetchQueue() {
        if (isEndReached) return
        if (prefetchJob?.isActive == true) return

        prefetchJob = viewModelScope.launch {
            // Тримаємо до 2 наперед завантажених страв у буфері для безшовної роботи
            while (prefetchQueue.size < 2 && !isEndReached) {
                try {
                    val dish = repository.getRandomDish(category, userId(), sessionDishIds)
                    if (dish == null) {
                        isEndReached = true
                        if (_state.value is SwipeUiState.Loading && prefetchQueue.isEmpty()) {
                            _state.value = SwipeUiState.Empty
                        }
                        break
                    } else {
                        sessionDishIds.add(dish.id)
                        prefetchQueue.add(dish)
                        _nextDish.value = prefetchQueue.firstOrNull()

                        // Якщо користувач чекає на завантаження — показуємо страву негайно
                        if (_state.value is SwipeUiState.Loading) {
                            val next = prefetchQueue.removeFirst()
                            _nextDish.value = prefetchQueue.firstOrNull()
                            _state.value = SwipeUiState.Content(next, ++cardCounter)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (_state.value is SwipeUiState.Loading && prefetchQueue.isEmpty()) {
                        _state.value = SwipeUiState.Error(e.toUserMessage())
                    }
                    break
                }
            }
        }
    }
}

// ───────────────────────── Екран ─────────────────────────

@Composable
fun SwipeScreen(
    viewModel: SwipeViewModel,
    category: FoodCategory?,
    onBack: () -> Unit,
    onOpenDish: (String) -> Unit
) {
    val strings = com.example.tesltls.ui.strings.LocalAppStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nextDish by viewModel.nextDish.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentOnOpenDish by rememberUpdatedState(onOpenDish)

    // Невидиме попереднє завантаження картинки наступної страви в кеш Coil
    if (nextDish?.imageUrl != null) {
        Box(Modifier.size(0.dp).alpha(0f)) {
            DishImage(
                url = nextDish?.imageUrl,
                category = nextDish?.category,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SwipeEvent.OpenDish -> currentOnOpenDish(event.dishId)
                is SwipeEvent.ShowMessage ->
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onBack)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = category?.let { strings.categoryTitle(it) } ?: "—",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoodColors.TextPrimary
                )
                Text(
                    text = strings.swipeHint,
                    fontSize = 12.sp,
                    color = FoodColors.TextSecondary
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                SwipeUiState.Loading -> LoadingBox()
                SwipeUiState.Empty -> MessageBox(
                    message = strings.swipeEmptyMessage,
                    actionLabel = strings.toMenu,
                    onAction = onBack
                )
                is SwipeUiState.Error -> MessageBox(
                    message = s.message,
                    actionLabel = strings.tryAgain,
                    onAction = viewModel::reload
                )
                is SwipeUiState.Content -> SwipeDeck(
                    content = s,
                    category = category,
                    onSwiped = viewModel::onSwiped
                )
            }
        }
    }
}

// ───────────────────────── Картка зі свайпом ─────────────────────────

@Composable
private fun SwipeDeck(
    content: SwipeUiState.Content,
    category: FoodCategory?,
    onSwiped: (Dish, Boolean) -> Unit
) {
    val dish = content.dish
    val scope = rememberCoroutineScope()
    val currentOnSwiped by rememberUpdatedState(onSwiped)

    // Усе нижче "прив'язане" до token -> нова картка завжди стартує з нульової позиції
    val offsetX = remember(content.token) { Animatable(0f) }
    var handled by remember(content.token) { mutableStateOf(false) }

    val screenWidthPx = LocalWindowInfo.current.containerSize.width.toFloat().coerceAtLeast(1f)
    val threshold = screenWidthPx * 0.28f // відстань, після якої свайп "зараховується"

    /** Анімація вильоту картки за екран і повідомлення ViewModel. Спрацьовує лише раз. */
    fun fling(like: Boolean) {
        if (handled) return
        handled = true
        scope.launch {
            val target = if (like) screenWidthPx * 1.4f else -screenWidthPx * 1.4f
            offsetX.animateTo(target, tween(250))
            currentOnSwiped(dish, like)
        }
    }

    val progress = (offsetX.value / threshold).coerceIn(-1f, 1f)

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer { rotationZ = offsetX.value / screenWidthPx * 15f }
                .pointerInput(content.token) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                offsetX.value > threshold -> fling(true)
                                offsetX.value < -threshold -> fling(false)
                                else -> scope.launch { offsetX.animateTo(0f, spring()) }
                            }
                        },
                        onDragCancel = { scope.launch { offsetX.animateTo(0f, spring()) } },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (!handled) scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                        }
                    )
                }
        ) {
            DishCard(dish = dish, category = category, progress = progress)
        }

        Spacer(Modifier.height(20.dp))

        val strings = com.example.tesltls.ui.strings.LocalAppStrings.current
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            RoundActionButton(
                icon = R.drawable.ic_close,
                container = FoodColors.DangerSoft,
                content = FoodColors.Danger,
                label = strings.skip,
                onClick = { fling(false) }
            )
            RoundActionButton(
                icon = R.drawable.ic_check,
                container = FoodColors.Green,
                content = Color.White,
                label = strings.choose,
                onClick = { fling(true) }
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun DishCard(dish: Dish, category: FoodCategory?, progress: Float) {
    val strings = com.example.tesltls.ui.strings.LocalAppStrings.current
    val catTitle = category?.let { strings.categoryTitle(it) } ?: dish.category
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background((category?.color ?: FoodColors.Pill).copy(alpha = 0.7f))
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(22.dp))
        ) {
            DishImage(
                url = dish.imageUrl,
                category = dish.category,
                modifier = Modifier.fillMaxSize()
            )
            // Підказки, що з'являються під час перетягування
            if (progress > 0.05f) {
                SwipeBadge(
                    icon = R.drawable.ic_check,
                    container = FoodColors.Green,
                    content = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).alpha(progress)
                )
            }
            if (progress < -0.05f) {
                SwipeBadge(
                    icon = R.drawable.ic_close,
                    container = FoodColors.Danger,
                    content = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).alpha(-progress)
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            Text(
                text = catTitle.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = FoodColors.GreenLabel
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = dish.name,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoodColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun SwipeBadge(
    @DrawableRes icon: Int,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(16.dp)
            .size(56.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(icon, tint = content, size = 28.dp)
    }
}

@Composable
private fun RoundActionButton(
    @DrawableRes icon: Int,
    container: Color,
    content: Color,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(icon, tint = content, size = 30.dp, contentDescription = label)
    }
}
