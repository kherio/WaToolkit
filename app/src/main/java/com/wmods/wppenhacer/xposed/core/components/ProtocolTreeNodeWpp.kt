package com.wmods.wppenhacer.xposed.core.components

import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.YukiLog
import java.lang.reflect.Constructor
import java.lang.reflect.Field

@Suppress("UNCHECKED_CAST", "UNUSED")
class ProtocolTreeNodeWpp(val mInstance: Any) {

    init {
        if (!TYPE.isInstance(mInstance)) throw RuntimeException("object is not a ProtocolTreeNode")
    }

    companion object {
        lateinit var TYPE: Class<*>
        private lateinit var fieldTag: Field
        private lateinit var fieldData: Field
        private lateinit var fieldChildren: Field
        private lateinit var fieldAttributes: Field
        private lateinit var constructorFull: Constructor<*>

        @JvmStatic
        fun initialize(classLoader: ClassLoader) {
            try {
                TYPE = Unobfuscator.loadProtocolTreeNodeClass(classLoader)
                val keyValueClass = Unobfuscator.loadKeyValueClass(classLoader)
                KeyValueWpp.initialize(keyValueClass)

                // A00 -> String (Tag)
                fieldTag = TYPE.declaredFields.first { it.type == String::class.java }
                    .apply { isAccessible = true }

                // A01 -> byte[] (Data)
                fieldData = TYPE.declaredFields.first { it.type == ByteArray::class.java }
                    .apply { isAccessible = true }

                // A02 -> ProtocolTreeNode[] (Children)
                val selfArrayType = java.lang.reflect.Array.newInstance(TYPE, 0)::class.java
                fieldChildren = TYPE.declaredFields.first { it.type == selfArrayType }
                    .apply { isAccessible = true }

                // A03 -> KeyValue[] (Attributes)
                val keyValueArrayType =
                    java.lang.reflect.Array.newInstance(keyValueClass, 0)::class.java
                fieldAttributes = TYPE.declaredFields.first { it.type == keyValueArrayType }
                    .apply { isAccessible = true }

                // Construtor: (String, byte[], KeyValue[], ProtocolTreeNode[])
                constructorFull = TYPE.getDeclaredConstructor(
                    String::class.java,
                    ByteArray::class.java,
                    keyValueArrayType,
                    selfArrayType
                ).apply { isAccessible = true }

            } catch (e: Exception) {
                YukiLog.log("ProtocolTreeNodeWpp Init Error: ${e.message}")
            }
        }

        fun create(
            tag: String,
            data: ByteArray?,
            attributes: List<KeyValueWpp>?,
            children: List<ProtocolTreeNodeWpp>?
        ): ProtocolTreeNodeWpp? {
            try {
                val attrArray = if (!attributes.isNullOrEmpty()) {
                    val arr = java.lang.reflect.Array.newInstance(
                        KeyValueWpp.TYPE,
                        attributes.size
                    ) as Array<Any>
                    for (i in attributes.indices) {
                        arr[i] = attributes[i].mInstance
                    }
                    arr
                } else null

                val childArray = if (!children.isNullOrEmpty()) {
                    val arr = java.lang.reflect.Array.newInstance(TYPE, children.size) as Array<Any>
                    for (i in children.indices) {
                        arr[i] = children[i].mInstance
                    }
                    arr
                } else null

                val instance = constructorFull.newInstance(tag, data, attrArray, childArray)
                return ProtocolTreeNodeWpp(instance)
            } catch (e: Exception) {
                YukiLog.log(e)
                return null
            }
        }
    }

    val tag: String?
        get() = try {
            fieldTag.get(mInstance) as? String
        } catch (_: Exception) {
            null
        }

    val data: ByteArray?
        get() = try {
            fieldData.get(mInstance) as? ByteArray
        } catch (_: Exception) {
            null
        }

    val children: List<ProtocolTreeNodeWpp>
        get() {
            val arr = try {
                fieldChildren.get(mInstance) as? Array<*>
            } catch (_: Exception) {
                null
            } ?: return emptyList()
            return arr.filterNotNull().map { ProtocolTreeNodeWpp(it) }
        }

    val attributes: List<KeyValueWpp>
        get() {
            val arr = try {
                fieldAttributes.get(mInstance) as? Array<*>
            } catch (_: Exception) {
                null
            } ?: return emptyList()
            return arr.filterNotNull().map { KeyValueWpp(it) }
        }

    // ==========================================
    // KeyValue modification operations
    // ==========================================

    /**
     * Adds a new KeyValue to the array. If keys with the same name already exist,
     * the new one is appended at the end, allowing duplicate keys.
     */
    fun addKeyValue(key: String, value: String) {
        try {
            val currentAttrs = attributes.map { it.mInstance }.toMutableList()
            val newKv = KeyValueWpp.create(key, value) ?: return
            currentAttrs.add(newKv.mInstance)

            val newArray = java.lang.reflect.Array.newInstance(
                KeyValueWpp.TYPE,
                currentAttrs.size
            ) as Array<Any>
            for (i in currentAttrs.indices) {
                newArray[i] = currentAttrs[i]
            }
            fieldAttributes.set(mInstance, newArray)
        } catch (e: Exception) {
            YukiLog.log(e)
        }
    }

