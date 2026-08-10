import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
// Ambient augmentation from @fastify/multipart: adds isMultipart()/parts() to
// FastifyRequest. Imported for its types even though the plugin is registered
// in server.ts.
import '@fastify/multipart';
import type { Modality } from '@supernutrition/shared';
import {
  GATEWAY_CODES,
  GatewayError,
  type AuthContext,
  type MarketResolution,
} from '../../security/index.js';
import { mapError, resolveCorrelationId } from '../../middleware/index.js';
import { runInferencePipeline, type PipelineDeps } from './pipeline.js';
import { cancellationStatus } from './transfer-state.js';
import {
  MAX_PAYLOAD_BYTES,
  validateGatewayHeaders,
  type InferenceRequestInput,
} from './inference.handler.js';

/**
 * Fastify wiring for `/gw/v1`.
 *
 * This module owns transport concerns only. Every decision lives in
 * `pipeline.ts`, which is why the pipeline is testable without a server.
 *
 * Note what this file does NOT import: no repository, no database package, no
 * module infrastructure. The gateway's inability to durably store user content
 * is a dependency-graph property enforced by lint and by the boundary scan
 * (docs/09 §9.6).
 */

export interface GatewayRouteDeps extends PipelineDeps {
  readonly authenticate: (request: FastifyRequest) => Promise<AuthContext>;
  readonly issueTestSession: () => Promise<{
    accessToken: string;
    refreshToken: string;
    accessExpiresAt: string;
    refreshExpiresAt: string;
    authMode: string;
  }>;
  readonly resolveMarketPolicy: (
    marketId: string,
    modality: Modality,
    now: Date,
  ) => MarketResolution;
  readonly policyVersion: string;
}

const correlationOf = (request: FastifyRequest): string =>
  resolveCorrelationId(
    (request.headers['x-correlation-id'] ?? request.headers['x-request-id']) as string | undefined,
  );

const fail = (reply: FastifyReply, error: unknown, correlationId: string): FastifyReply => {
  const mapped = mapError(error, correlationId);
  return reply.status(mapped.status).headers(mapped.headers).send(mapped.body);
};

