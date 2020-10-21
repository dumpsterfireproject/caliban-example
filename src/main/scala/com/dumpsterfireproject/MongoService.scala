package com.dumpsterfireproject

import com.dumpsterfireproject.MongoSchema._
import com.mongodb.client.model.Filters
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}
import org.bson.conversions.Bson
import org.mongodb.scala.bson.ObjectId
import zio.{FiberRef, Has, RIO, Task, ZIO, ZLayer}

import scala.jdk.CollectionConverters._

object MongoService {

  type MongoService = Has[Service]

  trait Service {
    def getDocument(id: String): Task[Option[SampleDocument]]
    def getDocuments(count: Int): Task[List[SampleDocument]]
  }

  def getDocument(id: String): RIO[MongoService, Option[SampleDocument]] = {
    RIO.accessM(_.get.getDocument(id))
  }

  def getDocuments(count: Int): RIO[MongoService, List[SampleDocument]] = {
    RIO.accessM(_.get.getDocuments(count))
  }

  final val MongoDatabaseName = "sampleDB"
  final val ResultsCollectionName: String = "sampleCollection"

  def documentFilter(id: String, userId: String): Bson = {
    val filters: java.lang.Iterable[Bson] = {
      Vector(
        Filters.eq("_id", new ObjectId(id)),
        Filters.eq("user.id", userId)
      ).asJava
    }
    Filters.and(filters)
  }

  def documentsFilter(userId: String): Bson = Filters.eq("user.id", userId)

  val mongoLayer: ZLayer[Has[MongoClient] with Has[FiberRef[Option[User]]], Nothing, MongoService] =
    ZLayer.fromFunction { hasClient =>
      new Service {
        val database: MongoDatabase = hasClient.get.getDatabase(MongoDatabaseName).withCodecRegistry(codecRegistry)
        val collection: MongoCollection[SampleDocument] = database.getCollection(ResultsCollectionName)

        def getDocument(id: String): Task[Option[SampleDocument]] = {
          for {
            userId <- hasClient.get[FiberRef[Option[User]]].get.map(_.map(_.id).getOrElse(""))
            result <- ZIO.fromFuture { implicit ec =>
              collection.find(documentFilter(id, userId)).toFuture().map(_.collectFirst { case d: SampleDocument => d } )
            }
          } yield result
        }

        def getDocuments(count: Int): Task[List[SampleDocument]] = {
          for {
            userId <- hasClient.get[FiberRef[Option[User]]].get.map(_.map(_.id).getOrElse(""))
            result <- ZIO.fromFuture { implicit ec =>
              collection.find(documentsFilter(userId)).limit(count).toFuture().map(_.collect { case r: SampleDocument => r }.toList )
            }
          } yield result
        }
      }
    }
}
