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

package snc.openchargingnetwork.node.scheduledTasks

import org.springframework.scheduling.annotation.Scheduled
import org.web3j.crypto.Credentials
import snc.openchargingnetwork.contracts.Registry
import snc.openchargingnetwork.node.config.NodeProperties
import snc.openchargingnetwork.node.models.RegistryPartyDetails
import snc.openchargingnetwork.node.models.entities.PlannedRoleEntity
import snc.openchargingnetwork.node.models.ocpi.BasicRole
import snc.openchargingnetwork.node.models.ocpi.Role
import snc.openchargingnetwork.node.repositories.PlannedRoleRepository
import snc.openchargingnetwork.node.repositories.RoleRepository
import snc.openchargingnetwork.node.tools.checksum


class PlannedPartySearch(private val registry: Registry,
                         private val roleRepo: RoleRepository,
                         private val plannedRoleRepo: PlannedRoleRepository,
                         private val properties: NodeProperties) {

    @Scheduled(fixedRateString = "\${ocn.node.plannedPartySearchRate}")
    fun performCheck() {
        val myAddress = Credentials.create(properties.privateKey).address.checksum()

        // registry.getParties() returns list of party ethereum addresses which can be used to get full party details
        val plannedParties = registry.parties.sendAsync().get()
                .map {
                    val details = registry.getPartyDetailsByAddress(it as String).sendAsync().get()
                    RegistryPartyDetails(
                            BasicRole(
                                    country = details.component1().toString(Charsets.UTF_8),
                                    id = details.component2().toString(Charsets.UTF_8)),
                            roles = details.component5().map { index -> Role.getByIndex(index) },
                            nodeOperator = details.component6().checksum())
                }
                .filter {
                    val isMyParty = it.nodeOperator == myAddress
                    val hasCompletedRegistration = roleRepo.existsByCountryCodeAndPartyIDAllIgnoreCase(it.party.country, it.party.id)
                    isMyParty && !hasCompletedRegistration
                }

        for (party in plannedParties) {
            for (role in party.roles) {
                // TODO: parties that complete registration should be deleted from this repository
                if (!plannedRoleRepo.existsByPartyAndRoleAllIgnoreCase(party.party, role)) {
                    plannedRoleRepo.save(PlannedRoleEntity(party = party.party, role = role))
                    // TODO: notify new planned party
                    // TODO: announce new planned party saved to hubclientinfo listener
                }
            }
        }


    }


}