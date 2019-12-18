package eu.thesimplecloud.clientserverapi.communicationpromise

import eu.thesimplecloud.clientserverapi.lib.packet.communicationpromise.CommunicationPromise
import eu.thesimplecloud.clientserverapi.lib.packet.communicationpromise.ICommunicationPromise
import eu.thesimplecloud.clientserverapi.lib.packet.communicationpromise.flatten
import org.junit.Test
import java.lang.Exception

class CommunicationPromiseTest {

    @Test
    fun test() {
        val promise1 = CommunicationPromise<Int>()
        val promise2 = CommunicationPromise<Int>()
        promise1.then { println("then: $it") }
        val promise = promise1.then {
            println("promise1: $it")
            promise2
        }
        promise.flatten().thenAccept { println("test: $it") }
        promise1.thenAcceptNonNull { println("testttt: $it") }
        promise1.trySuccess(111)
        promise2.trySuccess(3333)
        println(2222)
    }

    @Test
    fun test2() {
        println("2-test2")
        val promise1 = CommunicationPromise<Int>()
        //val promise2 = CommunicationPromise<Int>()
        promise1.thenNonNull { result -> println("2-test-${result}") }.then { println("2-test: " + it.toString()) }.addFailureListener { println("2-failed: ${it::class.java.simpleName}") }
        //promise1.then { println("2-success-test: $it") }.addFailureListener { println("2-failed: ${it::class.java.simpleName}") }
        promise1.tryFailure(KotlinNullPointerException())
        Thread.sleep(200)
    }

}