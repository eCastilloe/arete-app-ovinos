package com.arete.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arete.app.ui.theme.AreteTheme

val MinTouchHeight = 56.dp
private val ButtonShape = RoundedCornerShape(12.dp)

@Composable
fun AreteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchHeight)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AreteSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchHeight)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEEF0EA)
@Composable
private fun AreteButtonPreview() {
    AreteTheme {
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(16.dp)) {
            AreteButton(text = "Guardar 28.5 kg", onClick = {})
            AreteSecondaryButton(
                text = "Pesar",
                onClick = {},
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}