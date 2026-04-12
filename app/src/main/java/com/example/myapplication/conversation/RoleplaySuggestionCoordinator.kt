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
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.model.hasSendableContent
import com.example.myapplication.model.toPlainText
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
    suspend fun generateSuggestions(
        request: RoleplaySuggestionRequest,
    ): RoleplaySuggestionResult {
        val assistant = request.resolveAssistant(request.settings, request.scenario.assistantId)
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
        val promptContext = promptContextAssembler.assemble(
            settings = request.settings,
            assistant = assistant,
            conversation = conversation,
            userInputText = request.currentInput.trim(),
            recentMessages = recentMessages,
            promptMode = PromptMode.ROLEPLAY,
        )
        val directorNote = request.buildDynamicDirectorNote(
            recentMessages,
            request.scenario,
            assistant,
            request.settings,
        )
        val decoratedPrompt = RoleplayPromptDecorator.decorate(
            baseSystemPrompt = promptContext.systemPrompt,
            scenario = request.scenario,
            assistant = assistant,
            settings = request.settings,
            includeOpeningNarrationReference = allMessages.isEmpty(),
            isVideoCallActive = request.isVideoCallActive,
            directorNote = directorNote,
        )
        val (userName, characterName) = request.resolveRoleplayNames(
            request.scenario,
            assistant,
            request.settings,
        )
        val conversationExcerpt = RoleplayTranscriptFormatter.formatMessages(
            messages = recentMessages,
            userName = userName,
            characterName = characterName,
            allowNarration = request.scenario.enableNarration,
            interactionMode = request.scenario.interactionMode,
        )
        val activeProvider = request.settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: request.settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: request.settings.apiKey
        val suggestionModel = request.resolveSuggestionModelId(request.settings)
        if (suggestionModel.isBlank()) {
            return RoleplaySuggestionResult(emptyList())
        }
        val suggestions = aiPromptExtrasService.generateRoleplaySuggestions(
            conversationExcerpt = conversationExcerpt,
            systemPrompt = decoratedPrompt,
            playerStyleReference = buildPlayerStyleReference(recentMessages),
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelId = suggestionModel,
            apiProtocol = activeProvider?.resolvedApiProtocol() ?: com.example.myapplication.model.ProviderApiProtocol.OPENAI_COMPATIBLE,
            provider = activeProvider,
            longformMode = request.scenario.longformModeEnabled,
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
