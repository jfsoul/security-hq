package aws.s3

import aws.AwsClient
import com.amazonaws.services.s3.AmazonS3
import utils.attempt.Attempt

import scala.io.BufferedSource

object S3 {
  def getS3Object(s3Client: AwsClient[AmazonS3], bucket: String, key: String): Attempt[BufferedSource] = {
    Attempt.Right(
      scala.io.Source
        .fromInputStream(s3Client.client.getObject(bucket, key).getObjectContent)
    )
  }
}
