/*
 * Copyright (C) 2017-2019  Lightbend
 *
 * This file is part of the Lightbend model-serving-tutorial (https://github.com/lightbend/model-serving-tutorial)
 *
 * The model-serving-tutorial is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License Version 2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lightbend.modelserving.spark

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import com.lightbend.model.winerecord.WineRecord
import com.lightbend.modelserving.model.ModelFactoryResolver
import org.apache.spark.serializer.KryoRegistrator

/**
  * Kryo serializer for model state
  */
class ModelStateSerializerKryo extends Serializer[ModelState]{

  super.setAcceptsNull(false)
  super.setImmutable(true)

  /** Reads bytes and returns a new object of the specified concrete type.
    * <p>
    * Before Kryo can be used to read child objects, {@link Kryo#reference(Object)} must be called with the parent object to
    * ensure it can be referenced by the child objects. Any serializer that uses {@link Kryo} to read a child object may need to
    * be reentrant.
    * <p>
    * This method should not be called directly, instead this serializer can be passed to {@link Kryo} read methods that accept a
    * serialier.
    *
    * @return May be null if { @link #getAcceptsNull()} is true.
    */
  override def read(kryo: Kryo, input: Input, `type`: Class[ModelState]): ModelState = {
    import ModelStateSerializerKryo._

    val start = System.currentTimeMillis()
    val nlen = input.readLong().asInstanceOf[Int]
    val nbytes = new Array[Byte](nlen)
    input.read(nbytes)
    val name = new String(nbytes)

    val mType = input.readLong().asInstanceOf[Int]
    val mlen = input.readLong().asInstanceOf[Int]
    val bytes = new Array[Byte](mlen)
    input.read(bytes)
    validateResolver()
    resolver.getFactory(mType) match {
      case Some(factory) => {
        val state = ModelState(name, factory.restore(bytes))
        println(s"KRYO deserialization in ${System.currentTimeMillis() - start} ms")
        state
      }
      case _ => {
        println(s"KRYO deserialization failed in ${System.currentTimeMillis() - start} ms")
        throw new Exception(s"Unknown model type $mType to restore")
      }
    }
  }

  /** Writes the bytes for the object to the output.
    * <p>
    * This method should not be called directly, instead this serializer can be passed to {@link Kryo} write methods that accept a
    * serialier.
    *
    * @param value May be null if { @link #getAcceptsNull()} is true.
    */
  override def write(kryo: Kryo, output: Output, value: ModelState): Unit = {
    val start = System.currentTimeMillis()
    output.writeLong(value.name.length)
    output.write(value.name.getBytes)
    output.writeLong(value.model.getType.value.toLong)
    val bytes = value.model.toBytes
    output.writeLong(bytes.length)
    output.write(bytes)
    println(s"KRYO serialization in ${System.currentTimeMillis() - start} ms")
  }
}

object ModelStateSerializerKryo{

  // Model Factory resolver
  private var resolver : ModelFactoryResolver[WineRecord, Double] = _

  // This method has to be invoked before execution starts
  def setResolver(res : ModelFactoryResolver[WineRecord, Double]) : Unit = resolver = res
  // Ensure that resolver is set
  private def validateResolver() : Unit = if(resolver == null) throw new Exception("Model factory resolver is not set")
}

class ModelStateRegistrator extends KryoRegistrator {
  override def registerClasses(kryo: Kryo) {
    kryo.register(classOf[ModelState], new ModelStateSerializerKryo())
  }
}
