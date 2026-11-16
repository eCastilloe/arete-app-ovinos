package com.arete.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arete.app.ui.theme.AreteTheme
import com.arete.app.ui.theme.FemaleBackground
import com.arete.app.ui.theme.FemaleBorder
import com.arete.app.ui.theme.FemaleText
import com.arete.app.ui.theme.MaleBackground
import com.arete.app.ui.theme.MaleBorder
import com.arete.app.ui.theme.MaleText

enum class Sex { FEMALE, MALE }

private data class SexStyle(
    val background: Color,
    val border: Color,
    val content: Color,
    val symbol: String,
    val label: String
)

private fun styleFor(sex: Sex): SexStyle = when (sex) {
    Sex.FEMALE -> SexStyle(FemaleBackground, FemaleBorder, FemaleText, "♀", "Hembra")
    Sex.MALE -> SexStyle(MaleBackground, MaleBorder, MaleText, "♂", "Macho")
}

@Composable
fun SexChip(sex: Sex, modifier: Modifier = Modifier) {
    val style = styleFor(sex)
    val shape = RoundedCornerShape(50)

    Row(
        modifier = modifier
            .background(style.background, shape)
            .border(1.dp, style.border, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = style.symbol, color = style.content, style = MaterialTheme.typography.labelMedium)
        Text(text = style.label, color = style.content, style = MaterialTheme.typography.labelMedium)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEEF0EA)
@Composable
private fun SexChipPreview() {
    AreteTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SexChip(sex = Sex.FEMALE)
            SexChip(sex = Sex.MALE)
        }
    }
}