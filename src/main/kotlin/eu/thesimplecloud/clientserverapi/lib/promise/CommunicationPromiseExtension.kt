/*
 * MIT License
 *
 * Copyright (C) 2020 Frederick Baier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package eu.thesimplecloud.clientserverapi.lib.promise

import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture


fun ICommunicationPromise<Unit>.completeWhenAllCompleted(promises: List<ICommunicationPromise<*>>): ICommunicationPromise<Unit> {
    if (promises.isEmpty()) {
        trySuccess(Unit)
        return this
    }
    promises[0].combineAll(promises.drop(1)).addResultListener { this.trySuccess(Unit) }
    return this
}

fun Collection<ICommunicationPromise<*>>.combineAllPromises(): ICommunicationPromise<Unit> {
    return CommunicationPromise.combineAllToUnitPromise(this)
}

/**
 * Returns a new promise that will complete when the inner promise completes.
 * The new promise will complete with the same specifications.
 */
fun <T : Any> ICommunicationPromise<ICommunicationPromise<T>>.flatten(additionalTimeout: Long = 0, timeoutEnabled: Boolean = true): ICommunicationPromise<T> {
    val enableTimeout = this.isTimeoutEnabled() && timeoutEnabled
    val newPromise = CommunicationPromise<T>(this.getTimeout() + additionalTimeout, enableTimeout)
    this.addCompleteListener {
        if (it.isSuccess) {
            val innerPromise = it.get()
            newPromise.copyStateFromOtherPromise(innerPromise)
        } else {
            newPromise.tryFailure(it.cause())
        }
    }
    return newPromise
}

/**
 * Returns a [ICommunicationPromise] with a list. The returned promise will complete when all promises in this list are completed.
 */
fun <T : Any> List<ICommunicationPromise<T>>.toListPromise(): ICommunicationPromise<List<T?>> {
    return this.combineAllPromises().then { this.map { it.getNow() } }
}

/**
 * Returns a [ICommunicationPromise] with a list. The returned promise will complete when all promises in this list are completed.
 * Note: if the expected list is large it is recommended to increase the [additionalTimeout]. Otherwise the returned promise will just time out.
 */
fun <T : Any> ICommunicationPromise<List<ICommunicationPromise<T>>>.toListPromise(additionalTimeout: Long = 400): ICommunicationPromise<List<T?>> {
    return this.then { list -> list.toListPromise()  }.flatten(additionalTimeout)
}

/*
* Copies the information of the specified promise to this promise when the specified promise completes.
*/
fun <T: Any> ICommunicationPromise<Unit>.copyStateFromOtherPromiseToUnitPromise(otherPromise: ICommunicationPromise<T>) {
    otherPromise.addCompleteListener {
        if (it.isSuccess) {
            this.trySuccess(Unit)
        } else {
            this.tryFailure(it.cause())
        }
    }
}

fun <T : Any> ICommunicationPromise<T>.createBlockingDirectCallInterface(expectedInterface: Class<T>): T {
    return mockInterface(expectedInterface) { method, args ->
        return@mockInterface method.invoke(this.getBlocking(), *args)
    }
}

fun <T : Any> ICommunicationPromise<T>.createNonBlockingDirectCallInterface(expectedInterface: Class<T>): T {
    return mockInterface(expectedInterface) { method, args ->
        return@mockInterface handleMethodCallNonBlocking(this, method, args)
    }
}

fun <T : Any> handleMethodCallNonBlocking(promise: ICommunicationPromise<T>, method: Method, args: Array<Any>): ICommunicationPromise<Any> {
    if (method.returnType != ICommunicationPromise::class.java)
        throw UnsupportedOperationException("Cannot call a method non blocking not returning a ICommunicationPromise")
    return promise.then { method.invoke(it, *args) as ICommunicationPromise<Any> }.flatten()
}

fun <T : Any> CompletableFuture<T>.toCommunicationPromise(): ICommunicationPromise<T> {
    val communicationPromise = CommunicationPromise<T>()
    this.handle { t, throwable ->
        if (t == null) {
            communicationPromise.setFailure(throwable)
        } else {
            communicationPromise.setSuccess(t)
        }
    }
    return communicationPromise
}

fun <T : Any> ICommunicationPromise<T>.toCompletableFuture(): CompletableFuture<T> {
    val completableFuture = CompletableFuture<T>()
    this.addResultListener { completableFuture.complete(it) }
        .addFailureListener { completableFuture.completeExceptionally(it) }
    return completableFuture
}

private fun <T> mockInterface(interfaceClass: Class<T>, callFunction: (method: Method, args: Array<Any>) -> Any?): T {
    return Proxy.newProxyInstance(interfaceClass.classLoader, arrayOf(interfaceClass)) { _, method, args ->
        return@newProxyInstance callFunction(method, args ?: emptyArray())
    } as T
}