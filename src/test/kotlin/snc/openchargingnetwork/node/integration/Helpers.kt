package snc.openchargingnetwork.node.integration

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.web3j.crypto.Credentials
import org.web3j.crypto.Keys
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.ClientTransactionManager
import org.web3j.tx.gas.StaticGasProvider
import shareandcharge.openchargingnetwork.notary.SignableHeaders
import snc.openchargingnetwork.contracts.Registry
import snc.openchargingnetwork.node.Application
import snc.openchargingnetwork.node.integration.parties.CpoServer
import snc.openchargingnetwork.node.integration.parties.MspServer
import snc.openchargingnetwork.node.models.ocpi.BasicRole
import snc.openchargingnetwork.node.tools.generateUUIDv4Token

const val provider = "http://localhost:8544"
val objectMapper = jacksonObjectMapper()

/**
 * Sets up the registry and test parties for integration tests
 */
fun setupNetwork(hubClientInfoParams: HubClientInfoParams): NetworkComponents {
    // REGISTRY CONTRACT
    val registry = deployRegistry()

    // NODE 1 = http://localhost:8080
    val node1 = Credentials.create("0xae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f")
    val node1Context = setUpNode(registry.contractAddress, node1, 8080, hubClientInfoParams = hubClientInfoParams)

    // NODE 2 = http://localhost:8081
    val node2 = Credentials.create("0x0dbbe8e4ae425a6d2687f1a7e3ba17bc98c673636790f1b8ad91193c05875ef1")
    val node2Context = setUpNode(registry.contractAddress, node2, 8081, hubClientInfoParams = hubClientInfoParams)

    // CPO 1
    val cpo1 = Credentials.create("0xc88b703fb08cbea894b6aeff5a544fb92e78a18e19814cd85da83b71f772aa6c")
    val cpoServer1 = CpoServer(cpo1, BasicRole("CPA", "CH"), 8100)
    cpoServer1.setPartyInRegistry(registry.contractAddress, node1.address)
    cpoServer1.registerCredentials()

    // CPO 2
    val cpo2 = Credentials.create("0x388c684f0ba1ef5017716adb5d21a053ea8e90277d0868337519f97bede61418")
    val cpoServer2 = CpoServer(cpo2, BasicRole("CPB", "CH"), 8101)
    cpoServer2.setPartyInRegistry(registry.contractAddress, node2.address)
    cpoServer2.registerCredentials()

    // MSP
    val msp = Credentials.create("0x659cbb0e2411a44db63778987b1e22153c086a95eb6b18bdf89de078917abc63")
    val mspServer = MspServer(msp, BasicRole("MSP", "DE"), 8200)
    mspServer.setPartyInRegistry(registry.contractAddress, node1.address)
    mspServer.registerCredentials()

    val cpos = listOf(
            CpoTestCase(
                    party = BasicRole("CPA", "CH"),
                    address = cpo1.address,
                    operator = node1.address,
                    server = cpoServer1),
            CpoTestCase(
                    party = BasicRole("CPB", "CH"),
                    address = cpo2.address,
                    operator = node2.address,
                    server = cpoServer2))

    val msps = listOf(
            MspTestCase(
                    party = BasicRole("MSP", "DE"),
                    credentials = msp,
                    server = mspServer))

    val nodes = listOf(node1Context, node2Context)

    return NetworkComponents(cpos, msps, nodes)
}

/**
 * Stops the party servers and OCN nodes of the provided list
 */
fun stopPartyServers(components: NetworkComponents) {
    for (cpo in components.cpos) {
        cpo.server.stopServer()
    }
    for (msp in components.msps) {
        msp.server.stopServer()
    }
    for (node in components.nodes) {
        node.close()
    }
}

/**
 * Deploys and gets instance
 */
fun deployRegistry(): Registry {
    val web3 = Web3j.build(HttpService(provider))
    val txManager = ClientTransactionManager(web3, "0x627306090abaB3A6e1400e9345bC60c78a8BEf57")
    val gasProvider = StaticGasProvider(0.toBigInteger(), 10000000.toBigInteger())
    return Registry.deploy(web3, txManager, gasProvider).sendAsync().get()
}

/**
 * Gets deployed instance
 */
fun getRegistryInstance(credentials: Credentials, contractAddress: String): Registry {
    val web3 = Web3j.build(HttpService(provider))
    val txManager = ClientTransactionManager(web3, credentials.address)
    val gasProvider = StaticGasProvider(0.toBigInteger(), 10000000.toBigInteger())
    return Registry.load(contractAddress, web3, txManager, gasProvider)
}

fun setUpNode(registryAddress: String, credentials: Credentials, port: Int, signatures: Boolean = true, hubClientInfoParams: HubClientInfoParams) : ConfigurableApplicationContext {
    val domain = "http://localhost:$port"
    val appContext = SpringApplicationBuilder(Application::class.java)
            .addCommandLineProperties(true)
            .run("--server.port=$port",
                    "--ocn.node.url=$domain",
                    "--ocn.node.privatekey=${credentials.ecKeyPair.privateKey.toString(16)}",
                    "--ocn.node.web3.provider=$provider",
                    "--ocn.node.web3.contracts.registry=$registryAddress",
                    "--ocn.node.signatures=$signatures",
                    "--ocn.node.stillAliveEnabled=${hubClientInfoParams.stillAliveEnabled}",
                    "--ocn.node.stillAliveRate=${hubClientInfoParams.stillAliveRate}")
    getRegistryInstance(credentials, registryAddress).setNode(domain).sendAsync().get()
    return appContext
}

fun getTokenA(node: String, parties: List<BasicRole>): String {
    val response = khttp.post("$node/admin/generate-registration-token",
            headers = mapOf("Authorization" to "Token randomkey"),
            json = coerceToJson(parties))
    return response.jsonObject.getString("token")
}

fun coerceToJson(obj: Any): Any {
    return objectMapper.readValue(objectMapper.writeValueAsString(obj))
}

fun String.checksum(): String {
    return Keys.toChecksumAddress(this)
}

fun Credentials.privateKey(): String {
    return ecKeyPair.privateKey.toString(16)
}

fun SignableHeaders.toMap(tokenC: String, signature: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    map["Authorization"] = "Token $tokenC"
    map["OCN-Signature"] = signature
    map["X-Request-ID"] = generateUUIDv4Token()
    correlationId?.let { map["X-Correlation-ID"] = it }
    fromCountryCode?.let { map["OCPI-From-Country-Code"] = it }
    fromPartyId?.let { map["OCPI-From-Party-Id"] = it }
    toCountryCode?.let { map["OCPI-To-Country-Code"] = it }
    toPartyId?.let { map["OCPI-To-Party-Id"] = it }
    return map
}