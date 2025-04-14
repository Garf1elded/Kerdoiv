package com.w52ptg.kerdoiv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.core.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AuthWrapper {
                    QuestionnaireApp()
                }
            }
        }
    }
}

@Composable
fun AuthWrapper(content: @Composable () -> Unit) {
    var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }

    if (user == null) {
        AuthScreen(
            onLogin = { email, password, onResult ->
                FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        user = FirebaseAuth.getInstance().currentUser
                        onResult(null)
                    }
                    .addOnFailureListener {
                        onResult(it.localizedMessage ?: "Ismeretlen hiba történt bejelentkezéskor.")
                    }
            },
            onRegister = { email, password, onResult ->
                FirebaseAuth.getInstance()
                    .createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        user = FirebaseAuth.getInstance().currentUser
                        onResult(null)
                    }
                    .addOnFailureListener {
                        onResult(it.localizedMessage ?: "Ismeretlen hiba történt regisztrációkor.")
                    }
            }
        )
    } else {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = {
                    FirebaseAuth.getInstance().signOut()
                    user = null
                }) {
                    Text("Kijelentkezés")
                }
            }
            content()
        }
    }
}

@Composable
fun AuthScreen(
    onLogin: (String, String, (String?) -> Unit) -> Unit,
    onRegister: (String, String, (String?) -> Unit) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Bejelentkezés / Regisztráció", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Jelszó") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                val description = if (passwordVisible) "Jelszó elrejtése" else "Jelszó megjelenítése"

                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
        )

        errorMessage?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            onLogin(email, password) { error -> errorMessage = error }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Bejelentkezés")
        }

        Button(onClick = {
            onRegister(email, password) { error -> errorMessage = error }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Regisztráció")
        }
    }
}



@Composable
fun QuestionnaireApp(viewModel: QuestionnaireViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (uiState.submitted) {
            SubmissionScreen(
                answers = uiState.answers,
                onRestart = viewModel::restart
            )
        } else {
            QuestionnaireScreen(
                questions = uiState.questions,
                answers = uiState.answers,
                onAnswer = viewModel::updateAnswer,
                onSubmit = viewModel::submit
            )
        }
    }
}

@Composable
fun QuestionnaireScreen(
    questions: List<Question>,
    answers: Map<Int, String>,
    onAnswer: (Int, String) -> Unit,
    onSubmit: () -> Unit
) {
    Column {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(questions) { question ->
                Text(text = question.text, style = MaterialTheme.typography.titleMedium)
                when (question.type) {
                    QuestionType.TEXT -> {
                        OutlinedTextField(
                            value = answers[question.id] ?: "",
                            onValueChange = { input ->
                                if (question.expectNumeric.not() || input.all { it.isDigit() }) {
                                    onAnswer(question.id, input)
                                }
                            },
                            keyboardOptions = if (question.expectNumeric)
                                KeyboardOptions(keyboardType = KeyboardType.Number)
                            else
                                KeyboardOptions.Default,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                    QuestionType.SINGLE_CHOICE -> {
                        question.options?.forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = answers[question.id] == option,
                                    onClick = { onAnswer(question.id, option) }
                                )
                                Text(option)
                            }
                        }
                    }
                    QuestionType.MULTI_CHOICE -> {
                        question.options?.forEach { option ->
                            val selectedOptions = answers[question.id]?.split(", ")?.toMutableSet() ?: mutableSetOf()
                            val isSelected = option in selectedOptions
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedOptions.add(option) else selectedOptions.remove(option)
                                        onAnswer(question.id, selectedOptions.joinToString(", "))
                                    }
                                )
                                Text(option)
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = onSubmit,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 16.dp)
        ) {
            Text("Beküldés")
        }
    }
}

@Composable
fun SubmissionScreen(answers: Map<Int, String>, onRestart: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val alphaAnim by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing), label = "fade"
    )
    val scaleAnim by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = tween(durationMillis = 800, easing = EaseOutBounce), label = "scale"
    )

    LaunchedEffect(Unit) {
        visible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "✅ Beküldés sikeres!",
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF2E7D32),
            modifier = Modifier
                .alpha(alphaAnim)
                .scale(scaleAnim)
                .padding(bottom = 24.dp)
        )

        answers.forEach { (id, answer) ->
            Text("${id + 1}. válasz: $answer")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onRestart) {
            Text("Kitöltés újra")
        }
    }
}

enum class QuestionType { TEXT, SINGLE_CHOICE, MULTI_CHOICE }

data class Question(
    val id: Int,
    val text: String,
    val type: QuestionType,
    val options: List<String>? = null,
    val expectNumeric: Boolean = false
)

data class QuestionnaireUiState(
    val questions: List<Question> = listOf(
        Question(0, "Mi a neved?", QuestionType.TEXT),
        Question(1, "Hány éves vagy?", QuestionType.TEXT, expectNumeric = true),
        Question(2, "Mi a kedvenc színed?", QuestionType.TEXT),
        Question(3, "Melyik állatot szereted?", QuestionType.SINGLE_CHOICE, listOf("Kutya", "Macska", "Nyúl")),
        Question(4, "Melyik évszakokat kedveled?", QuestionType.MULTI_CHOICE, listOf("Tavasz", "Nyár", "Ősz", "Tél")),
        Question(5, "Mi a kedvenc éttermed?", QuestionType.TEXT),
        Question(6, "Melyik sportot űzöd?", QuestionType.SINGLE_CHOICE, listOf("Futás", "Úszás", "Kerékpározás")),
        Question(7, "Hobbijaid?", QuestionType.MULTI_CHOICE, listOf("Olvasás", "Zenehallgatás", "Kirándulás", "Főzés")),
        Question(8, "Kedvenc tantárgyad?", QuestionType.TEXT),
        Question(9, "Milyen zenét szeretsz?", QuestionType.MULTI_CHOICE, listOf("Pop", "Rock", "Klasszikus", "Jazz"))
    ),
    val answers: Map<Int, String> = emptyMap(),
    val submitted: Boolean = false
)

class QuestionnaireViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(QuestionnaireUiState())
    val uiState: StateFlow<QuestionnaireUiState> = _uiState

    fun updateAnswer(id: Int, answer: String) {
        _uiState.update { state ->
            state.copy(answers = state.answers + (id to answer))
        }
    }

    fun submit() {
        _uiState.update { state ->
            state.copy(submitted = true)
        }
    }

    fun restart() {
        _uiState.value = QuestionnaireUiState()
    }
}
