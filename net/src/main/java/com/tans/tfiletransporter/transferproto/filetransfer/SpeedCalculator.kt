package com.tans.tfiletransporter.transferproto.filetransfer

import com.tans.tfiletransporter.toSizeString
import com.tans.tfiletransporter.transferproto.SimpleObservable
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class SpeedCalculator : SimpleObservable<SpeedCalculator.Companion.SpeedObserver> {

    private val lastCycleSize: AtomicLong by lazy {
        AtomicLong(0L)
    }

    private val currentSize: AtomicLong by lazy {
        AtomicLong(0L)
    }

    override val observers: LinkedBlockingDeque<SpeedObserver> by lazy {
        LinkedBlockingDeque()
    }

    private val taskFuture: AtomicReference<ScheduledFuture<*>> by lazy {
        AtomicReference()
    }
    private val isUpdating: AtomicBoolean by lazy {
        AtomicBoolean(false)
    }

    fun start() {
        taskScheduleExecutor.execute {
            taskFuture.get()?.cancel(true)
            lastCycleSize.set(0L)
            currentSize.set(0L)
            isUpdating.set(false)
            val future = taskScheduleExecutor.scheduleAtFixedRate({
                val lastCycleSize = lastCycleSize.get()
                val currentSize = currentSize.get()
                this.lastCycleSize.set(currentSize)
                val speed = ((currentSize - lastCycleSize).coerceAtLeast(0L).toDouble() /
                        (CALCULATE_INTERVAL.toDouble() / 1000f )).toLong()
                val speedString = "${speed.toSizeString()}/s"
                for (o in observers) {
                    o.onSpeedUpdated(speed, speedString)
                }
            }, CALCULATE_INTERVAL, CALCULATE_INTERVAL, TimeUnit.MILLISECONDS)
            taskFuture.set(future)
        }
    }

    fun updateCurrentSize(size: Long) {
        if (isUpdating.compareAndSet(false, true)) {
            taskScheduleExecutor.execute {
                currentSize.set(size)
                isUpdating.set(false)
            }
        }
    }

    fun reset() {
        taskScheduleExecutor.execute {
            lastCycleSize.set(0L)
            currentSize.set(0L)
            isUpdating.set(false)
        }
    }

    fun stop() {
        taskScheduleExecutor.execute{
            lastCycleSize.set(0L)
            currentSize.set(0L)
            isUpdating.set(false)
            taskFuture.get()?.cancel(true)
            taskFuture.set(null)
        }
    }


    companion object {

        private const val CALCULATE_INTERVAL = 400L

        interface SpeedObserver {
            fun onSpeedUpdated(speedInBytes: Long, speedInString: String)
        }

        private val taskScheduleExecutor: ScheduledExecutorService by lazy {
            Executors.newScheduledThreadPool(1) {
                Thread(it, "SpeedCalculatorThread")
            }
        }
    }

}