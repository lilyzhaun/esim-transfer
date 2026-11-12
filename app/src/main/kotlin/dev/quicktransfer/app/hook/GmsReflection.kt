// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.hook

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal class GmsReflection(private val resolve: (String) -> Class<*>) {
    constructor(loader: ClassLoader) : this({ Class.forName(it, false, loader) })
    fun type(name: String): Class<*> = resolve(name)
    fun field(instance: Any, name: String): Any? = instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance)
    fun text(instance: Any, name: String) = field(instance, name)?.toString().orEmpty()
    fun number(instance: Any, name: String) = (field(instance, name) as Number).toLong()
    fun set(instance: Any, name: String, value: Any?) { instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(instance, value) }
    fun call(instance: Any, name: String, vararg args: Any?): Any? = invoke(method(instance.javaClass, name, args, false), instance, args)
    fun static(name: String, method: String, vararg args: Any?): Any? = invoke(method(type(name), method, args, true), null, args)
    fun create(name: String, vararg args: Any?): Any {
        val ctor = type(name).declaredConstructors.single { matches(it.parameterTypes, args) }.apply { isAccessible = true }
        try { return ctor.newInstance(*args) } catch (e: InvocationTargetException) { throw e.targetException }
    }
    fun declared(name: String, method: String, vararg params: Class<*>): Method = type(name).getDeclaredMethod(method, *params).apply { isAccessible = true }
    fun inherited(name: String, method: String, vararg params: Class<*>): Method {
        var current: Class<*>? = type(name)
        while (current != null) {
            current.declaredMethods.firstOrNull { it.name == method && it.parameterTypes.contentEquals(params) }?.let { return it.apply { isAccessible = true } }
            current = current.superclass
        }
        throw NoSuchMethodException("$name.$method")
    }
    private fun method(type: Class<*>, name: String, args: Array<out Any?>, static: Boolean): Method {
        var current: Class<*>? = type
        while (current != null) {
            val methods = current.declaredMethods.filter { it.name == name && Modifier.isStatic(it.modifiers) == static && matches(it.parameterTypes, args) }
            if (methods.isNotEmpty()) return methods.single().apply { isAccessible = true }
            current = current.superclass
        }
        throw NoSuchMethodException("${type.name}.$name/${args.size}")
    }
    private fun matches(types: Array<Class<*>>, args: Array<out Any?>): Boolean = types.size == args.size && types.indices.all { i ->
        val value = args[i]
        if (value == null) !types[i].isPrimitive else when (types[i]) {
            java.lang.Integer.TYPE -> value is Int
            java.lang.Long.TYPE -> value is Long
            java.lang.Boolean.TYPE -> value is Boolean
            else -> types[i].isInstance(value)
        }
    }
    private fun invoke(method: Method, receiver: Any?, args: Array<out Any?>): Any? {
        try { return method.invoke(receiver, *args) } catch (e: InvocationTargetException) { throw e.targetException }
    }
}
