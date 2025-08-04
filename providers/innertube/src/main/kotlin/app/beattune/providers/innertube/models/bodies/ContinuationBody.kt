package app.beattune.providers.innertube.models.bodies

import app.beattune.providers.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class ContinuationBody(
    val context: Context = Context.DefaultWeb,
    val continuation: String
)
