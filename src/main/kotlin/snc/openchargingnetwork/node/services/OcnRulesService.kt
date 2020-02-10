package snc.openchargingnetwork.node.services

import org.springframework.stereotype.Service
import snc.openchargingnetwork.node.config.NodeProperties
import snc.openchargingnetwork.node.models.OcnRules
import snc.openchargingnetwork.node.models.OcnRulesList
import snc.openchargingnetwork.node.models.OcnRulesListType
import snc.openchargingnetwork.node.models.entities.OcnRulesListEntity
import snc.openchargingnetwork.node.models.exceptions.OcpiClientInvalidParametersException
import snc.openchargingnetwork.node.models.ocpi.BasicRole
import snc.openchargingnetwork.node.repositories.OcnRulesListRepository
import snc.openchargingnetwork.node.repositories.PlatformRepository
import snc.openchargingnetwork.node.tools.extractToken

@Service
class OcnRulesService(private val platformRepository: PlatformRepository,
                      private val ocnRulesListRepository: OcnRulesListRepository,
                      properties: NodeProperties) {

    private val emptyRules = OcnRules(
            signatures = properties.signatures,
            whitelist = OcnRulesList(false, listOf()),
            blacklist = OcnRulesList(false, listOf()))

    fun getRules(authorization: String): OcnRules {
        val platform = platformRepository.findByAuth_TokenC(authorization.extractToken())
                ?: throw OcpiClientInvalidParametersException("Invalid CREDENTIALS_TOKEN_C")

        val rulesList = ocnRulesListRepository.findAllByPlatformID(platform.id)

        return OcnRules(
                signatures = platform.rules.signatures,
                whitelist = OcnRulesList(
                        active = platform.rules.whitelist,
                        list = extractList(rulesList, OcnRulesListType.WHITELIST)),
                blacklist = OcnRulesList(
                        active = platform.rules.blacklist,
                        list = extractList(rulesList, OcnRulesListType.BLACKLIST)
                )
        )
    }

    private fun extractList(rules: Iterable<OcnRulesListEntity>, type: OcnRulesListType): List<BasicRole> {
        return rules
                .filter { it.type == type }
                .map { it.counterparty }
    }

}