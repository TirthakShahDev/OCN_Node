package snc.openchargingnetwork.node.controllers.ocpi.v2_2

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import snc.openchargingnetwork.node.models.ocpi.BasicRole
import snc.openchargingnetwork.node.models.ocpi.OcpiResponse

data class OcnRulesList(val active: Boolean, val list: List<BasicRole>)
data class OcnRules(val signatures: Boolean, val whitelist: OcnRulesList, val blacklist: OcnRulesList)

@RestController
class OcnRulesController {

    @GetMapping("/ocpi/receiver/2.2/ocnrules")
    fun getRules(@RequestHeader("authorization") authorization: String): ResponseEntity<OcpiResponse<OcnRules>> {
        return ResponseEntity.ok(OcpiResponse(
                statusCode = 1000,
                data = OcnRules(
                    signatures = false,
                    whitelist = OcnRulesList(active = false, list = listOf()),
                    blacklist = OcnRulesList(active = false, list = listOf()))))
    }

}