package me.rerere.rikkahub.data.config

/**
 * Central v1 capability boundary for ChatHub.
 *
 * The underlying RikkaHub runtime stays intact. These switches make the first
 * product milestone explicit without deleting future capability modules.
 */
object ChatHubV1Scope {
    /** v1 search is exposed through the existing structured tool path. */
    const val ENABLE_SEARCH = true

    /** Deferred until the v1 conversation loop is validated. */
    const val ENABLE_LONG_TERM_MEMORY = false

    /** v1 does not expose the broader local-tool catalog. */
    const val ENABLE_LOCAL_TOOLS = false

    /** Recent-chat retrieval is part of the deferred memory surface. */
    const val ENABLE_RECENT_CHAT_REFERENCE = false

    /** Deferred workspace/terminal capability surface. */
    const val ENABLE_WORKSPACE_TOOLS = false

    /** Deferred Skills extension surface. */
    const val ENABLE_SKILLS = false

    /** v1 uses the built-in search tool, not external MCP tool packs. */
    const val ENABLE_MCP_TOOLS = false
}
