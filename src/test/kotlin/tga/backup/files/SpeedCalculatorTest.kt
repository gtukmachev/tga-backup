package tga.backup.files

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpeedCalculatorTest {

    @Test
    fun testSpeedCalculation() {
        val calc = SpeedCalculator(1000)
        
        calc.addProgress(0)
        Thread.sleep(100)
        calc.addProgress(100)
        Thread.sleep(100)
        calc.addProgress(200)
        
        val speed = calc.getSpeed()
        // 200 bytes in ~200ms -> ~1000 bytes/sec
        assertThat(speed).isBetween(800L, 1200L)
    }

    @Test
    fun testWindowing() {
        val calc = SpeedCalculator(200) // 200ms window
        
        calc.addProgress(0)
        Thread.sleep(100)
        calc.addProgress(100)
        Thread.sleep(150) // total 250ms, first point should be dropped
        calc.addProgress(200)
        
        // stats should only have (T+100, 100) and (T+250, 200)
        // duration = 150ms, bytes = 100
        // speed = 100 * 1000 / 150 = 666 bytes/sec
        
        val speed = calc.getSpeed()
        assertThat(speed).isBetween(500L, 800L)
    }
}
