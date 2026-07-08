package com.cortex.agent.runtime.llm;

/**
 * LLM error classifier - reference hermes error_classifier.py
 *
 * @author cortex
 */
public class ErrorClassifier
{
    public enum FailoverReason
    {
        AUTH,               // 401/403 - auth failed
        RATE_LIMIT,         // 429 - throttled
        OVERLOADED,         // 503/529 - overloaded
        SERVER_ERROR,       // 500/502 - server error
        TIMEOUT,            // timeout
        CONTEXT_OVERFLOW,   // context too long
        MODEL_NOT_FOUND,    // 404 - model not found
        CONTENT_POLICY,     // content policy blocked
        BILLING,            // 402 - billing/quota exhausted
        PAYLOAD_TOO_LARGE,  // 413 - request body too large
        FORMAT_ERROR,       // 400 - bad request format
        IMAGE_TOO_LARGE,    // image size exceeds limit
        MULTIMODAL_UNSUPPORTED, // provider does not support multimodal
        UNKNOWN
    }

    public static class ClassifiedError
    {
        public final FailoverReason reason;
        public final int statusCode;
        public final String message;
        public final boolean shouldRetry;
        public final boolean shouldFailover;
        public final long retryDelayMs;

        public ClassifiedError(FailoverReason reason, int statusCode, String message,
                               boolean shouldRetry, boolean shouldFailover, long retryDelayMs)
        {
            this.reason = reason;
            this.statusCode = statusCode;
            this.message = message;
            this.shouldRetry = shouldRetry;
            this.shouldFailover = shouldFailover;
            this.retryDelayMs = retryDelayMs;
        }
    }

    public static ClassifiedError classify(int statusCode, String errorBody, Throwable cause)
    {
        String body = errorBody != null ? errorBody.toLowerCase() : "";
        boolean isTimeout = cause != null && (cause instanceof java.net.http.HttpTimeoutException
                || cause.getMessage() != null && cause.getMessage().toLowerCase().contains("timeout"));

        if (isTimeout)
        {
            return new ClassifiedError(FailoverReason.TIMEOUT, 0, "timeout", true, false, 3000);
        }

        // Body-based detection first (some providers return 400/500 with descriptive body)
        if (body.contains("image") && (body.contains("not supported")
                || body.contains("does not support") || body.contains("only") && body.contains("text")))
        {
            return new ClassifiedError(FailoverReason.MULTIMODAL_UNSUPPORTED, statusCode, errorBody, false, true, 0);
        }
        if (body.contains("image") && (body.contains("too large") || body.contains("exceeds") || body.contains("size")))
        {
            return new ClassifiedError(FailoverReason.IMAGE_TOO_LARGE, statusCode, errorBody, false, false, 0);
        }
        if (body.contains("billing") || body.contains("quota") || body.contains("insufficient_quota")
                || body.contains("payment") || body.contains("credit"))
        {
            return new ClassifiedError(FailoverReason.BILLING, statusCode, errorBody, false, true, 0);
        }
        if (body.contains("context") && (body.contains("length") || body.contains("overflow") || body.contains("too long")))
        {
            return new ClassifiedError(FailoverReason.CONTEXT_OVERFLOW, statusCode, errorBody, false, false, 0);
        }
        if (body.contains("content_policy") || body.contains("content filter") || body.contains("safety"))
        {
            return new ClassifiedError(FailoverReason.CONTENT_POLICY, statusCode, errorBody, false, false, 0);
        }

        switch (statusCode)
        {
            case 400:
                return new ClassifiedError(FailoverReason.FORMAT_ERROR, statusCode, errorBody, false, false, 0);
            case 401:
            case 403:
                return new ClassifiedError(FailoverReason.AUTH, statusCode, errorBody, false, true, 0);
            case 402:
                return new ClassifiedError(FailoverReason.BILLING, statusCode, errorBody, false, true, 0);
            case 404:
                return new ClassifiedError(FailoverReason.MODEL_NOT_FOUND, statusCode, errorBody, false, true, 0);
            case 413:
                return new ClassifiedError(FailoverReason.PAYLOAD_TOO_LARGE, statusCode, errorBody, false, false, 0);
            case 429:
                // Check for billing-related 429 (some providers use 429 for quota exhaustion)
                if (body.contains("quota") || body.contains("billing"))
                {
                    return new ClassifiedError(FailoverReason.BILLING, statusCode, errorBody, false, true, 0);
                }
                return new ClassifiedError(FailoverReason.RATE_LIMIT, statusCode, errorBody, true, true, 5000);
            case 500:
            case 502:
                return new ClassifiedError(FailoverReason.SERVER_ERROR, statusCode, errorBody, true, false, 2000);
            case 503:
            case 529:
                return new ClassifiedError(FailoverReason.OVERLOADED, statusCode, errorBody, true, true, 3000);
            default:
                return new ClassifiedError(FailoverReason.UNKNOWN, statusCode, errorBody, true, false, 2000);
        }
    }
}