export const registerGatewayRoutes = async (
  app: FastifyInstance,
  deps: GatewayRouteDeps,
): Promise<void> => {
  app.post('/gw/v1/inference/photo', async (request, reply) => {
    const correlationId = correlationOf(request);
    let attemptId: string | null = null;

    try {
      if (!request.isMultipart()) {
        throw new GatewayError(GATEWAY_CODES.UNSUPPORTED_MEDIA_TYPE, {
          details: [{ field: 'body', issue: 'expected_multipart' }],
        });
      }

      // M3 — header checks and authentication happen BEFORE the body is read.
      //
      // request.parts() buffers the whole photo, up to 8MB, and it used to run
      // first: an unauthenticated caller could pin 8MB of server memory per
      // request by posting to this route. The release's validation_order puts
      // contract version and request id ahead of authentication, and both are
      // header-only, so the order it prescribes is preserved — only the point
      // at which bytes are accepted has moved.
      const { requestId } = validateGatewayHeaders({
        contractVersion: request.headers['x-contract-version'] as string | undefined,
        requestId: request.headers['x-request-id'] as string | undefined,
      });
      const auth = await deps.authenticate(request);

      let contextRaw: string | null = null;
      let bytes: Uint8Array | null = null;
      let contentType = '';

      for await (const part of request.parts()) {
        if (part.type === 'file' && part.fieldname === 'payload') {
          const buffer = await part.toBuffer();
          bytes = new Uint8Array(buffer);
          contentType = part.mimetype;
        } else if (part.type === 'field' && part.fieldname === 'context') {
          contextRaw = String(part.value);
        }
      }

      if (contextRaw === null) {
        throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
          details: [{ field: 'context', issue: 'missing' }],
        });
      }
      if (bytes === null) {
        throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
          details: [{ field: 'payload', issue: 'missing-normalized-photo-payload' }],
        });
      }

      let parsedContext: unknown;
      try {
        parsedContext = JSON.parse(contextRaw);
      } catch {
        throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
          details: [{ field: 'context', issue: 'unparseable_json' }],
        });
      }

      // M6 — "parsed as JSON" is not "is the shape we expect". Valid JSON
      // includes null, numbers, strings and arrays, and every one of those was
      // cast straight to InferenceRequestInput. `null` then turned the first
      // property read into a TypeError and the gateway reported its own crash as
      // a 500 for what was a malformed client request; the scalars survived as
      // far as the version check and came back as `unsupported-version`, which
      // sends a client looking for the wrong problem.
      if (
        parsedContext === null ||
        typeof parsedContext !== 'object' ||
        Array.isArray(parsedContext)
      ) {
        throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
          details: [{ field: 'context', issue: 'not_a_json_object' }],
        });
      }
      const context = parsedContext as InferenceRequestInput;
      attemptId = typeof context?.attemptId === 'string' ? context.attemptId : null;

      const result = await runInferencePipeline(deps, {
        context,
        payload: { bytes, contentType },
        auth,
        clientIp: request.ip,
        requestId,
      });

      return reply
        .status(result.status)
        .header('X-Correlation-Id', correlationId)
        .headers(result.headers ?? {})
        .send(result.body);
    } catch (error) {
      // Disposal is unconditional. The pipeline disposes in its own `finally`,
      // but a failure before the pipeline is entered must not leave bytes held.
      if (attemptId) deps.payloads.dispose(attemptId);
      return fail(reply, error, correlationId);
    }
  });

  app.delete<{ Params: { attemptId: string } }>(
    '/gw/v1/inference/:attemptId',
    async (request, reply) => {
      const correlationId = correlationOf(request);
      try {
        // M2 — the authenticated identity is USED, not merely obtained. Attempt
        // ids are chosen by the client, so discarding this result let any
        // authenticated installation cancel, and read the transfer record of,
        // any attempt id it cared to guess.
        const auth = await deps.authenticate(request);
        const record = deps.transfers.cancel(request.params.attemptId, auth.installationId);
        deps.payloads.dispose(request.params.attemptId);

        if (record === null) {
          // No record the caller may see: never begun, finished longer ago than
          // the retention window, or begun by a different installation. All
          // three answer identically, so the response cannot be used to
          // enumerate attempt ids.
          //
          // This used to answer 204, which by contract means "nothing was sent,
          // this is a complete guarantee" — a promise about the user's photo the
          // gateway had no basis for. Declining to answer is honest and needs no
          // new code in the published taxonomy.
          throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
            details: [{ field: 'attemptId', issue: 'unknown_attempt' }],
          });
        }

        const status = cancellationStatus(record);
        reply.status(status).header('X-Correlation-Id', correlationId);
        // 204 carries no body by definition; the record is still authoritative
        // and repeat cancellation returns the same terminal state.
        return status === 204 ? reply.send() : reply.send(record);
      } catch (error) {
        return fail(reply, error, correlationId);
      }
    },
  );

  /**
   * Declared by the release and by docs/06 §6.5, but `text` is a RESERVED
   * modality in v1 (schema.toml `[modality] supported = ["photo"]`). It answers
   * `invalid-modality` — "not supported by this release" — rather than 404,
   * which would say the path does not exist and send a client hunting for a
   * typo instead of falling back to manual entry.
   */
  app.post('/gw/v1/inference/text', async (request, reply) =>
    fail(
      reply,
      new GatewayError(GATEWAY_CODES.INVALID_MODALITY, {
        details: [{ field: 'modality', issue: 'reserved_in_this_release' }],
      }),
      correlationOf(request),
    ),
  );

  app.post('/gw/v1/session', async (request, reply) => {
    const correlationId = correlationOf(request);
    try {
      const pair = await deps.issueTestSession();
      return reply.status(201).header('X-Correlation-Id', correlationId).send(pair);
    } catch (error) {
      return fail(reply, error, correlationId);
    }
  });

  app.get<{ Querystring: { jurisdiction?: string; modality?: string } }>(
    '/gw/v1/policy/market',
    async (request, reply) => {
      const correlationId = correlationOf(request);
      try {
        // `modality` is non-optional in market-policy.toml. Defaulting it would
        // answer the photo posture to a client that asked about nothing —
        // exactly the cross-modality inference MP-4 forbids.
        const modality = request.query.modality;
        if (modality === undefined || modality === '') {
          throw new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
            details: [{ field: 'modality', issue: 'required' }],
          });
        }
        if (modality !== 'photo' && modality !== 'text') {
          throw new GatewayError(GATEWAY_CODES.INVALID_MODALITY, {
            details: [{ field: 'modality', issue: 'unsupported' }],
          });
        }

        const now = deps.now();
        const resolved = deps.resolveMarketPolicy(request.query.jurisdiction ?? '', modality, now);

        return reply
          .status(200)
          .header('Cache-Control', 'no-store')
          .header('X-Correlation-Id', correlationId)
          .send({
            marketJurisdiction: resolved.marketJurisdiction,
            processingRegion: resolved.processingRegion,
            residencyPosture: resolved.residencyPosture,
            remotePermitted: resolved.remotePermitted,
            consentCopyVersion: resolved.consentCopyVersion,
            acceptedInputLanguages: resolved.acceptedInputLanguages,
            policyVersion: deps.policyVersion,
            resolvedAt: now.toISOString(),
          });
      } catch (error) {
        return fail(reply, error, correlationId);
      }
    },
  );

  app.addContentTypeParser(
    'application/octet-stream',
    { bodyLimit: MAX_PAYLOAD_BYTES },
    (_request, payload, done) => done(null, payload),
  );
};
