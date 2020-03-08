package snc.openchargingnetwork.node.services

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import snc.openchargingnetwork.node.data.examplePlatforms
import snc.openchargingnetwork.node.data.exampleRoles
import snc.openchargingnetwork.node.models.entities.EndpointEntity
import snc.openchargingnetwork.node.models.entities.PlatformEntity
import snc.openchargingnetwork.node.models.ocpi.ClientInfo
import snc.openchargingnetwork.node.models.ocpi.ConnectionStatus
import snc.openchargingnetwork.node.models.ocpi.InterfaceRole
import snc.openchargingnetwork.node.models.ocpi.ModuleID
import snc.openchargingnetwork.node.repositories.EndpointRepository
import snc.openchargingnetwork.node.repositories.PlatformRepository
import snc.openchargingnetwork.node.repositories.RoleRepository

class HubClientInfoServiceTest {

    private val platformRepo: PlatformRepository = mockk()
    private val roleRepo: RoleRepository = mockk()
    private val endpointRepo: EndpointRepository = mockk()
    private val httpService: HttpService = mockk()
    private val routingService: RoutingService = mockk()

    private val hubClientInfoService: HubClientInfoService

    init {
        hubClientInfoService = HubClientInfoService(platformRepo, roleRepo, endpointRepo, httpService, routingService)
    }

    @Test
    fun getLocalList() {
        every { platformRepo.findAll() } returns examplePlatforms.asIterable()
        every { roleRepo.findAllByPlatformID(1L) } returns exampleRoles.filter { it.platformID == 1L }
        every { roleRepo.findAllByPlatformID(2L) } returns exampleRoles.filter { it.platformID == 2L }
        every { roleRepo.findAllByPlatformID(3L) } returns exampleRoles.filter { it.platformID == 3L }
        val localList = hubClientInfoService.getLocalList()
        assertThat(localList.size).isEqualTo(exampleRoles.size)
        assertThat(localList.filter { it.status == ConnectionStatus.CONNECTED }.size).isEqualTo(3)
    }

    @Test
    fun `getPartiesToNotifyOfClientInfoChange should only notify connected platforms`() {
        val updatedPlatform = PlatformEntity(
                id = 3L
        )
        every { platformRepo.findAll() } returns examplePlatforms.asIterable()
        every { roleRepo.findAllByPlatformID(1L) } returns exampleRoles.filter { it.platformID == 1L }
        every { endpointRepo.findByPlatformIDAndIdentifierAndRole(1L, ModuleID.HUB_CLIENT_INFO.id, InterfaceRole.RECEIVER) } returns EndpointEntity(
                platformID = 1L,
                identifier = ModuleID.HUB_CLIENT_INFO.id,
                role = InterfaceRole.RECEIVER,
                url = "http://testplatform.com/ocpi/cpo/2.2/clientinfo"
        )

        val parties = hubClientInfoService.getPartiesToNotifyOfClientInfoChange(updatedPlatform)
        assertThat(parties.count() == 1)
    }
}