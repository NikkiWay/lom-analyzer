package com.example.lomanalyzer.security

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow

@Composable
@Suppress("FunctionNaming")
fun MasterPasswordDialog(
    isNewVault: Boolean,
    onPasswordSubmit: (CharArray) -> Boolean,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DialogWindow(
        onCloseRequest = onDismiss,
        title = if (isNewVault) "Create Master Password" else "Enter Master Password",
        state = DialogState(width = 400.dp, height = if (isNewVault) 320.dp else 250.dp),
        resizable = false,
    ) {
        MaterialTheme {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PasswordPromptText(isNewVault)
                PasswordField(password) { password = it; errorMessage = null }
                if (isNewVault) {
                    ConfirmPasswordField(confirmPassword) { confirmPassword = it; errorMessage = null }
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colors.error) }
                SubmitButton(isNewVault, password, confirmPassword, onPasswordSubmit) { errorMessage = it }
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun PasswordPromptText(isNewVault: Boolean) {
    Text(
        if (isNewVault) "Create a master password to protect your VK token."
        else "Enter your master password to unlock the token vault."
    )
}

@Composable
@Suppress("FunctionNaming")
private fun PasswordField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Password") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
@Suppress("FunctionNaming")
private fun ConfirmPasswordField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Confirm Password") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
@Suppress("FunctionNaming")
private fun SubmitButton(
    isNewVault: Boolean,
    password: String,
    confirmPassword: String,
    onPasswordSubmit: (CharArray) -> Boolean,
    onError: (String) -> Unit,
) {
    Button(
        onClick = {
            when {
                password.isBlank() -> onError("Password cannot be empty")
                isNewVault && password != confirmPassword -> onError("Passwords do not match")
                !onPasswordSubmit(password.toCharArray()) -> onError("Wrong password — decryption failed")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (isNewVault) "Create" else "Unlock")
    }
}
