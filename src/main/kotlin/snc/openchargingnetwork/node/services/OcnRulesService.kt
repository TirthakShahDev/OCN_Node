package snc.openchargingnetwork.node.services

import org.springframework.stereotype.Service
import snc.openchargingnetwork.node.models.OcnRules
import snc.openchargingnetwork.node.models.OcnRulesList
import snc.openchargingnetwork.node.models.OcnRulesListType
import snc.openchargingnetwork.node.models.entities.OcnRulesListEntity
import snc.openchargingnetwork.node.models.entities.PlatformEntity
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

        val rulesList = ocnRulesListRepository.findAllByPlatformID(platform.id).map { it.counterparty }

        return OcnRules(
                signatures = platform.rules.signatures,
                whitelist = OcnRulesList(
                        active = platform.rules.whitelist,
                        list = when (platform.rules.whitelist) {
                            true -> rulesList
                            false -> listOf()
                        }),
                blacklist = OcnRulesList(
                        active = platform.rules.blacklist,
                        list = when (platform.rules.blacklist) {
                            true -> rulesList
                            false -> listOf()
                        }))
    }

    fun updateWhitelist(authorization: String, parties: List<BasicRole>) {
        // 1. check token C / find platform
        val platform = findPlatform(authorization)

        // 2. determine whether whitelist is active
        platform.rules.whitelist = when (parties.count()) {
            // set to false if provided list is empty (deletes list)
            0 -> false
            else -> {
                // 2.1. check blacklist active
                assertListNotActive(platform, OcnRulesListType.BLACKLIST)
                // set to true if list not empty
                true
            }
        }

        // 3. save whitelist option
        platformRepository.save(platform)

        // 4. re-apply whitelist
        ocnRulesListRepository.deleteByPlatformID(platform.id)
        ocnRulesListRepository.saveAll(parties.map { OcnRulesListEntity(
            platformID = platform.id!!,
            counterparty = it.toUpperCase()) })
    }

    fun appendToWhitelist(authorization: String, body: BasicRole) {
        // 1. check token C / find platform
        val platform = findPlatform(authorization)

        // 2. check blacklist active
        assertListNotActive(platform, OcnRulesListType.BLACKLIST)

        // 3. set whitelist to true
        platform.rules.whitelist = true

        // 4. check entry does not already exist
        if (ocnRulesListRepository.existsByCounterparty(body.toUpperCase())) {
            throw OcpiClientInvalidParametersException("Party already on OCN Rules whitelist")
        }

        // 5. save whitelist option
        platformRepository.save(platform)

        // 6. add to whitelist
        ocnRulesListRepository.save(OcnRulesListEntity(
                platformID = platform.id!!,
                counterparty = body))
    }

    fun deleteFromWhitelist(authorization: String, party: BasicRole) {
        // 1. check token C / find platform
        val platform = findPlatform(authorization)

        // 2. check whitelist/blacklist activeness
        if (platform.rules.blacklist || !platform.rules.whitelist) {
            throw OcpiClientGenericException("Cannot delete entry from OCN Rules whitelist")
        }

        // 3. delete entry
        ocnRulesListRepository.deleteByPlatformIDAndCounterparty(platform.id, party)
    }

    private fun findPlatform(authorization: String): PlatformEntity {
        return platformRepository.findByAuth_TokenC(authorization.extractToken())
                ?: throw OcpiClientInvalidParametersException("Invalid CREDENTIALS_TOKEN_C")
    }

    private fun assertListNotActive(platform: PlatformEntity, type: OcnRulesListType) {
        val list = when (type) {
            OcnRulesListType.WHITELIST -> platform.rules.whitelist
            OcnRulesListType.BLACKLIST -> platform.rules.blacklist
        }
        if (list) {
            throw OcpiClientGenericException("OCN Rules whitelist and blacklist cannot be active at same time")
        }
    }

}