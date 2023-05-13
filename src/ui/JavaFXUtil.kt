package ui

import javafx.beans.value.ObservableValue
import javafx.scene.control.Slider
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory
import kotlin.math.roundToInt


object JavaFXUtil
{
    fun sliderSpinnerLink(
        slider: Slider,
        spinner: Spinner<Double>,
        initMin: Double,
        initMax: Double,
        min: Double,
        max: Double,
        initValue: Double,
        step: Double,
        integerSpinner: Boolean
    ) {
        // Set min max values
        slider.min = initMin
        slider.max = initMax
        spinner.valueFactory = DoubleSpinnerValueFactory(min, max, initValue, step)
        slider.value = initValue

        // Sync spinner with slider
        slider.valueProperty().addListener { v: ObservableValue<out Number>?, _: Number?, newVal: Number ->
            spinner.valueFactory.setValue((if (integerSpinner) newVal.toDouble().roundToInt() else newVal.toDouble()).toDouble())
        }

        // Sync slider with spinner
        spinner.valueProperty().addListener { v: ObservableValue<out Double>?, _: Double?, newVal: Double? ->
            val f = spinner.valueFactory as DoubleSpinnerValueFactory
            if (spinner.value > slider.max)
            {
                slider.max = (slider.max + (slider.max - slider.min)).coerceAtMost(max)
            }
            else if (spinner.value < slider.min)
            {
                slider.min = (slider.min - (slider.max - slider.min)).coerceAtLeast(min)
            }
            if (!slider.isPressed)
            {
                slider.value = newVal!!
            }
        }
    }
}