    /**
     * Removes ALL occurrences of a KeyValue matching the given key.
     */
    fun removeAllKeyValuesByKey(key: String) {
        try {
            val currentAttrs = attributes
            val filtered = currentAttrs.filter { it.key != key }.map { it.mInstance }

            val newArray =
                java.lang.reflect.Array.newInstance(KeyValueWpp.TYPE, filtered.size) as Array<Any>
            for (i in filtered.indices) {
                newArray[i] = filtered[i]
            }
            fieldAttributes.set(mInstance, newArray)
        } catch (e: Exception) {
            YukiLog.log(e)
        }
    }

    /**
     * Removes a specific KeyValue instance.
     * Useful when duplicate keys exist and you want to remove only one of them.
     */
    fun removeKeyValue(keyValueToRemove: KeyValueWpp) {
        try {
            val currentAttrs = attributes
            // Filters by comparing the exact instance reference (=== / !==)
            val filtered = currentAttrs.filter { it.mInstance !== keyValueToRemove.mInstance }
                .map { it.mInstance }

            val newArray =
                java.lang.reflect.Array.newInstance(KeyValueWpp.TYPE, filtered.size) as Array<Any>
            for (i in filtered.indices) {
                newArray[i] = filtered[i]
            }
            fieldAttributes.set(mInstance, newArray)
        } catch (e: Exception) {
            YukiLog.log(e)
        }
    }

    /**
     * Modifies ALL values of an existing KeyValue with the given key.
     * If duplicates exist, all of them take the new value.
     */
    fun modifyKeyValue(key: String, newValue: String) {
        try {
            val currentAttrs = attributes
            val modifiedList = currentAttrs.map {
                if (it.key == key) {
                    KeyValueWpp.create(key, newValue)?.mInstance ?: it.mInstance
                } else {
                    it.mInstance
                }
            }

            val newArray = java.lang.reflect.Array.newInstance(
                KeyValueWpp.TYPE,
                modifiedList.size
            ) as Array<Any>
            for (i in modifiedList.indices) {
                newArray[i] = modifiedList[i]
            }
            fieldAttributes.set(mInstance, newArray)
        } catch (e: Exception) {
            YukiLog.log(e)
        }
    }

    /**
     * Gets the value of the first KeyValue matching the key, if any.
     */
    fun getFirstKeyValue(key: String): String? {
        return attributes.firstOrNull { it.key == key }?.value
    }

    class KeyValueWpp(val mInstance: Any) {

        init {
            if (!TYPE.isInstance(mInstance)) throw RuntimeException("object is not a KeyValue")
        }

        companion object {
            lateinit var TYPE: Class<*>
            private lateinit var fieldKey: Field
            private lateinit var fieldValue: Field
            private lateinit var fieldJid: Field
            private lateinit var constructorStringString: Constructor<*>


            fun initialize(keyValueClass: Class<*>) {
                try {
                    TYPE = keyValueClass

                    // The KeyValue class has exactly two String fields (key and A03, which is the value)
                    val stringFields = TYPE.declaredFields.filter { it.type == String::class.java }
                    if (stringFields.size >= 2) {
                        fieldKey = stringFields[0].apply { isAccessible = true }
                        fieldValue = stringFields[1].apply { isAccessible = true }
                    }

                    fieldJid = TYPE.declaredFields.first { it.type == FMessageWpp.UserJid.TYPE_JID }
                        .apply { isAccessible = true }

                    // (String, String) constructor used to create simple new instances
                    constructorStringString =
                        TYPE.getDeclaredConstructor(String::class.java, String::class.java).apply {
                            isAccessible = true
                        }
                } catch (e: Exception) {
                    YukiLog.log("KeyValueWpp Init Error: ${e.message}")
                }
            }

            fun create(key: String, value: String): KeyValueWpp? {
                return try {
                    val instance = constructorStringString.newInstance(key, value)
                    KeyValueWpp(instance)
                } catch (e: Exception) {
                    YukiLog.log(e)
                    null
                }
            }
        }

        var key: String?
            get() = try {
                fieldKey.get(mInstance) as? String
            } catch (e: Exception) {
                YukiLog.log(e)
                null
            }
            set(value) = try {
                fieldKey.set(mInstance, value)
            } catch (e: Exception) {
                YukiLog.log(e)
            }

        var value: String?
            get() = try {
                fieldValue.get(mInstance) as? String
            } catch (e: Exception) {
                YukiLog.log(e)
                null
            }
            set(value) = try {
                fieldValue.set(mInstance, value)
            } catch (e: Exception) {
                YukiLog.log(e)
            }

        var userJid: FMessageWpp.UserJid?
            get() = try {
                FMessageWpp.UserJid(FMessageWpp.UserJid(fieldJid.get(mInstance)).userRawString)
            } catch (e: Exception) {
                null
            }
            set(value) = try {
                fieldJid.set(mInstance, value?.userJid)
            } catch (e: Exception) {
                YukiLog.log(e)
            }

        override fun toString(): String {
            return "KeyValueWpp(key=$key, value=$value)"
        }
    }


}