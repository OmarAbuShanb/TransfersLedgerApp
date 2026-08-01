package dev.anonymous.transfers_ledger.core.notification

import dev.anonymous.transfers_ledger.core.PhoneNumberUtils
import dev.anonymous.transfers_ledger.core.PaymentSources
import dev.anonymous.transfers_ledger.domain.model.DirectionSource
import dev.anonymous.transfers_ledger.domain.model.TransactionDirection
import java.util.regex.Pattern

object PaymentNotificationParser {
    private const val AMOUNT = "([0-9٠-٩۰-۹]+(?:[.,][0-9٠-٩۰-۹]+)?)"
    private const val SPACES = "\\s*"

    private val supportedSources = listOf(
        NotificationSource(
            packageNames = listOf(PaymentSources.PALPAY_PACKAGE),
            sourceName = PaymentSources.PALPAY,
            defaultDirection = TransactionDirection.INCOMING,
            patterns = listOf(
                rule(
                    name = "PalPay English pay-to-friend",
                    regex = "Transfer\\s+Pay-to-Friend:$SPACES(.*?)$SPACES[,،]$SPACES(?:amount\\s+of|بمبلغ)$SPACES(?:ILS$SPACES)?$AMOUNT",
                    directionMode = DirectionMode.AUTO_BY_KEYWORDS,
                    outgoingHints = listOf("WALLET")
                ),
                rule(
                    name = "PalPay Arabic pay-to-friend",
                    regex = "تحويل\\s+دفع\\s+لصديق:$SPACES(.*?)$SPACES[,،]$SPACES(?:بمبلغ|amount\\s+of)$SPACES(?:ILS$SPACES)?$AMOUNT",
                    directionMode = DirectionMode.AUTO_BY_KEYWORDS,
                    outgoingHints = listOf("WALLET")
                ),
                rule(
                    name = "PalPay short pay-to-friend",
                    regex = "Pay-to-Friend:$SPACES(.*?)$SPACES[,،]$SPACES(?:amount\\s+of|بمبلغ)$SPACES(?:ILS$SPACES)?$AMOUNT",
                    directionMode = DirectionMode.AUTO_BY_KEYWORDS,
                    outgoingHints = listOf("WALLET")
                )
            )
        ),
        NotificationSource(
            packageNames = listOf(PaymentSources.JAWWAL_PAY_PACKAGE),
            sourceName = PaymentSources.JAWWAL_PAY,
            defaultDirection = TransactionDirection.INCOMING,
            patterns = listOf(
                rule(
                    name = "JawwalPay Arabic incoming with reference",
                    regex = "بقيمة:${SPACES}(?:ILS${SPACES})?${AMOUNT}${SPACES}من${SPACES}(.*?)${SPACES}[.]${SPACES}.*?المرجع${SPACES}:${SPACES}\\((.*?)\\)",
                    directionMode = DirectionMode.INCOMING,
                    senderGroup = 2,
                    amountGroup = 1,
                    referenceGroup = 3
                ),
                rule(
                    name = "JawwalPay Arabic incoming (transfer to account)",
                    regex = "([^\\n]+?)\\s+قام\\s+بتحويل\\s+مبلغ\\s+(?:ILS\\s+)?${AMOUNT}\\s+لحسابك",
                    directionMode = DirectionMode.INCOMING
                ),
                rule(
                    name = "JawwalPay Arabic outgoing (IBAN transfer)",
                    regex = "تم\\s+تحويل\\s+مبلغ\\s+الى${SPACES}(.*?)\\s+بقيمة${SPACES}(?:ILS${SPACES})?${AMOUNT}",
                    directionMode = DirectionMode.OUTGOING
                ),
                rule(
                    name = "JawwalPay English incoming with reference",
                    regex = "\"Money\\s+Transfer\"\\s+transaction\\s+with\\s+an\\s+amount\\s+of:${SPACES}(?:ILS${SPACES})?${AMOUNT}${SPACES},${SPACES}from:${SPACES}(.*?)\\s+is\\s+done\\s+successfully.*?Reference\\s+ID:${SPACES}(\\d+)",
                    directionMode = DirectionMode.INCOMING,
                    senderGroup = 2,
                    amountGroup = 1,
                    referenceGroup = 3
                ),
                rule(
                    name = "JawwalPay English incoming (no reference)",
                    regex = "\"Money\\s+Transfer\"\\s+transaction\\s+with\\s+an\\s+amount\\s+of:${SPACES}(?:ILS${SPACES})?${AMOUNT}${SPACES},${SPACES}from:${SPACES}(.*?)\\s+is\\s+done\\s+successfully",
                    directionMode = DirectionMode.INCOMING,
                    senderGroup = 2,
                    amountGroup = 1
                ),
                rule(
                    name = "JawwalPay English incoming (transfer to account)",
                    regex = "([^\\n]+?)\\s+has\\s+transferred\\s+an\\s+amount\\s+of\\s+(?:ILS\\s+)?${AMOUNT}\\s+to\\s+your\\s+account",
                    directionMode = DirectionMode.INCOMING
                )
            )
        ),
        NotificationSource(
            packageNames = listOf(PaymentSources.BANK_OF_PALESTINE_PACKAGE),
            sourceName = PaymentSources.BANK_OF_PALESTINE,
            defaultDirection = TransactionDirection.INCOMING,
            patterns = listOf(
                rule(
                    name = "BOP English mobile banking transfer",
                    regex = "Mobile:${SPACES}Banking\\s+transfer:${SPACES}(.*?)${SPACES}[,،]${SPACES}amount\\s+of${SPACES}${AMOUNT}${SPACES}ILS",
                    directionMode = DirectionMode.AUTO_BY_KEYWORDS,
                    outgoingHints = listOf("WALLET")
                ),
                rule(
                    name = "BOP English pay-to-friend",
                    regex = "Transfer\\s+Pay-to-Friend:${SPACES}(.*?)${SPACES}[,،]${SPACES}amount\\s+of${SPACES}(?:ILS${SPACES})?${AMOUNT}",
                    directionMode = DirectionMode.AUTO_BY_KEYWORDS,
                    outgoingHints = listOf("WALLET")
                ),
                rule(
                    name = "BOP Arabic pay-to-friend",
                    regex = "تحويل\\s+دفع\\s+لصديق:${SPACES}(.*?)${SPACES}[,،]${SPACES}(?:بمبلغ|amount\\s+of)${SPACES}(?:ILS${SPACES})?${AMOUNT}",
                    directionMode = DirectionMode.AUTO_BY_KEYWORDS,
                    outgoingHints = listOf("WALLET")
                ),
                rule(
                    name = "BOP Arabic banking transfer",
                    regex = "تحويل\\s+بنكي:${SPACES}(.*?)${SPACES}[,،]${SPACES}بمبلغ${SPACES}(?:ILS${SPACES})?${AMOUNT}",
                    directionMode = DirectionMode.AUTO_BY_KEYWORDS,
                    outgoingHints = listOf("WALLET")
                )
            )
        ),
        NotificationSource(
            packageNames = listOf(PaymentSources.JAWWAL_PAY_SMS),
            sourceName = PaymentSources.JAWWAL_PAY,
            defaultDirection = TransactionDirection.INCOMING,
            patterns = listOf(
                rule(
                    name = "JawwalPay SMS Arabic incoming (received)",
                    regex = "بقيمة:?${SPACES}(?:ILS${SPACES})?${AMOUNT}${SPACES}من${SPACES}(.*?)${SPACES}[.]",
                    directionMode = DirectionMode.INCOMING,
                    senderGroup = 2,
                    amountGroup = 1
                ),
                rule(
                    name = "JawwalPay SMS Arabic incoming (transfer to account)",
                    regex = "([^\\n]+?)\\s+قام\\s+بتحويل\\s+مبلغ\\s+(?:ILS\\s+)?${AMOUNT}\\s+لحسابك",
                    directionMode = DirectionMode.INCOMING
                ),
                rule(
                    name = "JawwalPay SMS Arabic outgoing (subscriber transfer)",
                    regex = "تمت\\s+حركة\\s+تحويل\\s+الأموال\\s+لمبلغ:?${SPACES}(?:ILS${SPACES})?${AMOUNT}${SPACES}للمشترك:?${SPACES}(.*?)${SPACES}بنجاح",
                    directionMode = DirectionMode.OUTGOING,
                    senderGroup = 2,
                    amountGroup = 1
                ),
                rule(
                    name = "JawwalPay SMS Arabic outgoing (IBAN transfer)",
                    regex = "تم\\s+تحويل\\s+مبلغ\\s+الى${SPACES}(.*?)\\s+بقيمة${SPACES}(?:ILS${SPACES})?${AMOUNT}",
                    directionMode = DirectionMode.OUTGOING
                ),
                rule(
                    name = "JawwalPay SMS English outgoing (to customer)",
                    regex = "Your\\s+\"Money\\s+Transfer\"\\s+transaction\\s+with\\s+an\\s+amount\\s+of:${SPACES}(?:ILS${SPACES})?${AMOUNT}${SPACES},${SPACES}to\\s+customer:${SPACES}(.*?)\\s+is\\s+done\\s+successfully",
                    directionMode = DirectionMode.OUTGOING,
                    senderGroup = 2,
                    amountGroup = 1
                ),
                rule(
                    name = "JawwalPay SMS English outgoing (from account)",
                    regex = "Amount\\s+(?:ILS\\s+)?${AMOUNT}\\s+has\\s+been\\s+transferred\\s+from\\s+your\\s+account\\s+to\\s+(\\S+)",
                    directionMode = DirectionMode.OUTGOING,
                    senderGroup = 2,
                    amountGroup = 1
                ),
                rule(
                    name = "JawwalPay SMS English incoming (transfer to account)",
                    regex = "([^\\n]+?)\\s+has\\s+transferred\\s+an\\s+amount\\s+of\\s+(?:ILS\\s+)?${AMOUNT}\\s+to\\s+your\\s+account",
                    directionMode = DirectionMode.INCOMING
                ),
                rule(
                    name = "JawwalPay SMS English incoming (from phone)",
                    regex = "\"Money\\s+Transfer\"\\s+transaction\\s+with\\s+an\\s+amount\\s+of:${SPACES}(?:ILS${SPACES})?${AMOUNT}${SPACES},${SPACES}from:${SPACES}(.*?)\\s+is\\s+done\\s+successfully",
                    directionMode = DirectionMode.INCOMING,
                    senderGroup = 2,
                    amountGroup = 1
                )
            )
        )
    )

