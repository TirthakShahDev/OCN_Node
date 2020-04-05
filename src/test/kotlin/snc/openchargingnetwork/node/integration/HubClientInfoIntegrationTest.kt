package snc.openchargingnetwork.node.integration

import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.web3j.crypto.Credentials
import snc.openchargingnetwork.node.models.ocpi.*
import java.lang.Thread.sleep
import java.util.concurrent.TimeUnit


class HubClientInfoIntegrationTest {

    private lateinit var networkComponents: NetworkComponents
    private lateinit var cpo1: TestCpo
    private lateinit var cpo2: TestCpo
    private lateinit var msp: TestMsp

    private val hubClientInfoParams = HubClientInfoParams(stillAliveEnabled = false, stillAliveRate = 2000)

    @BeforeAll
    fun bootStrap() {
        networkComponents = setupNetwork(hubClientInfoParams)
        val cpos = networkComponents.cpos
        cpo1 = cpos[0]
        cpo2 = cpos[1]
        msp = networkComponents.msps.first()
    }

    @AfterAll
    fun stopTestParties() {
        stopPartyServers(networkComponents)
    }

    /**
     * Tests that new parties and deleted parties trigger hubClientInfo notifications
     */
    @Test
    fun hubClientInfo_partyRegisteredNotification() {
        val newCpoDefinition = PartyDefinition(
                nodeNumber = 1,
                port = 8102,
                party = BasicRole("CPC", "CH"),
                credentials = Credentials.create("0x82d052c865f5763aad42add438569276c00d3d88a2d062d36b2bae914d58b8c8"))
        val newCpo = setUpCpo(
                newCpoDefinition,
                networkComponents.nodes.first{ d -> d.definition.nodeNumber == newCpoDefinition.nodeNumber }.definition,
                networkComponents.registry)

        // The assumption is that cpo1 is also connected to node1
        await().atMost(2, TimeUnit.SECONDS).until{cpo1.server.hubClientInfoStatuses.containsKey(newCpoDefinition.party)}
        assertThat(cpo1.server.hubClientInfoStatuses[newCpoDefinition.party]).isEqualTo(ConnectionStatus.CONNECTED)

        newCpo.server.deleteCredentials()
        await().atMost(2, TimeUnit.SECONDS).until{cpo1.server.hubClientInfoStatuses[newCpoDefinition.party] == ConnectionStatus.SUSPENDED}
    }

    /**
     * Tests that HubClientInfo functionality can be disabled
     */
    @Test
    fun hubClientInfo_stillAliveCanBeDisabled() {
        val cpo2Role = cpo2.party
        assertThat(cpo1.server.hubClientInfoStatuses[cpo2Role]).isEqualTo(ConnectionStatus.CONNECTED)
        cpo2.server.stopServer()
        sleep(hubClientInfoParams.stillAliveRate * 2)
        assertThat(cpo1.server.hubClientInfoStatuses[cpo2Role]).isEqualTo(ConnectionStatus.CONNECTED) //Should still be connected as StillAliveCheck is disabled
    }

    /**
     * Tests that a party's client info is updated when sending or receiving a request.
     * StillAliveCheck should be disabled for this test or updated time may change unexpectedly
     */
    @Test
    fun hubClientInfo_clientInfoUpdatedByRequest() {
        val clientInfoBeforeRequest = getClientInfo()

        // make arbitrary request in order to update clientInfo.lastupdated
        msp.server.getLocation(cpo1.party)

        val clientInfoAfterRequest = getClientInfo()

        val clientInfoFilter = { party: BasicRole ->
            { ci: ClientInfo -> BasicRole(ci.partyID, ci.countryCode) == party }
        }
        val mspClientInfoBeforeRequest = clientInfoBeforeRequest.first { ci -> clientInfoFilter(msp.party)(ci) }
        val cpo1ClientInfoBeforeRequest = clientInfoBeforeRequest.first { ci -> clientInfoFilter(cpo1.party)(ci) }
        val cpo2ClientInfoBeforeRequest = clientInfoBeforeRequest.first { ci -> clientInfoFilter(cpo2.party)(ci) }
        val mspClientInfoAfterRequest = clientInfoAfterRequest.first { ci -> clientInfoFilter(msp.party)(ci) }
        val cpo1ClientInfoAfterRequest = clientInfoAfterRequest.first { ci -> clientInfoFilter(cpo1.party)(ci) }
        val cpo2ClientInfoAfterRequest = clientInfoAfterRequest.first { ci -> clientInfoFilter(cpo2.party)(ci) }

        // the MSP and CPO1 should be updated as they were involved in the request, but CPO2 shouldn't be updated
        assertThat(mspClientInfoBeforeRequest.lastUpdated).isLessThan(mspClientInfoAfterRequest.lastUpdated)
        assertThat(cpo1ClientInfoBeforeRequest.lastUpdated).isLessThan(cpo1ClientInfoAfterRequest.lastUpdated)
        assertThat(cpo2ClientInfoBeforeRequest.lastUpdated).isEqualTo(cpo2ClientInfoAfterRequest.lastUpdated)
    }

    private fun getClientInfo(): Array<ClientInfo> {
        val nodeRole = BasicRole(id = "OCN", country = "DE")
        val response = msp.server.getHubClientInfoList(nodeRole)
        assertThat(response.statusCode).isEqualTo(200)

        val json = response.jsonObject
        assertThat(json.getInt("status_code")).isEqualTo(1000)

        return objectMapper.readValue(json.getJSONArray("data").toString())
    }
}