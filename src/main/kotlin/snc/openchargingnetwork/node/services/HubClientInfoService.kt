/*
    Copyright 2019-2020 eMobilify GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package snc.openchargingnetwork.node.services

import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import snc.openchargingnetwork.node.models.HttpResponse
import snc.openchargingnetwork.node.models.OcnHeaders
import snc.openchargingnetwork.node.models.entities.PlatformEntity
import snc.openchargingnetwork.node.models.entities.RoleEntity
import snc.openchargingnetwork.node.models.ocpi.*
import snc.openchargingnetwork.node.repositories.EndpointRepository
import snc.openchargingnetwork.node.repositories.PlatformRepository
import snc.openchargingnetwork.node.repositories.RoleRepository
import snc.openchargingnetwork.node.tools.generateUUIDv4Token
import java.time.Instant

@Service
class HubClientInfoService(private val platformRepo: PlatformRepository,
                           private val roleRepo: RoleRepository,
                           private val endpointRepo: EndpointRepository,
                           private val httpService: HttpService,
                           private val routingService: RoutingService) {

    /**
     * Get a HubClientInfo list of local connections
     */
    fun getLocalList(): List<ClientInfo> {
        val allClientInfo = mutableListOf<ClientInfo>()
        for (platform in platformRepo.findAll()) {
            for (role in roleRepo.findAllByPlatformID(platform.id)) {
                allClientInfo.add(ClientInfo(
                        partyID = role.partyID,
                        countryCode = role.countryCode,
                        role = role.role,
                        status = platform.status,
                        lastUpdated = platform.lastUpdated))
            }
        }
        return allClientInfo
    }

    /**
     * Get parties who should be sent a HubClientInfo Push notification
     */
    fun getPartiesToNotifyOfClientInfoChange(changedPlatform: PlatformEntity) : List<RoleEntity> {
        val clientsToNotify = mutableListOf<RoleEntity>()
        for (platform in platformRepo.findAll()) {

            // Only push the update if the platform is connected and it isn't the platform that triggered the event
            if (platform.status == ConnectionStatus.CONNECTED && platform.id != changedPlatform.id) {

                // Only push the update if the platform has implemented the HubClientInfo Receiver endpoint
                val hubClientInfoPutEndpoint = endpointRepo.findByPlatformIDAndIdentifierAndRole(
                        platformID = platform.id,
                        identifier = ModuleID.HUB_CLIENT_INFO.id,
                        Role = InterfaceRole.RECEIVER
                )
                if (hubClientInfoPutEndpoint != null) {
                    for (clientRole in roleRepo.findAllByPlatformID(platform.id)) { //TODO: It could be redundant to notify each party. Perhaps it's better to assume single receiver interface
                        clientsToNotify.add(clientRole)
                    }
                }
            }
        }
        return clientsToNotify
    }

    /**
     * Send a notification of a ClientInfo change to a list of parties
     */
    fun notifyPartiesOfClientInfoChange(parties: Iterable<RoleEntity>, changedClientInfo: ClientInfo) {
        for (party in parties) {
            val sender = BasicRole(id = "OCN", country = "DE") // TODO: put node platformID and countryCode in a shared, configurable location
            val receiver = BasicRole(party.partyID, party.countryCode)
            val requestVariables = OcpiRequestVariables(
                    module = ModuleID.HUB_CLIENT_INFO,
                    interfaceRole = InterfaceRole.RECEIVER,
                    method = HttpMethod.PUT,
                    headers = OcnHeaders(
                            authorization = "Token ${platformRepo.findById(party.platformID).get().auth.tokenB}",
                            requestID = generateUUIDv4Token(),
                            correlationID = generateUUIDv4Token(),
                            sender = sender,
                            receiver = receiver),
                    body = changedClientInfo,
                    urlPathVariables = "${changedClientInfo.countryCode}/${changedClientInfo.partyID}")
            val (url, headers) = routingService.prepareLocalPlatformRequest(requestVariables, proxied = false)
            @Suppress("UNUSED_VARIABLE") val response: HttpResponse<Unit> = httpService.makeOcpiRequest(url, headers, requestVariables)
        }
    }

    /**
     * Confirm the online status of the client corresponding to a role
     */
    fun renewClientConnection(sender: BasicRole) {
        val role = roleRepo.findByCountryCodeAndPartyIDAllIgnoreCase(countryCode = sender.country, partyID = sender.id) ?: throw IllegalArgumentException("sender could not be found")
        val client = platformRepo.findById(role.platformID).get()
        client.renewConnection(Instant.now())
        platformRepo.save(client)
    }
}