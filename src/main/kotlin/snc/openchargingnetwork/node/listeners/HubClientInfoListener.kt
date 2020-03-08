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

package snc.openchargingnetwork.node.listeners

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener
import snc.openchargingnetwork.node.models.entities.PlatformEntity
import snc.openchargingnetwork.node.models.entities.RoleEntity
import snc.openchargingnetwork.node.models.events.PlatformDisconnectedDomainEvent
import snc.openchargingnetwork.node.models.events.PlatformRegisteredDomainEvent
import snc.openchargingnetwork.node.models.events.PlatformReconnectedDomainEvent
import snc.openchargingnetwork.node.models.events.PlatformUnregisteredDomainEvent
import snc.openchargingnetwork.node.models.ocpi.*
import snc.openchargingnetwork.node.repositories.RoleRepository
import snc.openchargingnetwork.node.services.HubClientInfoService

@Component
class HubClientInfoListener(private val hubClientInfoService: HubClientInfoService,
                            private val roleRepo: RoleRepository) {

    @Async
    @TransactionalEventListener
    fun handlePlatformRegisteredDomainEvent(event: PlatformRegisteredDomainEvent) {
        notifyPartiesOfChanges(event.platform, event.roles)
    }

    @Async
    @TransactionalEventListener
    fun handlePlatformUnregisteredDomainEvent(event: PlatformUnregisteredDomainEvent) {
        notifyPartiesOfChanges(event.platform, event.roles)
    }

    @Async
    @TransactionalEventListener
    fun handlePlatformReconnectedDomainEvent(event: PlatformReconnectedDomainEvent) {
        val roles = roleRepo.findAllByPlatformID(event.platform.id)
        notifyPartiesOfChanges(event.platform, roles)
    }

    @Async
    @TransactionalEventListener
    fun handlePlatformDisconnectedDomainEvent(event: PlatformDisconnectedDomainEvent) {
        val roles = roleRepo.findAllByPlatformID(event.platform.id)
        notifyPartiesOfChanges(event.platform, roles)
    }

    private fun notifyPartiesOfChanges(changedPlatform: PlatformEntity, changedRoles: Iterable<RoleEntity>) {
        for (platformRole in changedRoles) {
            val updatedClientInfo = ClientInfo(
                    partyID = platformRole.partyID,
                    countryCode = platformRole.countryCode,
                    role = platformRole.role,
                    status = changedPlatform.status,
                    lastUpdated = changedPlatform.lastUpdated)
            val parties = hubClientInfoService.getPartiesToNotifyOfClientInfoChange(changedPlatform)
            hubClientInfoService.notifyPartiesOfClientInfoChange(parties, updatedClientInfo)
        }
    }
}