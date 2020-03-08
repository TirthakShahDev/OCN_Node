package snc.openchargingnetwork.node.integration

import org.springframework.context.ConfigurableApplicationContext
import org.web3j.crypto.Credentials
import snc.openchargingnetwork.node.integration.parties.CpoServer
import snc.openchargingnetwork.node.integration.parties.MspServer
import snc.openchargingnetwork.node.models.ocpi.BasicRole

class JavalinException(val httpCode: Int = 200, val ocpiCode: Int = 2001, message: String): Exception(message)

data class CpoTestCase(val party: BasicRole, val address: String, val operator: String, val server: CpoServer)
data class MspTestCase(val party: BasicRole, val credentials: Credentials, val server: MspServer)
data class NetworkComponents(val cpos: List<CpoTestCase>, val msps: List<MspTestCase>, val nodes: List<ConfigurableApplicationContext>)

data class  HubClientInfoParams(val stillAliveEnabled: Boolean, val stillAliveRate: Long)