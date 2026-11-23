package com.example

import com.example.data.model.AutomatonNode
import com.example.data.model.MonetizationModel
import com.example.data.model.SurvivalTier
import com.example.data.model.WithdrawalStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun automatonNetUsdc_isClampedAtZero() {
        val thriving = AutomatonNode(
            name = "Alpha",
            earnedUsdc = 65.0,
            computeBurnUsdc = 12.5
        )
        assertEquals(52.5, thriving.netUsdc, 0.001)

        val underwater = AutomatonNode(
            name = "Burned",
            earnedUsdc = 4.0,
            computeBurnUsdc = 10.0
        )
        assertEquals(0.0, underwater.netUsdc, 0.001)
    }

    @Test
    fun enumDeserialization_fallsBackSafely() {
        assertEquals(MonetizationModel.X402_API, MonetizationModel.fromRaw("UNKNOWN_VALUE"))
        assertEquals(SurvivalTier.NORMAL, SurvivalTier.fromRaw("UNKNOWN_TIER"))
        assertEquals(WithdrawalStatus.REQUESTED, WithdrawalStatus.fromRaw("UNKNOWN_STATUS"))
    }
}
