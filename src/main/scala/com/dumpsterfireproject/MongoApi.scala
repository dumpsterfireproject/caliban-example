package com.dumpsterfireproject

import caliban.GraphQL.graphQL
import caliban.{GraphQL, RootResolver, schema}
import caliban.schema.{GenericSchema, Schema}
import com.dumpsterfireproject.MongoService.MongoService
import com.dumpsterfireproject.MongoSchema.{SampleDocument, User}
import org.mongodb.scala.bson.ObjectId
import zio.RIO

object MongoApi extends GenericSchema[MongoService] {

  implicit val oidSchema: Schema[Any, ObjectId] = Schema.stringSchema.contramap(_.toHexString)
  implicit val userSchema = Schema.gen[User]
  implicit val documentSchema = Schema.gen[SampleDocument]

  case class DocumentId(id: String)
  case class DocumentCount(count: Int)

  case class Queries(id: DocumentId => RIO[MongoService, Option[SampleDocument]],
                     allDocs: DocumentCount => RIO[MongoService, List[SampleDocument]])

  val api: GraphQL[MongoService] =
    graphQL(
      RootResolver(
        Queries(
          documentId => MongoService.getDocument(documentId.id),
          documentCount => MongoService.getDocuments(documentCount.count)
        )
      )
    )
}
