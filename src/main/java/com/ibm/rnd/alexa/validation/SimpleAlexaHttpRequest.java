package com.ibm.rnd.alexa.validation;

import com.amazon.ask.model.RequestEnvelope;
import com.amazon.ask.servlet.verifiers.AlexaHttpRequest;

/**
 * Framework-agnostic {@link AlexaHttpRequest} so verifiers work without a servlet request.
 *
 * @param serializedRequestEnvelope   the raw body exactly as received
 * @param deserializedRequestEnvelope the parsed body
 * @param baseEncoded64Signature      {@code Signature} header, may be null
 * @param signingCertificateChainUrl  {@code SignatureCertChainUrl} header, may be null
 */
public record SimpleAlexaHttpRequest(
        byte[] serializedRequestEnvelope,
        RequestEnvelope deserializedRequestEnvelope,
        String baseEncoded64Signature,
        String signingCertificateChainUrl) implements AlexaHttpRequest {

    @Override
    public byte[] getSerializedRequestEnvelope() {
        return serializedRequestEnvelope;
    }

    @Override
    public RequestEnvelope getDeserializedRequestEnvelope() {
        return deserializedRequestEnvelope;
    }

    @Override
    public String getBaseEncoded64Signature() {
        return baseEncoded64Signature;
    }

    @Override
    public String getSigningCertificateChainUrl() {
        return signingCertificateChainUrl;
    }
}
