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

package snc.openchargingnetwork.node.controllers.ocpi.v2_2

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import snc.openchargingnetwork.node.models.OcnRules
import snc.openchargingnetwork.node.models.OcnRulesList
import snc.openchargingnetwork.node.models.ocpi.BasicRole
import snc.openchargingnetwork.node.models.ocpi.OcpiResponse
import snc.openchargingnetwork.node.services.OcnRulesService
import snc.openchargingnetwork.node.services.RoutingService

@RestController
class OcnRulesController(private val ocnRulesService: OcnRulesService) {

    @GetMapping("/ocpi/receiver/2.2/ocnrules")
    fun getRules(@RequestHeader("authorization") authorization: String): ResponseEntity<OcpiResponse<OcnRules>> {
        return ResponseEntity.ok(OcpiResponse(
                statusCode = 1000,
                data = ocnRulesService.getRules(authorization)))
    }

    @PutMapping("/ocpi/receiver/2.2/ocnrules/whitelist")
    fun updateWhitelist(@RequestHeader("authorization") authorization: String,
                        @RequestBody body: List<BasicRole>): ResponseEntity<OcpiResponse<Unit>> {

        ocnRulesService.updateWhitelist(authorization, body)
        return ResponseEntity.ok(OcpiResponse(statusCode = 1000))
    }

}