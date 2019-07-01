package snc.openchargingnetwork.client.controllers.ocpi.v2_2

import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import snc.openchargingnetwork.client.repositories.*
import snc.openchargingnetwork.client.config.Properties
import snc.openchargingnetwork.client.models.ocpi.ConnectionStatusType
import snc.openchargingnetwork.client.models.ocpi.Role
import snc.openchargingnetwork.client.models.ocpi.OcpiStatus
import snc.openchargingnetwork.client.models.entities.Auth
import snc.openchargingnetwork.client.models.entities.EndpointEntity
import snc.openchargingnetwork.client.models.entities.RoleEntity
import snc.openchargingnetwork.client.models.exceptions.OcpiClientInvalidParametersException
import snc.openchargingnetwork.client.models.exceptions.OcpiServerNoMatchingEndpointsException
import snc.openchargingnetwork.client.models.ocpi.*
import snc.openchargingnetwork.client.services.HttpRequestService
import snc.openchargingnetwork.client.tools.*

@RestController
@RequestMapping("/ocpi/hub/2.2/credentials")
class CredentialsController(private val platformRepo: PlatformRepository,
                            private val roleRepo: RoleRepository,
                            private val endpointRepo: EndpointRepository,
                            private val properties: Properties,
                            private val httpRequestService: HttpRequestService) {

    @GetMapping
    fun getCredentials(@RequestHeader("Authorization") authorization: String): OcpiResponse<Credentials> {

        // TODO: allow token A authorization

        return platformRepo.findByAuth_TokenC(authorization.extractToken())?.let {

            OcpiResponse(
                    statusCode = OcpiStatus.SUCCESS.code,
                    data = Credentials(
                            token = it.auth.tokenC!!,
                            url = urlJoin(properties.url, "/ocpi/hub/versions"),
                            roles = listOf(CredentialsRole(
                                    role = Role.HUB,
                                    businessDetails = BusinessDetails(name = "Share&Charge Message Broker"),
                                    partyID = "SNC",
                                    countryCode = "DE"))))

        } ?: throw OcpiClientInvalidParametersException("Invalid CREDENTIALS_TOKEN_C")
    }

    @PostMapping
    @Transactional
    fun postCredentials(@RequestHeader("Authorization") authorization: String,
                        @RequestBody body: Credentials): OcpiResponse<Credentials> {

        // check platform previously registered by admin
        val platform = platformRepo.findByAuth_TokenA(authorization.extractToken())
                ?: throw OcpiClientInvalidParametersException("Invalid CREDENTIALS_TOKEN_C")

        // GET versions information endpoint with TOKEN_B (both provided in request body)
        val versionsInfo = httpRequestService.getVersions(body.url, body.token)

        // try to match version 2.2
        val correctVersion = versionsInfo.versions.firstOrNull { it.version == "2.2" }
                ?: throw OcpiServerNoMatchingEndpointsException("Expected version 2.2 from $versionsInfo")

        // GET 2.2 version details
        val versionDetail = httpRequestService.getVersionDetail(correctVersion.url, body.token)


        // ensure each role does not already exist
        for (role in body.roles) {
            if (roleRepo.existsByCountryCodeAndPartyIDAllIgnoreCase(role.countryCode, role.partyID)) {
                throw OcpiClientInvalidParametersException("Role with party_id=${role.partyID} and country_code=${role.countryCode} already registered")
            }
        }

        // generate TOKEN_C
        val tokenC = generateUUIDv4Token()

        // set platform connection details
        platform.auth = Auth(tokenA = null, tokenB = body.token, tokenC = tokenC)
        platform.versionsUrl = body.url
        platform.status = ConnectionStatusType.CONNECTED
        platform.lastUpdated = getTimestamp()
        platformRepo.save(platform)

        // set platform's endpoints
        for (endpoint in versionDetail.endpoints) {
            endpointRepo.save(EndpointEntity(
                    platformID = platform.id!!,
                    identifier = endpoint.identifier,
                    role = endpoint.role,
                    url = endpoint.url
            ))
        }

        // set platform's roles' credentials
        for (role in body.roles) {
            roleRepo.save(RoleEntity(
                    platformID = platform.id!!,
                    role = role.role,
                    businessDetails = role.businessDetails,
                    partyID = role.partyID,
                    countryCode = role.countryCode
            ))
        }

        // return Broker's platform connection information and role credentials
        return OcpiResponse(
                statusCode = OcpiStatus.SUCCESS.code,
                data = Credentials(
                        token = tokenC,
                        url = urlJoin(properties.url, "/ocpi/hub/versions"),
                        roles = listOf(CredentialsRole(
                                role = Role.HUB,
                                businessDetails = BusinessDetails(name = "Share&Charge Message Broker"),
                                partyID = "SNC",
                                countryCode = "DE"))))
    }

    @PutMapping
    @Transactional
    fun putCredentials(@RequestHeader("Authorization") authorization: String,
                       @RequestBody body: Credentials): OcpiResponse<Credentials> {

        // find platform (required to have already been fully registered)
        val platform = platformRepo.findByAuth_TokenC(authorization.extractToken())
                ?: throw OcpiClientInvalidParametersException("Invalid CREDENTIALS_TOKEN_C")

        // GET versions information endpoint with TOKEN_B (both provided in request body)
        val versionsInfo: Versions = httpRequestService.getVersions(body.url, body.token)

        // try to match version 2.2
        val correctVersion = versionsInfo.versions.firstOrNull { it.version == "2.2" }
                ?: throw OcpiClientInvalidParametersException("Expected version 2.2 from ${body.url}")

        // GET 2.2 version details
        val versionDetail = httpRequestService.getVersionDetail(correctVersion.url, body.token)

        // generate TOKEN_C
        val tokenC = generateUUIDv4Token()

        // set platform connection information
        platform.auth = Auth(tokenA = null, tokenB = body.token, tokenC = tokenC)
        platform.versionsUrl = body.url
        platform.status = ConnectionStatusType.CONNECTED
        platform.lastUpdated = getTimestamp()
        platformRepo.save(platform)

        endpointRepo.deleteByPlatformID(platform.id)
        roleRepo.deleteByPlatformID(platform.id)

        // set platform's endpoints
        for (endpoint in versionDetail.endpoints) {
            endpointRepo.save(EndpointEntity(
                    platformID = platform.id!!,
                    identifier = endpoint.identifier,
                    role = endpoint.role,
                    url = endpoint.url))
        }

        // set platform's roles' credentials
        for (role in body.roles) {
            roleRepo.save(RoleEntity(
                    platformID = platform.id!!,
                    role = role.role,
                    businessDetails = role.businessDetails,
                    partyID = role.partyID,
                    countryCode = role.countryCode))
        }

        // return Broker's platform connection information and role credentials
        // TODO: set roles information (name, party_id, country_code) in application.properties
        return OcpiResponse(
                statusCode = OcpiStatus.SUCCESS.code,
                data = Credentials(
                        token = tokenC,
                        url = urlJoin(properties.url, "/ocpi/hub/versions"),
                        roles = listOf(CredentialsRole(
                                role = Role.HUB,
                                businessDetails = BusinessDetails(name = "Share&Charge Message Broker"),
                                partyID = "SNC",
                                countryCode = "DE"))))
    }

    @DeleteMapping
    @Transactional
    fun deleteCredentials(@RequestHeader("Authorization") authorization: String): OcpiResponse<Nothing?> {

        val platform = platformRepo.findByAuth_TokenC(authorization.extractToken())
                ?: throw OcpiClientInvalidParametersException("Invalid CREDENTIALS_TOKEN_C")

        platformRepo.deleteById(platform.id!!)
        roleRepo.deleteByPlatformID(platform.id)
        endpointRepo.deleteByPlatformID(platform.id)

        return OcpiResponse(statusCode = 1000, data = null)
    }

}