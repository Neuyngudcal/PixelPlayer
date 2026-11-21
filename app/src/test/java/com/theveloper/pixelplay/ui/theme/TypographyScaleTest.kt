package com.theveloper.pixelplay.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class TypographyScaleTest {

    @Test
    fun `baseline 15 roles strictly follow Google Material 3 Expressive TypeScaleTokens`() {
        assertThat(Typography.displayLarge.fontSize).isEqualTo(57.sp)
        assertThat(Typography.displayLarge.lineHeight).isEqualTo(64.sp)
        assertThat(Typography.displayMedium.fontSize).isEqualTo(45.sp)
        assertThat(Typography.displayMedium.lineHeight).isEqualTo(52.sp)
        assertThat(Typography.displaySmall.fontSize).isEqualTo(36.sp)
        assertThat(Typography.displaySmall.lineHeight).isEqualTo(44.sp)

        assertThat(Typography.headlineLarge.fontSize).isEqualTo(32.sp)
        assertThat(Typography.headlineLarge.lineHeight).isEqualTo(40.sp)
        assertThat(Typography.headlineMedium.fontSize).isEqualTo(28.sp)
        assertThat(Typography.headlineMedium.lineHeight).isEqualTo(36.sp)
        assertThat(Typography.headlineSmall.fontSize).isEqualTo(24.sp)
        assertThat(Typography.headlineSmall.lineHeight).isEqualTo(32.sp)

        assertThat(Typography.titleLarge.fontSize).isEqualTo(22.sp)
        assertThat(Typography.titleLarge.lineHeight).isEqualTo(28.sp)
        assertThat(Typography.titleMedium.fontSize).isEqualTo(16.sp)
        assertThat(Typography.titleMedium.lineHeight).isEqualTo(24.sp)
        assertThat(Typography.titleSmall.fontSize).isEqualTo(14.sp)
        assertThat(Typography.titleSmall.lineHeight).isEqualTo(20.sp)

        assertThat(Typography.bodyLarge.fontSize).isEqualTo(16.sp)
        assertThat(Typography.bodyLarge.lineHeight).isEqualTo(24.sp)
        assertThat(Typography.bodyMedium.fontSize).isEqualTo(14.sp)
        assertThat(Typography.bodyMedium.lineHeight).isEqualTo(20.sp)
        assertThat(Typography.bodySmall.fontSize).isEqualTo(12.sp)
        assertThat(Typography.bodySmall.lineHeight).isEqualTo(16.sp)

        assertThat(Typography.labelLarge.fontSize).isEqualTo(14.sp)
        assertThat(Typography.labelLarge.lineHeight).isEqualTo(20.sp)
        assertThat(Typography.labelMedium.fontSize).isEqualTo(12.sp)
        assertThat(Typography.labelMedium.lineHeight).isEqualTo(16.sp)
        assertThat(Typography.labelSmall.fontSize).isEqualTo(11.sp)
        assertThat(Typography.labelSmall.lineHeight).isEqualTo(16.sp)
    }

    @Test
    fun `all 30 Material 3 Expressive roles have lineHeight strictly greater than fontSize and emphasized weights stronger than baseline`() {
        val pairs = listOf(
            Typography.displayLarge to Typography.displayLargeEmphasized,
            Typography.displayMedium to Typography.displayMediumEmphasized,
            Typography.displaySmall to Typography.displaySmallEmphasized,
            Typography.headlineLarge to Typography.headlineLargeEmphasized,
            Typography.headlineMedium to Typography.headlineMediumEmphasized,
            Typography.headlineSmall to Typography.headlineSmallEmphasized,
            Typography.titleLarge to Typography.titleLargeEmphasized,
            Typography.titleMedium to Typography.titleMediumEmphasized,
            Typography.titleSmall to Typography.titleSmallEmphasized,
            Typography.bodyLarge to Typography.bodyLargeEmphasized,
            Typography.bodyMedium to Typography.bodyMediumEmphasized,
            Typography.bodySmall to Typography.bodySmallEmphasized,
            Typography.labelLarge to Typography.labelLargeEmphasized,
            Typography.labelMedium to Typography.labelMediumEmphasized,
            Typography.labelSmall to Typography.labelSmallEmphasized,
        )

        pairs.forEach { (baseline, emphasized) ->
            assertThat(baseline.lineHeight.value).isGreaterThan(baseline.fontSize.value)
            assertThat(emphasized.lineHeight.value).isGreaterThan(emphasized.fontSize.value)
            assertThat(emphasized.fontSize).isEqualTo(baseline.fontSize)
            val baseWeight = (baseline.fontWeight ?: FontWeight.Normal).weight
            val emphWeight = (emphasized.fontWeight ?: FontWeight.Normal).weight
            assertThat(emphWeight).isGreaterThan(baseWeight)
            assertThat(baseline.fontFamily).isEqualTo(GoogleSansRounded)
            assertThat(emphasized.fontFamily).isEqualTo(GoogleSansRounded)
        }
    }
}