    private val sourcesByPackage: Map<String, NotificationSource> =
        supportedSources.flatMap { source ->
            source.packageNames.map { pkg -> pkg to source }
        }.toMap()

    fun parse(packageName: String, text: String): ParsedPaymentNotification? {
        val source = sourcesByPackage[packageName] ?: return null

        source.patterns.forEach { rule ->
            val matcher = rule.pattern.matcher(text)
            if (matcher.find()) {
                return ParsedPaymentNotification(
                    sender = cleanSender(matcher.groupOrNull(rule.senderGroup)),
                    amount = matcher.groupOrNull(rule.amountGroup).orEmpty(),
                    reference = rule.referenceGroup?.let { matcher.groupOrNull(it) },
                    sourceName = source.sourceName,
                    direction = resolveDirection(source, rule, text),
                    directionSource = DirectionSource.AUTO,
                    matchedRuleName = rule.name
                )
            }
        }

        return null
    }

    private fun resolveDirection(
        source: NotificationSource,
        rule: NotificationRule,
        text: String
    ): TransactionDirection {
        return when (rule.directionMode) {
            DirectionMode.INCOMING -> TransactionDirection.INCOMING
            DirectionMode.OUTGOING -> TransactionDirection.OUTGOING
            DirectionMode.AUTO_BY_KEYWORDS -> detectDirection(rule, text) ?: source.defaultDirection
        }
    }

