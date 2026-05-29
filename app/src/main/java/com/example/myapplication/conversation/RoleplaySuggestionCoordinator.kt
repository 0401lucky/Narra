package com.example.myapplication.conversation

import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.model.hasSendableContent
import com.example.myapplication.model.toPlainText
import com.example.myapplication.roleplay.RoleplayConversationSupport
import com.example.myapplication.roleplay.RoleplayMessageFormatSupport
import com.example.myapplication.roleplay.RoleplayOnlineReferenceSupport
import com.example.myapplication.roleplay.RoleplayOutputParser
import com.example.myapplication.roleplay.RoleplayPromptDecorator
import com.example.myapplication.roleplay.RoleplayTranscriptFormatter

data class RoleplaySuggestionRequest(
    val scenario: RoleplayScenario,
    val session: RoleplaySession,
    val settings: AppSettings,
    val currentInput: String,
    val isVideoCallActive: Boolean = false,
    val recentMessageWindow: Int,
    val conversationMessages: List<ChatMessage>,
    val resolveAssistant: (AppSettings, String) -> Assistant?,
    val resolveRoleplayNames: (RoleplayScenario, Assistant?, AppSettings) -> Pair<String, String>,
    val resolveSuggestionModelId: (AppSettings) -> String,
    val buildDynamicDirectorNote: (List<ChatMessage>, RoleplayScenario, Assistant?, AppSettings) -> String,
)

data class RoleplaySuggestionResult(
    val suggestions: List<RoleplaySuggestionUiModel>,
)

class RoleplaySuggestionCoordinator(
    private val conversationRepository: ConversationRepository,
    private val promptContextAssembler: PromptContextAssembler,
    private val aiPromptExtrasService: AiPromptExtrasService,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    private val outputParser = RoleplayOutputParser()

    suspend fun generateSuggestions(
        request: RoleplaySuggestionRequest,
    ): RoleplaySuggestionResult {
        val assistant = request.resolveAssistant(request.settings, request.scenario.assistantId)
        val currentInteractionMode = RoleplayMessageFormatSupport.resolveScenarioInteractionMode(request.scenario)
        val useVideoCallMode = currentInteractionMode == RoleplayInteractionMode.ONLINE_PHONE &&
            request.isVideoCallActive
        val conversation = conversationRepository.getConversation(request.session.conversationId)
            ?: Conversation(
                id = request.session.conversationId,
                createdAt = nowProvider(),
                updatedAt = nowProvider(),
                assistantId = request.scenario.assistantId,
            )
        val allMessages = request.conversationMessages
            .filter { message ->
                message.status == MessageStatus.COMPLETED &&
                    message.hasSendableContent()
            }
        val recentWindow = assistant?.contextMessageSize
            ?.takeIf { it > 0 }
            ?.coerceAtMost(request.recentMessageWindow)
            ?: request.recentMessageWindow
        val recentMessages = allMessages.takeLast(recentWindow)
        val sanitizedRecentMessages = RoleplayOnlineReferenceSupport.sanitizeRequestMessages(
            messages = recentMessages,
            scenario = request.scenario,
            assistant = assistant,
            settings = request.settings,
            outputParser = outputParser,
        )
        val promptContext = promptContextAssembler.assemble(
            settings = RoleplayConversationSupport.resolvePromptSettings(request.scenario, request.settings),
            assistant = RoleplayConversationSupport.resolvePromptAssistant(request.scenario, assistant),
            conversation = conversation,
            userInputText = request.currentInput.trim(),
            recentMessages = sanitizedRecentMessages,
            promptMode = PromptMode.ROLEPLAY,
            markUsage = false,
        )
        val directorNote = request.buildDynamicDirectorNote(
            sanitizedRecentMessages,
            request.scenario,
            assistant,
            request.settings,
        )
        val suggestionModel = request.resolveSuggestionModelId(request.settings)
        if (suggestionModel.isBlank()) {
            return RoleplaySuggestionResult(emptyList())
        }
        val decoratedPrompt = RoleplayPromptDecorator.decorate(
            baseSystemPrompt = promptContext.systemPrompt,
            scenario = request.scenario,
            assistant = assistant,
            settings = request.settings,
            includeOpeningNarrationReference = allMessages.isEmpty(),
            isVideoCallActive = useVideoCallMode,
            directorNote = directorNote,
            modelId = suggestionModel,
        )
        val (userName, characterName) = request.resolveRoleplayNames(
            request.scenario,
            assistant,
            request.settings,
        )
        val conversationExcerpt = RoleplayTranscriptFormatter.formatMessages(
            messages = sanitizedRecentMessages,
            userName = userName,
            characterName = characterName,
            allowNarration = request.scenario.enableNarration,
            interactionMode = currentInteractionMode,
        )
        val suggestionProvider = request.settings.resolveFunctionProvider(ProviderFunction.CHAT_SUGGESTION)
        val baseUrl = suggestionProvider?.baseUrl ?: request.settings.baseUrl
        val apiKey = suggestionProvider?.apiKey ?: request.settings.apiKey
        val suggestions = aiPromptExtrasService.generateRoleplaySuggestions(
            conversationExcerpt = conversationExcerpt,
            systemPrompt = decoratedPrompt,
            playerStyleReference = buildPlayerStyleReference(sanitizedRecentMessages),
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelId = suggestionModel,
            apiProtocol = suggestionProvider?.resolvedApiProtocol() ?: ProviderApiProtocol.OPENAI_COMPATIBLE,
            provider = suggestionProvider,
            longformMode = currentInteractionMode == RoleplayInteractionMode.OFFLINE_LONGFORM,
        )
        return RoleplaySuggestionResult(
            suggestions = suggestions,
        )
    }

    private fun buildPlayerStyleReference(
        messages: List<ChatMessage>,
    ): String {
        return messages
            .filter { it.role == MessageRole.USER }
            .takeLast(3)
            .mapNotNull { message ->
                message.parts.toPlainText()
                    .ifBlank { message.content }
                    .trim()
                    .takeIf { it.isNotBlank() }
            }
            .joinToString(separator = "\n") { line -> "- $line" }
    }
}
