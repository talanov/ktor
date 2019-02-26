package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

/**
 * Response validator method.
 *
 * You could throw an exception to fail the response.
 */
typealias ResponseValidator = suspend (response: HttpResponse) -> Unit

/**
 * Response exception handler method.
 */
typealias ResponseExceptionHandler = suspend (cause: Throwable) -> Unit

/**
 * Response validator feature is used for validate response and handle response exceptions.
 *
 * See also [Config] for additional details.
 */
class HttpResponseValidator(
    private val responseValidators: List<ResponseValidator>,
    private val responseExceptionHandlers: List<ResponseExceptionHandler>
) {
    private suspend fun validateResponse(response: HttpResponse) {
        responseValidators.forEach { it(response) }
    }

    private suspend fun processException(cause: Throwable) {
        responseExceptionHandlers.forEach { it(cause) }
    }

    /**
     * [HttpResponseValidator] configuration.
     */
    class Config {
        internal val responseValidators: MutableList<ResponseValidator> = mutableListOf()
        internal val responseExceptionHandlers: MutableList<ResponseExceptionHandler> = mutableListOf()

        /**
         * Add [ResponseExceptionHandler].
         * Last added handler executes first.
         */
        fun handleResponseException(block: ResponseExceptionHandler) {
            responseExceptionHandlers += block
        }

        /**
         * Add [ResponseValidator].
         * Last added validator executes first.
         */
        fun validateResponse(block: ResponseValidator) {
            responseValidators += block
        }
    }

    companion object : HttpClientFeature<Config, HttpResponseValidator> {
        override val key: AttributeKey<HttpResponseValidator> = AttributeKey("HttpResponseValidator")

        override fun prepare(block: Config.() -> Unit): HttpResponseValidator {
            val config = Config().apply(block)

            config.responseValidators.reverse()
            config.responseExceptionHandlers.reverse()

            return HttpResponseValidator(
                config.responseValidators,
                config.responseExceptionHandlers
            )
        }

        override fun install(feature: HttpResponseValidator, scope: HttpClient) {
            val BeforeReceive = PipelinePhase("BeforeReceive")
            scope.responsePipeline.insertPhaseBefore(HttpResponsePipeline.Receive, BeforeReceive)

            scope.responsePipeline.intercept(BeforeReceive) { container ->
                try {
                    feature.validateResponse(context.response)
                    proceedWith(container)
                } catch (cause: Throwable) {
                    feature.processException(cause)
                    throw cause
                }
            }
        }
    }
}

/**
 * Install [HttpResponseValidator] with [block] configuration.
 */
fun HttpClientConfig<*>.HttpResponseValidator(block: HttpResponseValidator.Config.() -> Unit) {
    install(HttpResponseValidator, block)
}