    private fun detectDirection(rule: NotificationRule, text: String): TransactionDirection? {
        val normalizedText = text.lowercase()
        if (rule.outgoingHints.any { normalizedText.contains(it.lowercase()) }) {
            return TransactionDirection.OUTGOING
        }
        if (rule.incomingHints.any { normalizedText.contains(it.lowercase()) }) {
            return TransactionDirection.INCOMING
        }
        return null
    }

    private fun cleanSender(sender: String?): String {
        val raw = sender?.trim().orEmpty()
        return if (PhoneNumberUtils.isPhoneNumber(raw)) PhoneNumberUtils.cleanPhoneNumber(raw) else raw.ifBlank { "Unknown" }
    }

    private fun rule(
        name: String,
        regex: String,
        directionMode: DirectionMode,
        senderGroup: Int = 1,
        amountGroup: Int = 2,
        referenceGroup: Int? = null,
        incomingHints: List<String> = emptyList(),
        outgoingHints: List<String> = emptyList()
    ): NotificationRule {
        return NotificationRule(
            name = name,
            pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.DOTALL),
            directionMode = directionMode,
            senderGroup = senderGroup,
            amountGroup = amountGroup,
            referenceGroup = referenceGroup,
            incomingHints = incomingHints,
            outgoingHints = outgoingHints
        )
    }

    private fun java.util.regex.Matcher.groupOrNull(index: Int): String? {
        return runCatching { group(index) }.getOrNull()
    }

    private data class NotificationSource(
        val packageNames: List<String>,
        val sourceName: String,
        val defaultDirection: TransactionDirection,
        val patterns: List<NotificationRule>
    )

    private data class NotificationRule(
        val name: String,
        val pattern: Pattern,
        val directionMode: DirectionMode,
        val senderGroup: Int,
        val amountGroup: Int,
        val referenceGroup: Int?,
        val incomingHints: List<String>,
        val outgoingHints: List<String>
    )

    enum class DirectionMode {
        INCOMING,
        OUTGOING,
        AUTO_BY_KEYWORDS
    }
}

data class ParsedPaymentNotification(
    val sender: String,
    val amount: String,
    val reference: String?,
    val sourceName: String,
    val direction: TransactionDirection,
    val directionSource: DirectionSource,
    val matchedRuleName: String
)
