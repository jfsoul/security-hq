import aws.ec2.EC2
import aws.{AWS, AwsClient}
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.ec2.AmazonEC2AsyncClientBuilder
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.sns.AmazonSNSAsyncClientBuilder
import com.google.cloud.securitycenter.v1.{SecurityCenterClient, SecurityCenterSettings}
import config.Config
import controllers._
import filters.HstsFilter
import model.AwsAccount
import org.quartz.impl.StdSchedulerFactory
import play.api.ApplicationLoader.Context
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{AnyContent, BodyParser, ControllerComponents}
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Logging}
import play.filters.csrf.CSRFComponents
import router.Routes
import schedule.JobScheduler
import schedule.unrecognised.IamUnrecognisedUserJob
import services.{CacheService, MetricService}
import utils.attempt.Attempt

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class AppComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
  with CSRFComponents
  with AhcWSComponents with AssetsComponents with Logging {

  implicit val impWsClient: WSClient = wsClient
  implicit val impPlayBodyParser: BodyParser[AnyContent] = playBodyParsers.default
  implicit val impControllerComponents: ControllerComponents = controllerComponents
  implicit val impAssetsFinder: AssetsFinder = assetsFinder
  override lazy val httpFilters = Seq(
    csrfFilter,
    new HstsFilter()
  )

  private val stack = configuration.get[String]("stack")
  private val stage = Config.getStage(configuration)

  // the aim of this is to get a list of available regions that we are able to access
  // note that:
  //  - Regions.values() returns Chinese and US government regions that are not accessible with the same AWS account
  //  - available regions can return regions that are not in the SDK and so Regions.findName will fail
  // to solve these we return the intersection of available regions and regions.values()
  private val availableRegions = {
    val ec2Client = AwsClient(AmazonEC2AsyncClientBuilder.standard().withRegion(Config.region).build(), AwsAccount(stack, stack, stack, stack), Config.region)
    try {
      val availableRegionsAttempt: Attempt[List[Regions]] = for {
        regionList <- EC2.getAvailableRegions(ec2Client)
        regionStringSet = regionList.map(_.getRegionName).toSet
      } yield Regions.values.filter(r => regionStringSet.contains(r.getName)).toList
      Await.result(availableRegionsAttempt.asFuture, 30 seconds).right.getOrElse(List(Config.region, Regions.US_EAST_1))
    } finally {
      ec2Client.client.shutdown()
    }
  }

  logger.info(s"Polling in the following regions: ${availableRegions.map(_.getName).mkString(", ")}")

  val regionsNotInSdk: Set[String] = availableRegions.map(_.getName).toSet -- Regions.values().map(_.getName).toSet
  if (regionsNotInSdk.nonEmpty) {
    logger.warn(s"Regions exist that are not in the current SDK (${regionsNotInSdk.mkString(", ")}), update your SDK!")
  }

  private val snykConfig = Config.getSnykConfig(configuration)
  private val googleAuthConfig = Config.googleSettings(httpConfiguration, configuration)
  private val ec2Clients = AWS.ec2Clients(configuration, availableRegions)
  private val cfnClients = AWS.cfnClients(configuration, availableRegions)
  private val taClients = AWS.taClients(configuration)
  private val s3Clients = AWS.s3Clients(configuration, availableRegions)
  private val iamClients = AWS.iamClients(configuration, availableRegions)
  private val efsClients = AWS.efsClients(configuration, availableRegions)
  val securityCredentialsProvider =
    new AWSCredentialsProviderChain(new ProfileCredentialsProvider("security"), DefaultAWSCredentialsProviderChain.getInstance())
  private val securitySnsClient = AmazonSNSAsyncClientBuilder.standard()
    .withCredentials(securityCredentialsProvider)
    .withRegion(Config.region)
    .withClientConfiguration(new ClientConfiguration().withMaxConnections(10))
    .build()
  private val securityCenterSettings = SecurityCenterSettings.newBuilder().setCredentialsProvider(Config.gcpCredentialsProvider(configuration)).build()
  private val securityCenterClient = SecurityCenterClient.create(securityCenterSettings)
  private val dynamoDbClient = AWS.dynamoDbClient(securityCredentialsProvider, Config.region, stage)
  private val securityS3Client = AmazonS3ClientBuilder.standard()
    .withCredentials(securityCredentialsProvider)
    .withRegion(Config.region)
    .build()

  private val cacheService = new CacheService(
    configuration,
    applicationLifecycle,
    environment,
    snykConfig,
    wsClient,
    ec2Clients,
    cfnClients,
    taClients,
    s3Clients,
    iamClients,
    efsClients,
    availableRegions,
    securityCenterClient
  )

  new MetricService(
    configuration,
    applicationLifecycle,
    environment,
    cacheService
  )

  val unrecognisedUserJob = new IamUnrecognisedUserJob(cacheService, securitySnsClient, securityS3Client, iamClients, configuration)(executionContext)

  val quartzScheduler = StdSchedulerFactory.getDefaultScheduler
  val jobScheduler = new JobScheduler(quartzScheduler, List(unrecognisedUserJob))
  jobScheduler.initialise()

  override def router: Router = new Routes(
    httpErrorHandler,
    new HQController(configuration, googleAuthConfig),
    new CredentialsController(configuration, cacheService, googleAuthConfig),
    new BucketsController(configuration, cacheService, googleAuthConfig),
    new SecurityGroupsController(configuration, cacheService, googleAuthConfig),
    new SnykController(configuration, cacheService, googleAuthConfig),
    new AuthController(environment, configuration, googleAuthConfig),
    assets,
    new GcpController(configuration, googleAuthConfig, cacheService)
  )
}
