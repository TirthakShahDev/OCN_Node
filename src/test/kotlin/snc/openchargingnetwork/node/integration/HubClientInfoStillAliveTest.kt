package snc.openchargingnetwork.node.integration

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import snc.openchargingnetwork.node.models.ocpi.*
import java.util.concurrent.TimeUnit


class HubClientInfoStillAliveTest {

    private lateinit var networkComponents: NetworkComponents
    private lateinit var cpo1: TestCpo
    private lateinit var cpo2: TestCpo

    private val hubClientInfoParams = HubClientInfoParams(stillAliveEnabled = true, stillAliveRate = 2000)

    @BeforeAll
    fun bootStrap() {
        networkComponents = setupNetwork(hubClientInfoParams)
        val cpos = networkComponents.cpos
        cpo1 = cpos[0]
        cpo2 = cpos[1]
    }

    @AfterAll
    fun stopTestParties() {
        stopPartyServers(networkComponents)
    }

    /**
     * Tests that a party which is unreachable will be marked as offline by the StillAliveCheck
     */
    @Test
    fun hubClientInfo_stillAlivePutsOffline() {
        val cpo2Role = cpo2.party
        assertThat(cpo1.server.hubClientInfoStatuses[cpo2Role]).isEqualTo(ConnectionStatus.CONNECTED)

        cpo2.server.stopServer()

        //TODO: ensure that test reliably works without factor of 2 to stillAliveRate
        await().atMost(hubClientInfoParams.stillAliveRate * 2, TimeUnit.MILLISECONDS).until{cpo1.server.hubClientInfoStatuses[cpo2Role] == ConnectionStatus.OFFLINE}
    }
}