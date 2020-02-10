package snc.openchargingnetwork.node.services

import org.springframework.stereotype.Service
import snc.openchargingnetwork.node.models.OcnRules
import snc.openchargingnetwork.node.models.OcnRulesList
import snc.openchargingnetwork.node.models.OcnRulesListType
import snc.openchargingnetwork.node.models.entities.OcnRulesListEntity
import snc.openchargingnetwork.node.models.exceptions.OcpiClientGenericException
import snc.openchargingnetwork.node.models.exceptions.OcpiClientInvalidParametersException
import snc.openchargingnetwork.node.models.ocpi.BasicRole
import snc.openchargingnetwork.node.repositories.OcnRulesListRepository
import snc.openchargingnetwork.node.repositories.PlatformRepository
import snc.openchargingnetwork.node.tools.extractToken

@Service
class OcnRulesService(private val platformRepository: PlatformRepository,
                      private val ocnRulesListRepository: OcnRulesListRepository) {

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

    fun updateWhitelist(authorization: String, body: List<BasicRole>) {
        // 1. check token C
        val platform = platformRepository.findByAuth_TokenC(authorization.extractToken())
                ?: throw OcpiClientInvalidParametersException("Invalid CREDENTIALS_TOKEN_C")

        // 2. check blacklist inactive
        if (platform.rules.blacklist) {
            throw OcpiClientGenericException("OCN Rules whitelist and blacklist cannot be active at same time.")
        }

        // 4. set whitelist to active
        platform.rules.whitelist = true

        // 5. save whitelist option
        platformRepository.save(platform)

        // 6. re-apply whitelist
        ocnRulesListRepository.deleteByPlatformID(platform.id)
        ocnRulesListRepository.saveAll(body.map { OcnRulesListEntity(
                platformID = platform.id!!,
                type = OcnRulesListType.WHITELIST,
                counterparty = it) })
    }

    private fun extractList(rules: Iterable<OcnRulesListEntity>, type: OcnRulesListType): List<BasicRole> {
        return rules
                .filter { it.type == type }
                .map { it.counterparty }
    }

}