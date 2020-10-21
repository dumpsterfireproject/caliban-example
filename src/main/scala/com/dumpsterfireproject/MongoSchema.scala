package com.dumpsterfireproject

import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.codecs.Macros._

object MongoSchema {

  case class User(id: String)
  case class SampleDocument(_id: ObjectId, user: User, text: String)

  import scala.jdk.CollectionConverters._
  val registries = Vector(
    fromProviders(classOf[User]),
    fromProviders(classOf[SampleDocument]),
    DEFAULT_CODEC_REGISTRY
  )
  val codecRegistry: CodecRegistry = fromRegistries(registries.asJava)

}
