package com.example.emo

import android.util.Log
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.measureTimeMillis

/***
 * https://www.cnblogs.com/joy99/p/15805916.html
 */
object CoroutineSample {
    private val TAG = "CoroutineSample"

    /***
     * 会在直接执行，不会block；
     */
    fun helloCoroutine() = runBlocking {
        launch {
            delay(100)
            Log.i(TAG, "hello")
            delay(300)
            Log.i(TAG, "world")
        }
        Log.i(TAG, ("test1"))
        Log.i(TAG, ("test2"))
    }

    /***
     * 通过协程join阻断当前进程，必须等协程完成之后才可以进行；
     */
    fun joinCoroutine() = runBlocking {
        val job = launch {
            delay(100)
            println("hello")
            delay(300)
            println("world")
        }
        Log.i(TAG, "test1")
        job.join()
        Log.i(TAG, "test2")
    }

    /***
     * 协程提供了一个 cancel() 方法来取消作业。
     * cancel之后可以Join对于协程进行同步，等待作业执行结束
     * job.cancelAndJoin() // 替代上面的 cancel & join
     */
    fun cancelCoroutine() = runBlocking {
        val job = launch {
            repeat(1000) { i ->
                Log.i(TAG, "job: test $i ...")
                delay(500L)
            }
        }
        delay(1300L) // 延迟一段时间
        Log.i(TAG, "main: ready to cancel!")
        job.cancel() // 取消该作业
        job.join() // 等待作业执行结束
//        job.cancelAndJoin() // 替代上面的 cancel & join
        Log.i(TAG, "main: Now cancel.")
    }


    /***
     * 协程并不是一定能取消，协程的取消是协作的。一段协程代码必须协作才能被取消。
     * 如果协程正在执行计算任务，并且没有检查取消的话，那么它是不能被取消的。
     * 只有在进行while判断的时候才有机会中断，否则一直执行；
     * 可见协程并没有被取消。为了能真正停止协程工作，我们需要定期检查协程是否处于 active 状态
     * 可以增加ensureActive()进行判断 yield()
     * 每一秒判断2次
     * //job: hello 0 ...
     * //job: hello 1 ...
     * //job: hello 2 ...
     * //main: ready to cancel!
     * //job: hello 3 ...
     * //job: hello 4 ...
     * //main: Now cancel.
     */
    fun cancelCoroutineFailed() = runBlocking {
        val startTime = System.currentTimeMillis()
        val job = launch(Dispatchers.Default) {
            var nextPrintTime = startTime
            var i = 0
            delay(1500L)
            while (i < 5) { // 一个执行计算的循环，只是为了占用 CPU
                Log.i(TAG, nextPrintTime.toString())
                ensureActive()
//                yield()
                // 每秒打印消息两次，只有在 if判断过程中才可以中断；
                if (System.currentTimeMillis() >= nextPrintTime) {
                    Log.i(TAG,"job: hello ${i++} ...")
                    nextPrintTime += 500L
                }
            }
        }
        delay(500) // 等待一段时间
        Log.i(TAG,"main: ready to cancel!")
        job.cancelAndJoin() // 取消一个作业并且等待它结束
        Log.i(TAG,"main: Now cancel.")
    }


    /***
     * 取消协程会抛出CancellationException；
     * 但是如果不进行try/catch app也不会Crash
     * 如果执行过程中会抛出Exception，那需要针对CancellationException进行特殊处理，无需进行过程处理；
     */
    fun cancelExceptionCoroutine() = runBlocking {
        val job = launch {
            try {
                delay(100)
                Log.i(TAG,"try...")
            }catch(e: CancellationException){
                Log.i(TAG,"CancellationException: ${e.message}")
            }
            catch (e: Exception) {
                Log.i(TAG,"exception: ${e.message}")
            } finally {
                Log.i(TAG,"finally...")
            }
        }
        delay(50)
        Log.i(TAG,"cancel")
        job.cancelAndJoin()
        Log.i(TAG,"Done")
    }


    /***
     * 协程执行超时；
     */
    fun timeoutCoroutine() = runBlocking {
        try{
            withTimeout(300) {
                Log.i(TAG,"start...")
                delay(100)
                Log.i(TAG,"progress 1...")
                delay(100)
                Log.i(TAG,"progress 2...")
                delay(100)
                Log.i(TAG,"progress 3...")
                delay(100)
                Log.i(TAG,"progress 4...")
                delay(100)
                Log.i(TAG,"progress 5...")
                Log.i(TAG,"end")
            }
        }catch (ex: TimeoutCancellationException){
            Log.i(TAG, "timeoutException: ${ex.message}")
        }catch (_: Exception){}
        finally {
            Log.i(TAG,"finally")
        }
    }

    private fun printWithThreadInfo(){
        Log.i(TAG, "currentThread: ${Thread.currentThread().name}")
    }

    private fun printWithThreadInfo(msg: String){
        Log.i(TAG, "${msg}, currentThread: ${Thread.currentThread().name}")
    }

    /***
     * 协程返回值方法调用，惰性启动 async
     * async 启动一个协程后，调用 await 方法后，会阻塞，等待结果的返回
     */
    fun asyncCoroutine() = runBlocking {
        val time = measureTimeMillis {
            val a = async(Dispatchers.IO) {
                printWithThreadInfo()
                delay(1000) // 模拟耗时操作
                1
            }
            val b = async(Dispatchers.IO) {
                printWithThreadInfo()
                delay(2000) // 模拟耗时操作
                2
            }
            printWithThreadInfo("${a.await() + b.await()}")
            printWithThreadInfo("end")
        }
        printWithThreadInfo("time: $time")
    }

    /***
     * 惰性启动 async
     * 使用惰性加载，需要手动调用start，调用过程不会柱塞
     * start和await不同地方是，start不柱塞，await会柱塞并且有返回值；
     * 注意：如果通过start和await调用两次，协程不会执行两次，第一次start执行之后，跌二次调用await直接返回结果；
     */
    fun lazyAsyncCoroutine() = runBlocking {
        val time = measureTimeMillis {
            val a = async(Dispatchers.IO, CoroutineStart.LAZY) {
                printWithThreadInfo("work.1 start")
                delay(1000) // 模拟耗时操作
                printWithThreadInfo("work.1 end")
                1
            }
            val b = async(Dispatchers.IO, CoroutineStart.LAZY) {
                printWithThreadInfo("work.2 start")
                delay(2000) // 模拟耗时操作
                printWithThreadInfo("work.2 end")
                2
            }
            printWithThreadInfo("main.start1")
            a.start()
            b.start()
            delay(400)
            printWithThreadInfo("main.start2")
            printWithThreadInfo("${a.await() + b.await()}")
            printWithThreadInfo("end")
        }
        printWithThreadInfo("time: $time")
    }

}
